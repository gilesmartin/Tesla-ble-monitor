package org.opencharge.teslable;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/** Sole owner of the native Tesla client and Bluetooth GATT connection. */
public final class TeslaBleService extends Service implements NativeTesla.Sink {
    private static final String TAG = "TeslaBLE";
    public static final String ACTION_COMMAND = "org.opencharge.teslable.COMMAND";
    public static final String ACTION_UI_SCAN = "org.opencharge.teslable.UI_SCAN";
    public static final String ACTION_UI_CONNECT = "org.opencharge.teslable.UI_CONNECT";
    public static final String ACTION_UI_PAIR = "org.opencharge.teslable.UI_PAIR";
    public static final String ACTION_UI_READ = "org.opencharge.teslable.UI_READ";
    public static final String ACTION_UI_DISCONNECT = "org.opencharge.teslable.UI_DISCONNECT";
    public static final String EXTRA_COMMAND = "cmd";
    public static final String EXTRA_VIN = "vin";
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_INTERVAL_SECONDS = "seconds";

    private static final String PREFS = "tesla_ble_service_v1";
    private static final String LEGACY_ACTIVITY_PREFS = "MainActivity";
    private static final String PREF_VIN = "vin";
    private static final String PREF_DESIRED = "desired_connection";
    private static final String PREF_POLL = "poll_enabled";
    private static final String PREF_INTERVAL = "poll_interval_seconds";
    private static final String PREF_RETRIES = "reconnect_attempts";
    private static final String PREF_LAST_CHARGE = "last_charge_json";
    private static final String PREF_LAST_CHARGE_AT = "last_charge_at_ms";
    private static final String CHANNEL_ID = "tesla_ble_monitor";
    private static final int NOTIFICATION_ID = 7303;
    private static final int DEFAULT_POLL_SECONDS = 15;
    private static final int MIN_POLL_SECONDS = 15;
    private static final int MAX_POLL_SECONDS = 3600;
    private static final int MAX_RECONNECT_ATTEMPTS = 8;
    private static final long CONNECT_SCAN_MS = 10_000L;
    private static final long INITIAL_RECONNECT_MS = 5_000L;
    private static final long MAX_RECONNECT_MS = 60_000L;
    private static final long CHARGE_TIMEOUT_MS = 12_000L;

    private static final UUID GATT_SERVICE = UUID.fromString("00000211-b2d1-43f0-9b88-960cebf8b91e");
    private static final UUID GATT_WRITE = UUID.fromString("00000212-b2d1-43f0-9b88-960cebf8b91e");
    private static final UUID GATT_NOTIFY = UUID.fromString("00000213-b2d1-43f0-9b88-960cebf8b91e");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public interface Listener {
        void onStatus(String json);
        void onDeviceFound(String name, String address, int rssi);
        void onTelemetry(String json);
        void onLog(String message);
    }

