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
    private volatile boolean closed;
    private volatile boolean closing;
    private final MqttPublishQueue publishQueue = new MqttPublishQueue(this::sendQueued,
            (work, delay) -> retryExecutor.schedule(work, delay, TimeUnit.MILLISECONDS), 512);

    @Override public synchronized void connect(String brokerUri, String clientId, String username,
            char[] password, String willTopic, String willPayload, Listener callback) {
        if (callback == null || closed || closing) return;
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
                        if (closed || closing) return;
                        retryAttempt = 0;
                        if (retryTask != null) retryTask.cancel(false);
                        retryTask = null;
                        target = listener;
                    }
                    publishQueue.setConnected(true);
                    if (target != null) target.onConnected();
                }

                @Override public void connectionLost(Throwable cause) {
                    Listener target;
                    synchronized (PahoMqttTransport.this) {
                        if (closed || closing) return;
                        target = listener;
                        scheduleRetry();
                    }
                    publishQueue.setConnected(false);
                    if (target != null) target.onConnectionLost();
                }

                @Override public void messageArrived(String topic, MqttMessage message) {
                    Listener target = listener;
                    if (target != null && message != null) {
                        target.onMessage(topic, new String(message.getPayload(), StandardCharsets.UTF_8),
                                message.isRetained(), message.isDuplicate());
                    }
                }

                @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) { }
            });
            connectOnce(created, options);
        } catch (MqttException | RuntimeException ignored) {
            Listener target = listener;
            publishQueue.setConnected(false);
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
                        if (closed || closing) return;
                        callback = listener;
                        scheduleRetry();
                    }
                    publishQueue.setConnected(false);
                    if (callback != null) callback.onConnectionLost();
                }
            });
        } catch (MqttException | RuntimeException ignored) {
            synchronized (this) { scheduleRetry(); }
        }
    }

    private synchronized void scheduleRetry() {
        if (closed || closing || client == null || retryExecutor.isShutdown()
                || (retryTask != null && !retryTask.isDone())) return;
        int exponent = Math.min(retryAttempt++, 6);
        long delaySeconds = Math.min(300L, 5L << exponent);
        retryTask = retryExecutor.schedule(() -> {
            MqttAsyncClient current;
            MqttConnectOptions options;
            synchronized (PahoMqttTransport.this) {
                retryTask = null;
                if (closed || closing || client == null || client.isConnected()) return;
                current = client;
                options = connectOptions;
            }
            if (options != null) connectOnce(current, options);
        }, delaySeconds, TimeUnit.SECONDS);
    }

    @Override public void publish(String topic, String payload, int qos, boolean retained) {
        if (closed || closing || !isConnected()) throw new IllegalStateException("MQTT is disconnected");
        if (topic == null || topic.isEmpty() || payload == null || qos < 0 || qos > 2
                || topic.length() > 512 || payload.getBytes(StandardCharsets.UTF_8).length > 32768) {
            throw new IllegalArgumentException("Invalid MQTT publication");
        }
        publishQueue.offer(topic, payload, qos, retained);
    }

    private void sendQueued(String topic, String payload, int qos, boolean retained, MqttPublishQueue.Callback done) {
        MqttAsyncClient current = client;
        if (current == null || !current.isConnected()) { done.complete(false); return; }
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        message.setRetained(retained);
        try {
            current.publish(topic, message, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) { done.complete(true); }
                @Override public void onFailure(IMqttToken token, Throwable error) { done.complete(false); }
            });
        } catch (MqttException ignored) { done.complete(false); }
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

    @Override public synchronized void disconnect() {
        if (closed || closing) return;
        closing = true;
        if (retryTask != null) retryTask.cancel(false);
        listener = null;
        publishQueue.closeWhenDrained(this::finishDisconnect, 3000L);
    }

    private void finishDisconnect() {
        MqttAsyncClient current;
        synchronized (this) {
            closed = true;
            current = client;
            client = null;
            connectOptions = null;
        }
        retryExecutor.shutdownNow();
        if (current != null) closeAsync(current);
    }

    private static void closeAsync(MqttAsyncClient target) {
        Thread closer = new Thread(() -> {
            // Suppress MQTT DISCONNECT so the broker also applies the offline will if draining timed out.
            try { target.disconnectForcibly(0L, 0L, false); } catch (MqttException | RuntimeException ignored) {}
            try { target.close(true); } catch (MqttException | RuntimeException ignored) {}
        }, "TeslaMqttClose");
        closer.setDaemon(true);
        closer.start();
    }
}
