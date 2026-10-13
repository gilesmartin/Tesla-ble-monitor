package org.opencharge.teslable;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.util.Log;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;

/** Foreground-only prototype; does not automatically wake sleeping cars or expose controls on a network. */
public final class MainActivity extends Activity implements NativeTesla.Sink {
    private static final UUID SVC = UUID.fromString("00000211-b2d1-43f0-9b88-960cebf8b91e");
    private static final UUID WRITE = UUID.fromString("00000212-b2d1-43f0-9b88-960cebf8b91e");
    private static final UUID NOTIFY = UUID.fromString("00000213-b2d1-43f0-9b88-960cebf8b91e");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final Handler ui = new Handler(Looper.getMainLooper());
    private NativeTesla tesla;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothDevice selected;
    // ADB requests are staged by the shell-restricted AdbCommandActivity.
    private boolean adbAutoConnect = false;
    private String pendingAdbAction = null;
    private String vin = "";
    private String expectedName = "";
    private int mtu = 23;
    private final ArrayDeque<byte[]> writes = new ArrayDeque<>();
    private boolean writing = false, scanning = false, ready = false;
    private final Set<String> seen = new HashSet<>();
    private EditText vinField;
    private LinearLayout matches;
    private TextView telemetry, log, status;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (tesla != null && ready) tesla.tick();
            ui.postDelayed(this,100);
        }
    };
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) { ui.post(() -> found(result)); }
        @Override public void onScanFailed(int errorCode) { ui.post(() -> say("BLE scan error: " + errorCode)); }
    };

    private static final int PERMISSION_REQUEST = 191;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        tesla = new NativeTesla(this,this);
        buildUi();
        vinField.setText(getPreferences(MODE_PRIVATE).getString("vin", ""));
        ui.post(ticker);
        if (adapter == null) say("This device has no Bluetooth adapter");
        ui.post(this::handleQueuedAdbCommand);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        ui.post(this::handleQueuedAdbCommand);
    }

    /** Process only commands placed in private app storage by the shell-protected activity. */
    private void handleQueuedAdbCommand() {
        android.content.SharedPreferences q = getSharedPreferences("tesla_adb_commands", MODE_PRIVATE);
        String cmd = q.getString("cmd", null);
        if (cmd == null) return;
        String requestedVin = q.getString("vin", null);
        // Consume before executing so an activity restart never repeats a pairing request.
        if (!q.edit().remove("cmd").remove("vin").commit()) {
            say("ADB command queue could not be cleared");
            return;
        }
        if (requestedVin != null && !requestedVin.isEmpty()) vinField.setText(requestedVin);
        cmd = cmd.trim().toLowerCase(Locale.US);
        say("ADB command: " + cmd);
        switch (cmd) {
            case "scan":
                pendingAdbAction = null;
                adbAutoConnect = false;
                startScan();
                break;
            case "connect":
            case "pair":
            case "read-charge":
                pendingAdbAction = cmd;
                if (ready) {
                    performPendingAdbAction();
                } else if (gatt != null) {
                    say("GATT connection already in progress; waiting for notifications");
                } else if (selected != null &&
                           expectedName.equals(expectedName(vinField.getText().toString().trim().toUpperCase(Locale.US)))) {
                    connect();
                } else {
                    adbAutoConnect = true;
                    startScan();
                }
                break;
            case "status":
                say("BLE status: " + (ready ? "transport-ready" : gatt != null ? "connecting" : "disconnected")
                    + ", selected=" + (selected != null)
                    + ", pending=" + (pendingAdbAction == null ? "none" : pendingAdbAction));
                break;
            case "storage-test":
                tesla.storageSelfTest();
                break;
            case "disconnect":
                pendingAdbAction = null;
                adbAutoConnect = false;
                disconnect();
                say("ADB disconnect completed");
                break;
            default:
                say("Unknown ADB command: " + cmd);
        }
    }

    private void performPendingAdbAction() {
        if (!ready || pendingAdbAction == null) return;
        String cmd = pendingAdbAction;
        pendingAdbAction = null;
        if ("pair".equals(cmd)) {
            say("Starting Vehicle Monitor pairing; Tesla key-card confirmation still required");
            tesla.pairMonitorKey();
        } else if ("read-charge".equals(cmd)) {
            say("Reading Tesla ChargeState");
            tesla.readChargeState();
        } else if ("connect".equals(cmd)) {
            say("ADB connect completed: Tesla BLE transport ready");
        }
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.rgb(32,46,57));
        t.setTextSize(size); t.setPadding(0,10,0,10); return t;
    }
    private Button button(String s, Runnable action) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(18*getResources().getDisplayMetrics().density);
        body.setPadding(pad,pad,pad,pad); body.setBackgroundColor(Color.rgb(246,248,252));
        TextView title=text("Tesla BLE Monitor",26);title.setTextColor(Color.rgb(14,48,82)); body.addView(title);
        body.addView(text("Prototype • Android 8+ • no root required • do not use while operating the vehicle",12));
        body.addView(text("Vehicle VIN",15));
        vinField=new EditText(this);vinField.setSingleLine(true);vinField.setHint("17-character Tesla VIN");
        vinField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        body.addView(vinField);
        body.addView(button("1. Scan nearby Tesla BLE devices",this::startScan));
        matches=new LinearLayout(this);matches.setOrientation(LinearLayout.VERTICAL);
        body.addView(matches);
        body.addView(button("2. Connect selected Tesla",this::connect));
        body.addView(button("3. Enroll monitor key (tap Tesla key card)",() -> {
            if (ready) {
                say("Requesting VEHICLE_MONITOR key. Approve with an existing Tesla key card on the car screen.");
                tesla.pairMonitorKey();
            } else say("Connect first");
        }));
        body.addView(button("4. Read charge state (car must be awake)",() -> {
            if (ready) tesla.readChargeState(); else say("Connect first");
        }));
        body.addView(button("Disconnect",this::disconnect));
        status=text("Not connected",16);body.addView(status);
        telemetry=text("No charge data yet",20);body.addView(telemetry);
        body.addView(text("Diagnostic log",16));
        log=text("",12);log.setTextIsSelectable(true);body.addView(log);
        scroll.addView(body);setContentView(scroll);
    }
    private boolean permissions() {
        String[] p = Build.VERSION.SDK_INT >= 31
            ? new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION }
            : new String[] { Manifest.permission.ACCESS_FINE_LOCATION };
        for (String x:p) if (checkSelfPermission(x)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(p,PERMISSION_REQUEST);return false;
        }
        return true;
    }
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g) {
        super.onRequestPermissionsResult(req,p,g);
        if (req==PERMISSION_REQUEST) say("Permissions processed; tap Scan again.");
    }
    private String inputVin() {
        String s=vinField.getText().toString().trim().toUpperCase(Locale.US);
        if (!s.matches("[A-HJ-NPR-Z0-9]{17}")) { say("Enter a valid 17-character VIN first"); return null; }
        return s;
    }
    private static String expectedName(String vin) {
        try {
            byte[] sha=MessageDigest.getInstance("SHA-1").digest(vin.getBytes(StandardCharsets.US_ASCII));
            StringBuilder b=new StringBuilder("S");
            for (int i=0;i<8;i++) b.append(String.format(Locale.US,"%02x",sha[i]&255));
            return b.append('C').toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void startScan() {
        String v=inputVin(); if(v==null || !permissions())return;
        if (adapter==null || !adapter.isEnabled()) {say("Enable Bluetooth in Android settings");return;}
        disconnect();vin=v;expectedName=expectedName(v);selected=null;seen.clear();matches.removeAllViews();
        scanner=adapter.getBluetoothLeScanner();if(scanner==null){say("Scanner unavailable");return;}
        scanner.startScan(scanCallback);scanning=true;
        say("Scanning for " + expectedName + " (10 seconds). Keep phone near car.");
        ui.postDelayed(() -> {
            if (scanning) {
                stopScan();
                if (adbAutoConnect) {
                    adbAutoConnect = false;
                    pendingAdbAction = null;
                    say("ADB scan timed out: no matching Tesla BLE advertisement found");
                } else say("BLE scan completed");
            }
        },10_000);
    }
    private void found(ScanResult result) {
        if(!scanning || result==null || result.getDevice()==null)return;
        String name = result.getScanRecord()==null ? null : result.getScanRecord().getDeviceName();
        if (name==null && hasConnectPermission()) name=result.getDevice().getName();
        if (!expectedName.equals(name) || !seen.add(result.getDevice().getAddress()))return;
        BluetoothDevice device=result.getDevice();
        String entry = name + "  •  " + device.getAddress() + "  •  " + result.getRssi() + " dBm";
        matches.addView(button("Select: " + entry,() -> { selected=device;stopScan();say("Selected " + device.getAddress()); }));
        if(selected==null)selected=device;
        say("Found matching Tesla: " + entry);
        if (adbAutoConnect) {
            adbAutoConnect = false;
            stopScan();
            connect();
        }
    }
    private boolean hasConnectPermission() { return Build.VERSION.SDK_INT<31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; }
    private void stopScan(){
        if(!scanning)return;scanning=false;
        try { if(scanner!=null)scanner.stopScan(scanCallback); } catch(SecurityException ignored){}
    }
    private void connect() {
        if(selected==null){say("Scan and select Tesla first");return;}
        if(!permissions())return;
        disconnectGatt();
        vin=inputVin(); if(vin==null)return;
        if (!expectedName.equals(expectedName(vin))) {
            say("VIN changed since scan. Scan again before connecting.");return;
        }
        getPreferences(MODE_PRIVATE).edit().putString("vin",vin).apply();
        tesla.useVehicle(vin);tesla.init(vin);
        status.setText("Connecting to " + selected.getAddress());
        gatt=selected.connectGatt(this,false,callbacks,BluetoothDevice.TRANSPORT_LE);
        if(gatt==null)say("Android connectGatt returned null");
    }
    private final BluetoothGattCallback callbacks = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g,int code,int state){
            ui.post(() -> {
                if(g != gatt)return;
                if(code==BluetoothGatt.GATT_SUCCESS && state==BluetoothProfile.STATE_CONNECTED){
                    status.setText("GATT connected; discovering service");g.discoverServices();
                } else {
                    say("GATT disconnected: " + code);
                    ready=false;tesla.connected(false);tx=null;writes.clear();writing=false;
                    status.setText("Disconnected");
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int code){ui.post(() -> {
            if(g!=gatt || code!=BluetoothGatt.GATT_SUCCESS){say("Service discovery failed: " + code);return;}
            BluetoothGattService svc=g.getService(SVC);
            tx=svc==null?null:svc.getCharacteristic(WRITE);
            BluetoothGattCharacteristic rx=svc==null?null:svc.getCharacteristic(NOTIFY);
            if(tx==null||rx==null){say("Tesla GATT service/characteristic missing");return;}
            g.requestMtu(247);
            if(!g.setCharacteristicNotification(rx,true)){say("Could not enable notifications");return;}
            BluetoothGattDescriptor desc=rx.getDescriptor(CCCD);
            if(desc==null){say("Notify CCCD missing");return;}
            if(!writeDescriptorCompat(g,desc,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))say("CCCD write failed");
        });}
        @Override public void onMtuChanged(BluetoothGatt g,int newMtu,int code) { ui.post(() -> {if(g==gatt && code==0){mtu=newMtu;say("BLE MTU: " + mtu);}}); }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int code) {ui.post(() -> {
            if(g!=gatt || !CCCD.equals(d.getUuid()))return;
            if(code==0){ready=true;status.setText("Tesla BLE transport ready");tesla.connected(true);say("Notifications enabled. You can pair or read.");performPendingAdbAction();}
            else say("Could not enable notifications: " + code);
        });}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int code) {ui.post(() -> {
            if(g!=gatt)return;
            writing=false;
            if(code!=0){say("BLE write failed: " + code);writes.clear();return;}
            flushWrites();
        });}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] val){
            if(NOTIFY.equals(c.getUuid())) {byte[] bytes=val.clone();ui.post(() -> {if(g==gatt&&ready)tesla.onNotification(bytes);});}
        }
        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c) {
            if(Build.VERSION.SDK_INT<33 && NOTIFY.equals(c.getUuid())) {
                byte[] bytes=c.getValue();if(bytes!=null)ui.post(() -> {if(g==gatt&&ready)tesla.onNotification(bytes);});
            }
        }
    };
    private static boolean writeDescriptorCompat(BluetoothGatt g,BluetoothGattDescriptor d,byte[] v) {
        if(Build.VERSION.SDK_INT>=33)return g.writeDescriptor(d,v)==BluetoothStatusCodes.SUCCESS;
        d.setValue(v);return g.writeDescriptor(d);
    }
    @Override public boolean onBleWrite(byte[] data){
        if(!ready||gatt==null||tx==null)return false;
        int n=Math.max(20,mtu-3);
        for(int p=0;p<data.length;p+=n)writes.add(java.util.Arrays.copyOfRange(data,p,Math.min(data.length,p+n)));
        flushWrites();return true;
    }
    @SuppressWarnings("deprecation")
    private void flushWrites(){
        if(writing||gatt==null||tx==null||writes.isEmpty())return;
        byte[] next=writes.poll();writing=true;
        boolean ok;
        try {
            if(Build.VERSION.SDK_INT>=33)ok=gatt.writeCharacteristic(tx,next,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
            else {tx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);tx.setValue(next);ok=gatt.writeCharacteristic(tx);}
        }catch(Exception e){ok=false;}
        if(!ok){writing=false;writes.clear();say("Cannot initiate BLE write");}
    }
    private void disconnect(){stopScan();disconnectGatt(); if(tesla!=null)tesla.close();}
    private void disconnectGatt(){
        ready=false;writes.clear();writing=false;tx=null;mtu=23;
        if(tesla!=null)tesla.connected(false);
        if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(Exception ignored){}gatt=null;}
    }
    private void say(String message){Log.i("TeslaBLE", message);String time=new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date());
        if(log!=null){String lines=time+"  "+message+"\n"+log.getText();if(lines.length()>9000)lines=lines.substring(0,9000);log.setText(lines);}
    }
    @Override public void onMessage(String s){say(s);}
    @Override public void onTelemetry(String json){
        try {
            Log.i("TeslaBLE", "ChargeState: " + json);
            JSONObject j=new JSONObject(json);
            String soc=j.isNull("battery_percent")?"unknown":j.getString("battery_percent")+"%";
            String range=j.isNull("range_km")?"unknown":String.format(Locale.US,"%.1f km",j.getDouble("range_km"));
            String charging=j.isNull("charging")?"unknown":j.getString("charging");
            telemetry.setText("Battery: "+soc+"\nRange: "+range+"\nCharging: "+charging+"\n\n"+json);
            say("Received authenticated ChargeState");
        }catch(Exception e){say("Telemetry decode error: " + e.getMessage());}
    }
    @Override protected void onDestroy() {
        ui.removeCallbacks(ticker);disconnect();super.onDestroy();
    }
}
