package org.opencharge.teslable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small authenticated, read-only HTTP listener. Production callers bind only 127.0.0.1. */
public final class TelemetryHttpServer implements AutoCloseable {
    private static final int DEFAULT_PORT = 8765;
    private static final int MAX_LINE_BYTES = 8192;
    private static final int MAX_HEADER_BYTES = 16384;
    private static final int MAX_HEADER_COUNT = 64;
    private static final int MAX_RESPONSE_BYTES = 65536;
    private final Supplier<String> telemetryJson;
    private final Supplier<String> statusJson;
    private final Supplier<String> bearerToken;
    private final Consumer<Boolean> driveSubscription;
    private final InetAddress bindAddress;
    private final int requestedPort;
    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;

    public TelemetryHttpServer(Supplier<String> telemetryJson, Supplier<String> statusJson,
                               Supplier<String> bearerToken) {
        this(telemetryJson, statusJson, bearerToken, ignored -> { }, loopbackAddress(), DEFAULT_PORT);
    }

    public TelemetryHttpServer(Supplier<String> telemetryJson, Supplier<String> statusJson,
                               Supplier<String> bearerToken, Consumer<Boolean> driveSubscription) {
        this(telemetryJson, statusJson, bearerToken, driveSubscription, loopbackAddress(), DEFAULT_PORT);
    }

    public TelemetryHttpServer(Supplier<String> telemetryJson, Supplier<String> statusJson,
                               Supplier<String> bearerToken, InetAddress bindAddress, int port) {
        this(telemetryJson, statusJson, bearerToken, ignored -> { }, bindAddress, port);
    }

    public TelemetryHttpServer(Supplier<String> telemetryJson, Supplier<String> statusJson,
                               Supplier<String> bearerToken, Consumer<Boolean> driveSubscription,
                               InetAddress bindAddress, int port) {
        if (telemetryJson == null || statusJson == null || bearerToken == null || driveSubscription == null) {
            throw new IllegalArgumentException("providers and subscription callback must not be null");
        }
        if (bindAddress == null || !bindAddress.isLoopbackAddress()
                || !"127.0.0.1".equals(bindAddress.getHostAddress())) {
            throw new IllegalArgumentException("listener must bind exactly to IPv4 loopback 127.0.0.1");
        }
        if (port < 0 || port > 65535) throw new IllegalArgumentException("invalid port");
        this.telemetryJson = telemetryJson;
        this.statusJson = statusJson;
        this.bearerToken = bearerToken;
        this.driveSubscription = driveSubscription;
        this.bindAddress = bindAddress;
        this.requestedPort = port;
    }

    private static InetAddress loopbackAddress() {
        try { return InetAddress.getByName("127.0.0.1"); }
        catch (UnknownHostException e) { throw new IllegalStateException("IPv4 loopback unavailable", e); }
    }

