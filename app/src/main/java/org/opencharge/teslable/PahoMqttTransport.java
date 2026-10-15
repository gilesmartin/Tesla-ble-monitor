package org.opencharge.teslable;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/** Paho transport adapter. It never waits for publish acknowledgements or logs failure details. */
public final class PahoMqttTransport implements MqttBridgePublisher.Transport {
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "TeslaMqttReconnect");
        thread.setDaemon(true);
        return thread;
    });
    private volatile MqttAsyncClient client;
    private volatile MqttConnectOptions connectOptions;
    private volatile Listener listener;
    private ScheduledFuture<?> retryTask;
    private int retryAttempt;
    private boolean closed;

    @Override public synchronized void connect(String brokerUri, String clientId, String username,
            char[] password, String willTopic, String willPayload, Listener callback) {
        if (callback == null || closed) return;
        listener = callback;
        if (client != null) {
            if (!client.isConnected()) scheduleRetry();
            return;
        }

        try {
            MqttAsyncClient created = new MqttAsyncClient(brokerUri, clientId, new MemoryPersistence());
            client = created;
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setHttpsHostnameVerificationEnabled(true);
            options.setAutomaticReconnect(false);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            options.setUserName(username);
            options.setPassword(password);
            options.setWill(willTopic, willPayload.getBytes(StandardCharsets.UTF_8), 1, true);
            connectOptions = options;
            created.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    Listener target;
                    synchronized (PahoMqttTransport.this) {
                        if (closed) return;
                        retryAttempt = 0;
                        if (retryTask != null) retryTask.cancel(false);
                        retryTask = null;
                        target = listener;
                    }
                    if (target != null) target.onConnected();
                }

                @Override public void connectionLost(Throwable cause) {
                    Listener target;
                    synchronized (PahoMqttTransport.this) {
                        if (closed) return;
                        target = listener;
                        scheduleRetry();
                    }
                    if (target != null) target.onConnectionLost();
                }

                @Override public void messageArrived(String topic, MqttMessage message) {
                    Listener target = listener;
                    if (target != null && message != null) {
                        target.onMessage(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
                    }
                }

                @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) { }
            });
            connectOnce(created, options);
        } catch (MqttException | RuntimeException ignored) {
            Listener target = listener;
            if (target != null) target.onConnectionLost();
            scheduleRetry();
        }
    }

    private void connectOnce(MqttAsyncClient target, MqttConnectOptions options) {
        try {
            target.connect(options, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken asyncActionToken) { }
                @Override public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Listener callback;
                    synchronized (PahoMqttTransport.this) {
                        if (closed) return;
                        callback = listener;
                        scheduleRetry();
                    }
                    if (callback != null) callback.onConnectionLost();
                }
            });
        } catch (MqttException | RuntimeException ignored) {
            synchronized (this) { scheduleRetry(); }
        }
    }

    private synchronized void scheduleRetry() {
        if (closed || client == null || retryExecutor.isShutdown()
                || (retryTask != null && !retryTask.isDone())) return;
        int exponent = Math.min(retryAttempt++, 6);
        long delaySeconds = Math.min(300L, 5L << exponent);
        retryTask = retryExecutor.schedule(() -> {
            MqttAsyncClient current;
            MqttConnectOptions options;
            synchronized (PahoMqttTransport.this) {
                retryTask = null;
                if (closed || client == null || client.isConnected()) return;
                current = client;
                options = connectOptions;
            }
            if (options != null) connectOnce(current, options);
        }, delaySeconds, TimeUnit.SECONDS);
    }

    @Override public void publish(String topic, String payload, int qos, boolean retained) {
        MqttAsyncClient current = client;
        if (current == null || !current.isConnected()) return;
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(retained);
            current.publish(topic, message);
        } catch (MqttException | RuntimeException ignored) { }
    }

    @Override public void subscribe(String topic, int qos) {
        MqttAsyncClient current = client;
        if (current == null || !current.isConnected()) return;
        try { current.subscribe(topic, qos); }
        catch (MqttException | RuntimeException ignored) { }
    }

    @Override public boolean isConnected() {
        MqttAsyncClient current = client;
        return current != null && current.isConnected();
    }

    @Override public void disconnect() {
        MqttAsyncClient current;
        synchronized (this) {
            if (closed) return;
            closed = true;
            if (retryTask != null) retryTask.cancel(false);
            retryTask = null;
            retryExecutor.shutdownNow();
            current = client;
            client = null;
            connectOptions = null;
            listener = null;
        }
        if (current == null) return;
        if (!current.isConnected()) {
            closeAsync(current);
            return;
        }
        try {
            current.disconnect(1000, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken asyncActionToken) { closeAsync(current); }
                @Override public void onFailure(IMqttToken asyncActionToken, Throwable exception) { closeAsync(current); }
            });
        } catch (MqttException | RuntimeException ignored) {
            closeAsync(current);
        }
    }

    private static void closeAsync(MqttAsyncClient target) {
        Thread closer = new Thread(() -> {
            try { target.close(); } catch (MqttException | RuntimeException ignored) { }
        }, "TeslaMqttClose");
        closer.setDaemon(true);
        closer.start();
    }
}