    public final class LocalBinder extends Binder {
        public TeslaBleService getService() { return TeslaBleService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object stateLock = new Object();
    private SharedPreferences preferences;
    private ApiTokenStore apiTokenStore;
    private NativeTesla tesla;
    private boolean nativeInitialized;
    private String nativeVin = "";
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothDevice selectedDevice;
    private String selectedAddress = "";
    private String selectedVin = "";
    private String currentVin = "";
    private String expectedAdvertisement = "";
    private BleScanDiagnostics scanDiagnostics = new BleScanDiagnostics();
    private final Map<String, BluetoothDevice> foundDevices = new HashMap<>();
    private final ArrayDeque<byte[]> writes = new ArrayDeque<>();
    private int mtu = 23;
    private boolean scanning;
    private boolean autoConnectAfterScan;
    private volatile boolean ready;
    private boolean writing;
    private volatile boolean serviceStarted;
    private volatile boolean foregroundActive;
    private volatile boolean desiredConnection;
    private volatile boolean pollEnabled;
    private volatile boolean pollInFlight;
    private volatile boolean keyPresent;
    private volatile boolean tokenConfigured;
    private volatile boolean apiAvailable;
    private volatile int pollIntervalSeconds = DEFAULT_POLL_SECONDS;
    private int pollFailures;
    private int reconnectAttempts;
    private volatile String connectionState = "disconnected";
    private volatile String authenticationState = "unknown";
    private volatile String lastChargeJson;
    private volatile long lastChargeAtMs;
    private Listener listener;
    private volatile TelemetryHttpServer apiServer;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (nativeInitialized && ready) {
                try { tesla.tick(); }
                catch (RuntimeException e) { log("Native BLE protocol tick failed"); }
            }
            main.postDelayed(this, 100L);
        }
    };
    private final Runnable scanTimeout = () -> {
        if (!scanning) return;
        stopScan();
        log(scanDiagnostics.summary());
        if (autoConnectAfterScan && desiredConnection) {
            connectionState = "advertisement_unavailable";
            log("No matching Tesla BLE advertisement found; reconnect will back off");
            notifyState();
            scheduleReconnect();
        } else {
            connectionState = ready ? "connected" : "disconnected";
            log("BLE scan finished");
            notifyState();
        }
    };
    private final Runnable reconnectTask = () -> {
        if (desiredConnection && !ready && gatt == null) beginPassiveReconnectScan();
    };
    private final Runnable connectTimeout = () -> {
        if (gatt != null && !ready) failConnection("BLE connection setup timed out");
    };
    private final Runnable pollTask = this::runPoll;
    private final Runnable chargeTimeout = () -> {
        if (pollInFlight) finishChargeFailure("ChargeState request timed out");
    };
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result != null) main.post(() -> handleScanResult(result));
        }
        @Override public void onScanFailed(int errorCode) {
            main.post(() -> {
                scanning = false;
                connectionState = "scan_failed";
                log("BLE scan failed with code " + errorCode);
                log(scanDiagnostics.summary());
                notifyState();
                if (autoConnectAfterScan && desiredConnection) scheduleReconnect();
            });
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        apiTokenStore = new ApiTokenStore(this);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        desiredConnection = preferences.getBoolean(PREF_DESIRED, false);
        pollEnabled = preferences.getBoolean(PREF_POLL, false);
        pollIntervalSeconds = clampPollInterval(preferences.getInt(PREF_INTERVAL, DEFAULT_POLL_SECONDS));
        reconnectAttempts = Math.max(0, preferences.getInt(PREF_RETRIES, 0));
        lastChargeJson = preferences.getString(PREF_LAST_CHARGE, null);
        lastChargeAtMs = preferences.getLong(PREF_LAST_CHARGE_AT, 0L);
        currentVin = preferences.getString(PREF_VIN, "");
        if (!isValidVin(currentVin)) {
            String legacyVin = getSharedPreferences(LEGACY_ACTIVITY_PREFS, MODE_PRIVATE).getString(PREF_VIN, "");
            if (isValidVin(legacyVin)) saveVin(legacyVin);
        }
        tesla = new NativeTesla(this, this);
        if (isValidVin(currentVin)) initializeNativeForVin(currentVin);
        startApiServer();
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        serviceStarted = true;
        boolean foreground = ensureForeground();
        foregroundActive = foreground;
        String initialAction = intent == null ? null : intent.getAction();
        if (!foreground && (intent == null || isBleAction(initialAction, intent))) {
            log("Foreground service could not start; BLE command rejected");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (intent == null) {
            // START_STICKY recovery: scan passively for the saved vehicle only if a prior explicit
            // connect requested reconnection. We never connect directly to a remembered address.
            if (foreground && desiredConnection && isValidVin(currentVin)) beginPassiveReconnectScan();
            return START_STICKY;
        }
        String action = intent.getAction();
        if (action == null) action = ACTION_COMMAND;
        if (apiServer == null && !"stop".equals(intent.getStringExtra(EXTRA_COMMAND))) startApiServer();
        if (ACTION_UI_SCAN.equals(action)) {
            String vin = intent.getStringExtra(EXTRA_VIN);
            if (isValidVin(vin)) { saveVin(vin); initializeNativeForVin(vin); startUserScan(); }
            else log("Scan requires a valid saved vehicle VIN");
        } else if (ACTION_UI_CONNECT.equals(action)) {
            String vin = intent.getStringExtra(EXTRA_VIN);
            if (isValidVin(vin)) saveVin(vin);
            startExplicitConnection(isValidVin(currentVin) ? currentVin : vin);
        } else if (ACTION_UI_PAIR.equals(action)) {
            pairVehicle();
        } else if (ACTION_UI_READ.equals(action)) {
            requestCharge(false);
        } else if (ACTION_UI_DISCONNECT.equals(action)) {
            disconnectExplicitly();
        } else {
            handleCommand(intent.getStringExtra(EXTRA_COMMAND), intent);
        }
        if (!foreground && !isBleAction(action, intent)) stopSelf(startId);
        return START_STICKY;
    }

    private boolean ensureForeground() {
        createNotificationChannel();
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (SecurityException | IllegalArgumentException | IllegalStateException e) {
            connectionState = "foreground_permission_missing";
            log("Connected-device foreground service permission missing; BLE work is disabled");
            notifyState();
            return false;
        }
    }

    private boolean isBleAction(String action, Intent intent) {
        if (ACTION_UI_SCAN.equals(action) || ACTION_UI_CONNECT.equals(action)
                || ACTION_UI_PAIR.equals(action) || ACTION_UI_READ.equals(action)
                || ACTION_UI_DISCONNECT.equals(action)) return true;
        String command = intent == null ? "" : intent.getStringExtra(EXTRA_COMMAND);
        return "scan".equals(command) || "connect".equals(command) || "disconnect".equals(command) || "pair".equals(command)
                || "read-charge".equals(command) || "poll-on".equals(command);
    }

    private void handleCommand(String rawCommand, Intent intent) {
        String command = rawCommand == null ? "" : rawCommand.trim().toLowerCase(Locale.US);
        switch (command) {
            case "scan": {
                String requestedVin = intent.getStringExtra(EXTRA_VIN);
                if (isValidVin(requestedVin)) saveVin(requestedVin);
                if (isValidVin(currentVin)) { initializeNativeForVin(currentVin); startUserScan(); }
                else log("Scan requires a valid saved vehicle VIN");
                break;
            }
            case "connect": {
                String requestedVin = intent.getStringExtra(EXTRA_VIN);
                if (isValidVin(requestedVin)) saveVin(requestedVin);
                startExplicitConnection(currentVin);
                break;
            }
            case "disconnect": disconnectExplicitly(); break;
            case "status":
                log("Status: " + buildStatusJson());
                notifyState();
                break;
            case "read-charge": requestCharge(false); break;
            case "pair": pairVehicle(); break;
            case "poll-on":
                pollEnabled = true;
                preferences.edit().putBoolean(PREF_POLL, true).apply();
                pollFailures = 0;
                log("Periodic ChargeState polling enabled; interval=" + pollIntervalSeconds + "s; no-wake policy active");
                if (ready) schedulePoll(0L);
                notifyState();
                break;
            case "poll-off":
                pollEnabled = false;
                preferences.edit().putBoolean(PREF_POLL, false).apply();
                main.removeCallbacks(pollTask);
                log("Periodic ChargeState polling disabled");
                notifyState();
                break;
            case "poll-interval": {
                int seconds = intent.getIntExtra(EXTRA_INTERVAL_SECONDS, -1);
                if (seconds < 0) {
                    String text = intent.getStringExtra(EXTRA_INTERVAL_SECONDS);
                    try { seconds = Integer.parseInt(text); } catch (Exception ignored) { seconds = -1; }
                }
                if (seconds < MIN_POLL_SECONDS || seconds > MAX_POLL_SECONDS) {
                    log("Polling interval rejected; valid range is 15..3600 seconds");
                } else {
                    pollIntervalSeconds = seconds;
                    preferences.edit().putInt(PREF_INTERVAL, seconds).apply();
                    pollFailures = 0;
                    log("Polling interval set to " + seconds + " seconds");
                    if (ready && pollEnabled && !pollInFlight) schedulePoll(seconds * 1000L);
                    notifyState();
                }
                break;
            }
            case "storage-test":
                boolean ok = tesla != null && tesla.storageSelfTest();
                if (!ok) log("Encrypted storage self-test failed; existing vehicle key was not changed");
                break;
            case "set-api-token": {
                String token = intent.getStringExtra(EXTRA_TOKEN);
                boolean saved = apiTokenStore != null && apiTokenStore.setToken(token);
                tokenConfigured = saved || (apiTokenStore != null && apiTokenStore.isConfigured());
                log(saved ? "Local API bearer token configured and encrypted" : "API token rejected or storage failed; token value suppressed");
                notifyState();
                break;
            }
            case "stop": stopExplicitly(); break;
            default:
                log("Unsupported ADB command");
                break;
        }
    }

    private void startExplicitConnection(String vin) {
        if (!isValidVin(vin)) {
            log("No saved VIN is available; enter it in the app before connecting");
            connectionState = "vin_required";
            notifyState();
            return;
        }
        saveVin(vin);
        initializeNativeForVin(vin);
        desiredConnection = true;
        reconnectAttempts = 0;
        preferences.edit().putBoolean(PREF_DESIRED, true).putInt(PREF_RETRIES, 0).apply();
        if (ready) {
            log("Tesla BLE connection is already ready");
            return;
        }
        if (selectedDevice != null && vin.equals(selectedVin)
                && expectedAdvertisement.equals(expectedAdvertisement(vin))) {
            connectSelectedDevice();
        } else {
            autoConnectAfterScan = true;
            beginScan();
        }
    }

    private void startUserScan() {
        if (!hasBlePermissions()) {
            connectionState = "permission_required";
            log("BLE scan requires Bluetooth scan/connect permissions; grant them in the app UI");
            notifyState();
            return;
        }
        if (ready || gatt != null) {
            log("Disconnect the active BLE session before scanning for another device");
            return;
        }
        autoConnectAfterScan = false;
        beginScan();
    }

    private void beginPassiveReconnectScan() {
        if (!desiredConnection || !isValidVin(currentVin) || ready || gatt != null) return;
        initializeNativeForVin(currentVin);
        autoConnectAfterScan = true;
        beginScan();
    }

    private void beginScan() {
        if (!hasBlePermissions()) {
            connectionState = "permission_required";
            log("BLE permission missing; connection attempt stopped");
            desiredConnection = false;
            preferences.edit().putBoolean(PREF_DESIRED, false).apply();
            notifyState();
            return;
        }
        boolean bluetoothEnabled;
        try { bluetoothEnabled = adapter != null && adapter.isEnabled(); }
        catch (SecurityException e) { bluetoothEnabled = false; }
        if (!bluetoothEnabled) {
            connectionState = adapter == null ? "adapter_unavailable" : "bluetooth_disabled";
            log("Bluetooth is disabled or permission is missing; no wake request was sent");
            notifyState();
            return;
        }
        if (scanning) return;
        BluetoothLeScanner availableScanner;
        try { availableScanner = adapter.getBluetoothLeScanner(); }
        catch (SecurityException e) { availableScanner = null; }
        if (availableScanner == null) {
            connectionState = "scanner_unavailable";
            log("Bluetooth LE scanner unavailable");
            notifyState();
            if (autoConnectAfterScan && desiredConnection) scheduleReconnect();
            return;
        }
        if (expectedAdvertisement.isEmpty() && isValidVin(currentVin)) expectedAdvertisement = expectedAdvertisement(currentVin);
        scanner = availableScanner;
        foundDevices.clear();
        scanDiagnostics = new BleScanDiagnostics();
        scanning = true;
        connectionState = "scanning";
        try {
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
            scanner.startScan(new ArrayList<>(), settings, scanCallback);
            main.removeCallbacks(scanTimeout);
            main.postDelayed(scanTimeout, CONNECT_SCAN_MS);
            log(autoConnectAfterScan ? "Passively scanning for the saved Tesla advertisement; no wake request is sent" : "Scanning for matching Tesla BLE advertisement");
            notifyState();
        } catch (SecurityException | IllegalStateException e) {
            scanning = false;
            connectionState = "scan_failed";
            log("Bluetooth scan could not start; check Bluetooth permissions and adapter state");
            log(scanDiagnostics.summary());
            notifyState();
            if (autoConnectAfterScan && desiredConnection) scheduleReconnect();
        }
    }

    private void handleScanResult(ScanResult result) {
        if (!scanning || result.getDevice() == null || !hasBlePermissions()) return;
        BluetoothDevice device = result.getDevice();
        String scanRecordName = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
        boolean scanRecordNamePresent = scanRecordName != null && !scanRecordName.isEmpty();
        String cachedName = null;
        boolean cachedNameChecked = false;
        if (!scanRecordNamePresent) {
            cachedNameChecked = true;
            try { cachedName = device.getName(); } catch (SecurityException ignored) { }
        }
        boolean cachedNamePresent = cachedName != null && !cachedName.isEmpty();
        scanDiagnostics.recordResult(scanRecordNamePresent,
                expectedAdvertisement.equals(scanRecordName), cachedNameChecked,
                cachedNamePresent, expectedAdvertisement.equals(cachedName));

        // Keep v0.3's matching behavior unchanged for this diagnostic build. The cached name is
        // observed on Android 12+ but is not used to select/connect a device until verified.
        String name = scanRecordName;
        if (!scanRecordNamePresent && Build.VERSION.SDK_INT < 31) name = cachedName;
        if (!expectedAdvertisement.equals(name)) return;
        String address;
        try { address = device.getAddress(); } catch (SecurityException e) { return; }
        if (address == null || foundDevices.containsKey(address)) return;
        foundDevices.put(address, device);
        selectedDevice = device;
        selectedAddress = address;
        selectedVin = currentVin;
        log("Matching Tesla BLE advertisement found");
        Listener callback = listener;
        if (callback != null) callback.onDeviceFound(name, address, result.getRssi());
        notifyState();
        if (autoConnectAfterScan && desiredConnection) {
            stopScan();
            connectSelectedDevice();
        }
    }

    public void selectDevice(String address) {
        if (address == null) return;
        BluetoothDevice candidate = foundDevices.get(address);
        if (candidate == null) {
            log("Selected BLE device is no longer available; scan again");
            return;
        }
        selectedDevice = candidate;
        selectedAddress = address;
        log("Tesla BLE device selected");
        notifyState();
    }

    private void connectSelectedDevice() {
        if (ready || gatt != null) return;
        if (selectedDevice == null || !isValidVin(currentVin) || !currentVin.equals(selectedVin)) {
            log("No matching Tesla advertisement selected for the saved VIN; passive scan required");
            if (desiredConnection) beginScan();
            return;
        }
        if (!hasBlePermissions()) {
            connectionState = "permission_required";
            log("Bluetooth connect permission missing; no connection attempted");
            notifyState();
            return;
        }
        stopScan();
        try {
            initializeNativeForVin(currentVin);
            connectionState = "connecting";
            ready = false;
            writes.clear();
            writing = false;
            gatt = selectedDevice.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                connectionState = "connect_failed";
                log("Android BLE connection request returned no GATT handle");
                notifyState();
                scheduleReconnect();
                return;
            }
            main.removeCallbacks(connectTimeout);
            main.postDelayed(connectTimeout, 20_000L);
            notifyState();
        } catch (SecurityException | IllegalStateException e) {
            connectionState = "connect_failed";
            log("Bluetooth connection could not start; permissions or adapter state are unavailable");
            notifyState();
            scheduleReconnect();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt source, int status, int newState) {
            main.post(() -> {
                if (source != gatt) return;
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    connectionState = "discovering_services";
                    log("BLE GATT connected; discovering Tesla service");
                    try { source.discoverServices(); }
                    catch (SecurityException | IllegalStateException e) { failConnection("BLE service discovery could not start"); }
                    notifyState();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    main.removeCallbacks(connectTimeout);
                    closeGattOnly();
                    connectionState = "disconnected";
                    log("BLE GATT disconnected; vehicle key and pairing data were left unchanged");
                    notifyState();
                    if (desiredConnection) scheduleReconnect();
                }
            });
        }

        @Override public void onServicesDiscovered(BluetoothGatt source, int status) {
            main.post(() -> {
                if (source != gatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { failConnection("Tesla GATT service discovery failed"); return; }
                try {
                    BluetoothGattService service = source.getService(GATT_SERVICE);
                    tx = service == null ? null : service.getCharacteristic(GATT_WRITE);
                    BluetoothGattCharacteristic rx = service == null ? null : service.getCharacteristic(GATT_NOTIFY);
                    if (tx == null || rx == null) { failConnection("Required Tesla BLE service or characteristic missing"); return; }
                    source.requestMtu(247);
                    if (!source.setCharacteristicNotification(rx, true)) { failConnection("Could not enable Tesla BLE notifications"); return; }
                    BluetoothGattDescriptor descriptor = rx.getDescriptor(CCCD);
                    if (descriptor == null || !writeDescriptorCompat(source, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        failConnection("Could not configure Tesla BLE notifications");
                    }
                } catch (SecurityException | IllegalStateException e) {
                    failConnection("Tesla BLE notification setup failed; check Bluetooth permission");
                }
            });
        }

        @Override public void onMtuChanged(BluetoothGatt source, int newMtu, int status) {
            main.post(() -> { if (source == gatt && status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu; });
        }

        @Override public void onDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor, int status) {
            main.post(() -> {
                if (source != gatt || !CCCD.equals(descriptor.getUuid())) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { failConnection("Tesla BLE notification subscription failed"); return; }
                main.removeCallbacks(connectTimeout);
                ready = true;
                main.removeCallbacks(ticker);
                main.post(ticker);
                connectionState = "connected";
                reconnectAttempts = 0;
                preferences.edit().putInt(PREF_RETRIES, 0).apply();
                try { tesla.connected(true); }
                catch (RuntimeException e) { failConnection("Native Tesla BLE session could not start"); return; }
                log("Tesla BLE transport ready; authenticated telemetry requires a valid enrolled key");
                notifyState();
                if (pollEnabled) schedulePoll(0L);
            });
        }

        @Override public void onCharacteristicWrite(BluetoothGatt source, BluetoothGattCharacteristic characteristic, int status) {
            main.post(() -> {
                if (source != gatt) return;
                writing = false;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    writes.clear();
                    log("Tesla BLE write failed with code " + status);
                    return;
                }
                flushWrites();
            });
        }

        @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] value) {
            if (!GATT_NOTIFY.equals(characteristic.getUuid()) || value == null) return;
            byte[] copy = value.clone();
            main.post(() -> { if (source == gatt && ready) tesla.onNotification(copy); });
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic) {
            if (Build.VERSION.SDK_INT < 33 && GATT_NOTIFY.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                if (value != null) {
                    byte[] copy = value.clone();
                    main.post(() -> { if (source == gatt && ready) tesla.onNotification(copy); });
                }
            }
        }
    };

    @SuppressWarnings("deprecation")
    private static boolean writeDescriptorCompat(BluetoothGatt bluetoothGatt, BluetoothGattDescriptor descriptor, byte[] value) {
        if (Build.VERSION.SDK_INT >= 33) return bluetoothGatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS;
        descriptor.setValue(value);
        return bluetoothGatt.writeDescriptor(descriptor);
    }

    @Override public boolean onBleWrite(byte[] data) {
        if (Looper.myLooper() != Looper.getMainLooper() || !ready || gatt == null || tx == null || data == null || data.length == 0 || data.length > 8192) return false;
        int chunkSize = Math.max(20, mtu - 3);
        int chunks = (data.length + chunkSize - 1) / chunkSize;
        if (writes.size() + chunks > 256) return false;
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            writes.add(java.util.Arrays.copyOfRange(data, offset, Math.min(data.length, offset + chunkSize)));
        }
        flushWrites();
        return true;
    }

    @SuppressWarnings("deprecation")
    private void flushWrites() {
        if (writing || !ready || gatt == null || tx == null || writes.isEmpty()) return;
        byte[] next = writes.poll();
        writing = true;
        boolean accepted;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                accepted = gatt.writeCharacteristic(tx, next, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS;
            } else {
                tx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                tx.setValue(next);
                accepted = gatt.writeCharacteristic(tx);
            }
        } catch (SecurityException | IllegalStateException e) { accepted = false; }
        if (!accepted) {
            writing = false;
            writes.clear();
            log("Could not queue Tesla BLE write");
        }
    }

    private void pairVehicle() {
        if (!ready || !nativeInitialized) {
            log("Pair command requires an active BLE connection; no automatic enrollment was started");
            return;
        }
        if (pollInFlight) {
            log("Pair command deferred while a ChargeState read is in flight");
            return;
        }
        log("Manual Vehicle Monitor enrollment requested; Tesla key-card authorization is required");
        tesla.pairMonitorKey();
    }

    private void requestCharge(boolean fromPoll) {
        if (!ready || !nativeInitialized) {
            if (!fromPoll) log("ChargeState read requires an active BLE connection; use connect first");
            return;
        }
        if (pollInFlight) {
            if (!fromPoll) log("ChargeState request already in flight; overlapping read suppressed");
            return;
        }
        pollInFlight = true;
        main.removeCallbacks(chargeTimeout);
        main.postDelayed(chargeTimeout, CHARGE_TIMEOUT_MS);
        try {
            // Native code uses NO_WAKE_SKIP/NO_WAKE_FAIL; this path never intentionally wakes the car.
            tesla.readChargeState();
            if (!fromPoll) log("ChargeState read requested under no-wake policy");
        } catch (RuntimeException e) {
            finishChargeFailure("ChargeState read could not be started");
        }
    }

    private void runPoll() {
        if (!pollEnabled) return;
        if (!ready) return; // No BLE work while unavailable; reconnect scanning has its own backoff.
        if (pollInFlight) {
            schedulePoll(pollIntervalSeconds * 1000L);
            return;
        }
        requestCharge(true);
    }

    private void schedulePoll(long delayMs) {
        main.removeCallbacks(pollTask);
        if (pollEnabled && ready) main.postDelayed(pollTask, Math.max(0L, delayMs));
    }

    private void finishChargeFailure(String reason) {
        if (!pollInFlight) return;
        pollInFlight = false;
        main.removeCallbacks(chargeTimeout);
        pollFailures = Math.min(pollFailures + 1, 8);
        log(reason + "; polling will back off without sending a wake request");
        if (pollEnabled && ready) {
            long base = pollIntervalSeconds * 1000L;
            long multiplier = 1L << Math.min(pollFailures, 4);
            schedulePoll(Math.min(120_000L, base * multiplier));
        }
        notifyState();
    }

    private void scheduleReconnect() {
        if (!desiredConnection) return;
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            desiredConnection = false;
            preferences.edit().putBoolean(PREF_DESIRED, false).apply();
            connectionState = "reconnect_paused";
            log("Bounded reconnect attempts exhausted; manual connect is required");
            notifyState();
            return;
        }
        long delay = Math.min(MAX_RECONNECT_MS, INITIAL_RECONNECT_MS << Math.min(reconnectAttempts, 4));
        reconnectAttempts++;
        preferences.edit().putInt(PREF_RETRIES, reconnectAttempts).apply();
        main.removeCallbacks(reconnectTask);
        main.postDelayed(reconnectTask, delay);
        connectionState = "reconnecting";
        notifyState();
    }

    private void failConnection(String reason) {
        main.removeCallbacks(connectTimeout);
        closeGattOnly();
        connectionState = "connect_failed";
        log(reason);
        notifyState();
        if (desiredConnection) scheduleReconnect();
    }

    private void disconnectExplicitly() {
        desiredConnection = false;
        reconnectAttempts = 0;
        preferences.edit().putBoolean(PREF_DESIRED, false).putInt(PREF_RETRIES, 0).apply();
        main.removeCallbacks(reconnectTask);
        main.removeCallbacks(scanTimeout);
        main.removeCallbacks(connectTimeout);
        main.removeCallbacks(pollTask);
        stopScan();
        closeGattOnly();
        connectionState = "disconnected";
        log("BLE disconnected by request; enrolled key and stored telemetry were preserved");
        notifyState();
    }

    private void stopExplicitly() {
        disconnectExplicitly();
        pollEnabled = false;
        preferences.edit().putBoolean(PREF_POLL, false).apply();
        if (apiServer != null) { apiServer.close(); apiServer = null; apiAvailable = false; }
        if (tesla != null && nativeInitialized) {
            try { tesla.close(); } catch (RuntimeException ignored) { }
            nativeInitialized = false;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundActive = false;
        serviceStarted = false;
        log("Foreground BLE service stopped");
        stopSelf();
    }

    private void closeGattOnly() {
        ready = false;
        writing = false;
        writes.clear();
        mtu = 23;
        pollInFlight = false;
        main.removeCallbacks(chargeTimeout);
        main.removeCallbacks(ticker);
        if (nativeInitialized) {
            try { tesla.connected(false); } catch (RuntimeException ignored) { }
        }
        BluetoothGatt old = gatt;
        gatt = null;
        tx = null;
        if (old != null) {
            try { old.disconnect(); } catch (SecurityException | IllegalStateException ignored) { }
            try { old.close(); } catch (IllegalStateException ignored) { }
        }
    }

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        main.removeCallbacks(scanTimeout);
        try { if (scanner != null) scanner.stopScan(scanCallback); }
        catch (SecurityException | IllegalStateException ignored) { }
    }

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void saveVin(String vin) {
        if (!isValidVin(vin)) return;
        String normalized = vin.trim().toUpperCase(Locale.US);
        if (!normalized.equals(currentVin)) {
            selectedDevice = null;
            selectedAddress = "";
            selectedVin = "";
            foundDevices.clear();
        }
        currentVin = normalized;
        expectedAdvertisement = expectedAdvertisement(currentVin);
        preferences.edit().putString(PREF_VIN, currentVin).apply();
        // Preserve the v0.1 GUI preference file for downgrade compatibility; this only stores the VIN.
        getSharedPreferences(LEGACY_ACTIVITY_PREFS, MODE_PRIVATE).edit().putString(PREF_VIN, currentVin).apply();
    }

    private void initializeNativeForVin(String vin) {
        if (!isValidVin(vin)) return;
        String normalized = vin.trim().toUpperCase(Locale.US);
        if (nativeInitialized && normalized.equals(nativeVin)) return;
        if (ready || gatt != null) {
            log("Refusing to change the active native vehicle while GATT is connected");
            return;
        }
        if (nativeInitialized) {
            try { tesla.close(); } catch (RuntimeException ignored) { }
            nativeInitialized = false;
        }
        try {
            tesla.useVehicle(normalized);
            tesla.init(normalized);
            nativeVin = normalized;
            nativeInitialized = true;
            keyPresent = tesla.hasPrivateKey();
            authenticationState = keyPresent ? "enrolled_key_present_not_yet_verified" : "not_enrolled";
            notifyState();
        } catch (RuntimeException e) {
            nativeInitialized = false;
            keyPresent = false;
            authenticationState = "initialization_failed";
            log("Native Tesla protocol initialization failed; existing key was not changed");
            notifyState();
        }
    }

    private static boolean isValidVin(String vin) {
        return vin != null && vin.trim().toUpperCase(Locale.US).matches("[A-HJ-NPR-Z0-9]{17}");
    }

    private static String expectedAdvertisement(String vin) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(vin.getBytes(StandardCharsets.US_ASCII));
            StringBuilder result = new StringBuilder("S");
            for (int i = 0; i < 8; i++) result.append(String.format(Locale.US, "%02x", digest[i] & 0xff));
            return result.append('C').toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static int clampPollInterval(int seconds) {
        return Math.max(MIN_POLL_SECONDS, Math.min(MAX_POLL_SECONDS, seconds));
    }

    private void startApiServer() {
        try {
            apiServer = new TelemetryHttpServer(this::buildTelemetryJson, this::buildStatusJson, apiTokenStore::getToken);
            apiServer.start();
            apiAvailable = true;
            log("Local telemetry API listening on 127.0.0.1:8765; bearer authentication required");
        } catch (IOException | RuntimeException e) {
            apiServer = null;
            apiAvailable = false;
            log("Local telemetry API could not bind loopback port 8765");
        }
    }

    private String buildTelemetryJson() {
        synchronized (stateLock) {
            JSONObject response = new JSONObject();
            long now = System.currentTimeMillis();
            boolean available = false;
            boolean fresh = false;
            JSONObject last = null;
            if (lastChargeJson != null) {
                try { last = new JSONObject(lastChargeJson); available = true; }
                catch (JSONException ignored) { last = null; }
            }
            long ageSeconds = lastChargeAtMs <= 0 ? -1 : Math.max(0L, (now - lastChargeAtMs) / 1000L);
            long freshnessSeconds = Math.max(60L, (long) pollIntervalSeconds * 3L);
            fresh = available && ageSeconds >= 0 && ageSeconds <= freshnessSeconds;
            try {
                response.put("schema_version", 1);
                response.put("source", "tesla_ble");
                response.put("available", available);
                response.put("fresh", fresh);
                response.put("connection_status", connectionState);
                response.put("last_successful_update", lastChargeAtMs > 0 ? isoTime(lastChargeAtMs) : JSONObject.NULL);
                response.put("telemetry_age_seconds", ageSeconds >= 0 ? ageSeconds : JSONObject.NULL);
                response.put("battery_percent", fresh ? valueOrNull(last, "battery_percent") : JSONObject.NULL);
                response.put("usable_battery_percent", fresh ? valueOrNull(last, "usable_battery_percent") : JSONObject.NULL);
                response.put("range_km", fresh ? valueOrNull(last, "range_km") : JSONObject.NULL);
                response.put("charging", fresh ? valueOrNull(last, "charging") : JSONObject.NULL);
                response.put("last_known", last == null ? JSONObject.NULL : last);
            } catch (JSONException ignored) { return "{\"error\":\"telemetry_unavailable\"}"; }
            return response.toString();
        }
    }

    private String buildStatusJson() {
        JSONObject response = new JSONObject();
        JSONObject api = new JSONObject();
        try {
            response.put("schema_version", 1);
            response.put("service_running", serviceStarted);
            response.put("foreground", foregroundActive);
            response.put("ble_connection_status", connectionState);
            response.put("connected", ready);
            response.put("authentication_status", authenticationState);
            response.put("enrolled_key_present", keyPresent);
            response.put("last_authenticated_update", lastChargeAtMs > 0 ? isoTime(lastChargeAtMs) : JSONObject.NULL);
            response.put("polling_enabled", pollEnabled);
            response.put("poll_interval_seconds", pollIntervalSeconds);
            response.put("telemetry_available", lastChargeJson != null && lastChargeAtMs > 0);
            api.put("enabled", true);
            api.put("running", apiServer != null && apiServer.isRunning());
            api.put("listen_address", "127.0.0.1");
            api.put("port", 8765);
            tokenConfigured = apiTokenStore != null && apiTokenStore.isConfigured();
            api.put("token_configured", tokenConfigured);
            response.put("api", api);
        } catch (JSONException ignored) { return "{\"error\":\"status_unavailable\"}"; }
        return response.toString();
    }

    private static Object valueOrNull(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return JSONObject.NULL;
        return object.opt(key);
    }

    private static String isoTime(long timeMs) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timeMs));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Tesla BLE Monitor", NotificationManager.IMPORTANCE_LOW));
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        return builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Tesla BLE Monitor")
                .setContentText("BLE " + connectionState + (pollEnabled ? " · polling on" : " · polling off"))
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void log(String message) {
        Log.i(TAG, message);
        Listener callback = listener;
        if (callback != null) callback.onLog(message);
        updateNotification();
    }

    private void updateNotification() {
        if (!serviceStarted || !foregroundActive) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void notifyState() {
        updateNotification();
        Listener callback = listener;
        if (callback != null) callback.onStatus(buildStatusJson());
    }

    public void setListener(Listener value) {
        listener = value;
        if (value != null) value.onStatus(buildStatusJson());
    }

    public void clearListener(Listener value) {
        if (listener == value) listener = null;
    }

    public String getStatusJson() { return buildStatusJson(); }
    public String getTelemetryJson() { return buildTelemetryJson(); }

    @Override public void onMessage(String message) {
        if (message == null) return;
        if (message.startsWith("Native Tesla protocol initialized; enrolled private key:")) {
            keyPresent = message.endsWith("present");
            authenticationState = keyPresent ? "enrolled_key_present_not_yet_verified" : "not_enrolled";
            log("Tesla protocol initialized; enrolled key status=" + (keyPresent ? "present" : "not_enrolled"));
            notifyState();
            return;
        }
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("charge state read skipped") || lower.contains("charge state read failed")) {
            finishChargeFailure("ChargeState unavailable; native no-wake policy skipped or failed the request");
        }
        if (lower.contains("token") || lower.contains("bearer") || lower.contains("secret")) {
            log("Sensitive native diagnostic detail suppressed");
        } else {
            log(message);
        }
    }

    @Override public void onTelemetry(String json) {
        if (json == null) return;
        try {
            JSONObject value = new JSONObject(json);
            long timestamp = System.currentTimeMillis();
            synchronized (stateLock) {
                lastChargeJson = value.toString();
                lastChargeAtMs = timestamp;
                preferences.edit().putString(PREF_LAST_CHARGE, lastChargeJson)
                        .putLong(PREF_LAST_CHARGE_AT, timestamp).apply();
            }
            authenticationState = "authenticated_charge_state_received";
            keyPresent = true;
            pollInFlight = false;
            pollFailures = 0;
            main.removeCallbacks(chargeTimeout);
            log("Authenticated ChargeState received and stored");
            Listener callback = listener;
            if (callback != null) callback.onTelemetry(buildTelemetryJson());
            notifyState();
            if (pollEnabled && ready) schedulePoll(pollIntervalSeconds * 1000L);
        } catch (JSONException e) {
            finishChargeFailure("Authenticated telemetry payload could not be decoded");
        }
    }

    @Override public void onDestroy() {
        serviceStarted = false;
        foregroundActive = false;
        main.removeCallbacks(ticker);
        main.removeCallbacks(scanTimeout);
        main.removeCallbacks(reconnectTask);
        main.removeCallbacks(connectTimeout);
        main.removeCallbacks(pollTask);
        main.removeCallbacks(chargeTimeout);
        stopScan();
        closeGattOnly();
        if (apiServer != null) { apiServer.close(); apiServer = null; }
        if (tesla != null && nativeInitialized) {
            try { tesla.close(); } catch (RuntimeException ignored) { }
            nativeInitialized = false;
        }
        super.onDestroy();
    }
}