    public synchronized void start() throws IOException {
        if (running) return;
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(bindAddress, requestedPort), 8);
            socket.setSoTimeout(500);
        } catch (IOException e) {
            try { socket.close(); } catch (IOException ignored) { }
            throw e;
        }
        serverSocket = socket;
        running = true;
        Thread thread = new Thread(() -> serve(socket), "TeslaBLE-Telemetry-API");
        thread.setDaemon(true);
        acceptThread = thread;
        thread.start();
    }

    public InetAddress getBindAddress() { return bindAddress; }
    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null || !socket.isBound() ? -1 : socket.getLocalPort();
    }
    public boolean isRunning() {
        ServerSocket socket = serverSocket;
        return running && socket != null && socket.isBound() && !socket.isClosed();
    }

    private void serve(ServerSocket listener) {
        try {
            while (running && serverSocket == listener) {
                Socket acceptedSocket;
                try {
                    acceptedSocket = listener.accept();
                } catch (SocketTimeoutException ignored) {
                    // Timed accept permits prompt shutdown without a busy loop.
                    continue;
                } catch (IOException e) {
                    break;
                }
                try (Socket client = acceptedSocket) {
                    client.setSoTimeout(5000);
                    handle(client);
                } catch (IOException ignored) {
                    // A client reset/write failure must not terminate the listener.
                }
            }
        } finally {
            synchronized (this) {
                try { listener.close(); } catch (IOException ignored) { }
                // A previous worker must never clear a newer listener's state.
                if (serverSocket == listener) {
                    serverSocket = null;
                    acceptThread = null;
                    running = false;
                }
            }
        }
    }

    private void handle(Socket client) throws IOException {
        Request request;
        try {
            request = readRequest(client.getInputStream());
        } catch (HeaderTooLarge e) {
            respond(client.getOutputStream(), 431, "Request Header Fields Too Large", error("headers_too_large"));
            return;
        } catch (BadRequest e) {
            respond(client.getOutputStream(), 400, "Bad Request", error("bad_request"));
            return;
        } catch (SocketTimeoutException e) {
            respond(client.getOutputStream(), 408, "Request Timeout", error("request_timeout"));
            return;
        }
        if (request == null) return;
        boolean telemetryRoute = "/v1/telemetry".equals(request.path);
        boolean statusRoute = "/v1/status".equals(request.path);
        boolean subscribeRoute = "/v1/telemetry/subscribe".equals(request.path);
        boolean unsubscribeRoute = "/v1/telemetry/unsubscribe".equals(request.path);
        if (!telemetryRoute && !statusRoute && !subscribeRoute && !unsubscribeRoute) {
            respond(client.getOutputStream(), 404, "Not Found", error("not_found"));
            return;
        }
        String expected = bearerToken.get();
        if (expected == null || expected.isEmpty()) {
            respond(client.getOutputStream(), 503, "Service Unavailable", error("api_token_not_configured"));
            return;
        }
        if (!authorized(request.authorization, expected)) {
            respond(client.getOutputStream(), 401, "Unauthorized", error("unauthorized"));
            return;
        }
        if (subscribeRoute || unsubscribeRoute) {
            if (!"POST".equals(request.method)) {
                respond(client.getOutputStream(), 405, "Method Not Allowed", error("method_not_allowed"));
                return;
            }
            boolean enable = subscribeRoute;
            try {
                driveSubscription.accept(enable);
                respond(client.getOutputStream(), 200, "OK", "{\"subscribed\":" + enable + "}");
            } catch (RuntimeException e) {
                respond(client.getOutputStream(), 500, "Internal Server Error", error("subscription_unavailable"));
            }
            return;
        }
        if (!"GET".equals(request.method)) {
            respond(client.getOutputStream(), 405, "Method Not Allowed", error("method_not_allowed"));
            return;
        }
        if (telemetryRoute && request.driveHeartbeat) {
            try {
                driveSubscription.accept(true);
            } catch (RuntimeException e) {
                respond(client.getOutputStream(), 500, "Internal Server Error", error("subscription_unavailable"));
                return;
            }
        }
        try {
            String body = telemetryRoute ? telemetryJson.get() : statusJson.get();
            if (body == null || body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                respond(client.getOutputStream(), 500, "Internal Server Error", error("response_unavailable"));
            } else {
                respond(client.getOutputStream(), 200, "OK", body);
            }
        } catch (RuntimeException e) {
            // Do not log request headers, tokens, or exception text.
            respond(client.getOutputStream(), 500, "Internal Server Error", error("response_unavailable"));
        }
    }

    private static boolean authorized(String header, String expected) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return false;
        String provided = header.substring(7).trim();
        if (provided.isEmpty() || provided.length() > 512) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                     provided.getBytes(StandardCharsets.UTF_8));
    }

    private static String error(String name) { return "{\"error\":\"" + name + "\"}"; }

    private static void respond(OutputStream output, int status, String reason, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n"
            + "Content-Length: " + payload.length + "\r\n"
            + "Cache-Control: no-store\r\n"
            + "Pragma: no-cache\r\n"
            + "X-Content-Type-Options: nosniff\r\n"
            + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(payload);
        output.flush();
    }

    private static final class Request {
        final String method;
        final String path;
        final String authorization;
        final boolean driveHeartbeat;
        Request(String method, String path, String authorization, boolean driveHeartbeat) {
            this.method = method; this.path = path; this.authorization = authorization;
            this.driveHeartbeat = driveHeartbeat;
        }
    }
    private static final class BadRequest extends Exception {
        private static final long serialVersionUID = 1L;
    }
    private static final class HeaderTooLarge extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static Request readRequest(InputStream input) throws IOException, BadRequest, HeaderTooLarge {
        int[] total = {0};
        String first = readLine(input, total);
        if (first == null) return null;
        String[] parts = first.split(" ", -1);
        if (parts.length != 3 || parts[0].isEmpty() || !parts[2].startsWith("HTTP/1.")) throw new BadRequest();
        Map<String, String> headers = new HashMap<>();
        int count = 0;
        while (true) {
            String line = readLine(input, total);
            if (line == null) throw new BadRequest();
            if (line.isEmpty()) break;
            if (++count > MAX_HEADER_COUNT) throw new HeaderTooLarge();
            int colon = line.indexOf(':');
            if (colon <= 0) throw new BadRequest();
            String name = line.substring(0, colon).trim().toLowerCase(Locale.US);
            String value = line.substring(colon + 1).trim();
            if (name.isEmpty()) throw new BadRequest();
            if ("authorization".equals(name) && headers.containsKey(name)) throw new BadRequest();
            if (headers.containsKey(name)) headers.put(name, headers.get(name) + "," + value);
            else headers.put(name, value);
        }
        if (headers.containsKey("transfer-encoding")) throw new BadRequest();
        String contentLength = headers.get("content-length");
        if (contentLength != null && !"0".equals(contentLength)) throw new BadRequest();
        return new Request(parts[0], parts[1], headers.get("authorization"),
                "active".equalsIgnoreCase(headers.get("x-tesla-drive-subscription")));
    }

    private static String readLine(InputStream input, int[] total) throws IOException, HeaderTooLarge {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        boolean sawAny = false;
        while (true) {
            int b = input.read();
            if (b < 0) return sawAny ? decodeLine(line) : null;
            sawAny = true;
            total[0]++;
            if (total[0] > MAX_HEADER_BYTES || line.size() >= MAX_LINE_BYTES) throw new HeaderTooLarge();
            if (b == '\n') return decodeLine(line);
            if (b != '\r') line.write(b);
        }
    }

    private static String decodeLine(ByteArrayOutputStream line) {
        return new String(line.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    @Override public void close() {
        Thread thread;
        synchronized (this) {
            running = false;
            ServerSocket socket = serverSocket;
            serverSocket = null;
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) { }
            }
            thread = acceptThread;
            acceptThread = null;
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try { thread.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
