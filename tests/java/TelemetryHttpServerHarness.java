package org.opencharge.teslable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Real-loopback HTTP integration checks; no Android framework or external network. */
public final class TelemetryHttpServerHarness {
    private static final String TOKEN = "v03-test-token-that-is-never-logged-0123456789";
    private static final class Reply {
        final int status;
        final String headers;
        final String body;
        Reply(int status, String headers, String body) {
            this.status = status; this.headers = headers; this.body = body;
        }
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static Reply request(int port, String method, String path, String[] authHeaders, int padBytes)
            throws IOException {
        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port)) {
            socket.setSoTimeout(2500);
            StringBuilder request = new StringBuilder();
            request.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: 127.0.0.1\r\n");
            if (authHeaders != null) for (String h : authHeaders) request.append(h).append("\r\n");
            if (padBytes > 0) request.append("X-Pad: ").append("x".repeat(padBytes)).append("\r\n");
            request.append("Connection: close\r\n\r\n");
            socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] block = new byte[2048];
            int n;
            try {
                while ((n = socket.getInputStream().read(block)) >= 0) bytes.write(block, 0, n);
            } catch (SocketException resetAfterResponse) {
                if (bytes.size() == 0) throw resetAfterResponse;
            }
            String response = bytes.toString(StandardCharsets.UTF_8);
            int sep = response.indexOf("\r\n\r\n");
            check(sep >= 0, "malformed HTTP response: " + response);
            String[] lines = response.substring(0, sep).split("\r\n");
            int status = Integer.parseInt(lines[0].split(" ")[1]);
            StringBuilder headers = new StringBuilder();
            for (int i = 1; i < lines.length; i++) headers.append(lines[i]).append('\n');
            return new Reply(status, headers.toString(), response.substring(sep + 4));
        }
    }
    private static String[] bearer(String token) { return new String[] { "Authorization: Bearer " + token }; }
    public static void main(String[] args) throws Exception {
        AtomicReference<String> token = new AtomicReference<>(TOKEN);
        TelemetryHttpServer server = new TelemetryHttpServer(
            () -> "{\"available\":false,\"fresh\":false}",
            () -> "{\"service_running\":true}", token::get,
            InetAddress.getByName("127.0.0.1"), 0);
        server.start();
        try {
            check(server.getBindAddress().getHostAddress().equals("127.0.0.1"), "listener must bind IPv4 loopback");
            check(server.getPort() > 0, "ephemeral port was not allocated");
            Reply missing = request(server.getPort(), "GET", "/v1/telemetry", null, 0);
            check(missing.status == 401, "missing bearer token must be rejected");
            check(!missing.body.contains(TOKEN), "secret token must not be echoed");
            Reply invalid = request(server.getPort(), "GET", "/v1/status", bearer("wrong-token"), 0);
            check(invalid.status == 401, "invalid bearer token must be rejected");
            Reply telemetry = request(server.getPort(), "GET", "/v1/telemetry", bearer(TOKEN), 0);
            check(telemetry.status == 200 && telemetry.body.contains("\"fresh\":false"), "authorized telemetry route failed");
            check(telemetry.headers.toLowerCase().contains("cache-control: no-store"), "responses must not be cached");
            Reply status = request(server.getPort(), "GET", "/v1/status", bearer(TOKEN), 0);
            check(status.status == 200 && status.body.contains("service_running"), "authorized status route failed");
            check(request(server.getPort(), "GET", "/v1/unknown", bearer(TOKEN), 0).status == 404, "unknown route must be rejected");
            check(request(server.getPort(), "POST", "/v1/status", bearer(TOKEN), 0).status == 405, "non-GET method must be rejected");
            check(request(server.getPort(), "GET", "/v1/status", new String[] {
                "Authorization: Bearer " + TOKEN, "Authorization: Bearer " + TOKEN }, 0).status == 400,
                "duplicate authorization headers must be rejected");
            check(request(server.getPort(), "GET", "/v1/status", bearer(TOKEN), 18000).status == 431,
                "oversized headers must be rejected");
            token.set(null);
            check(request(server.getPort(), "GET", "/v1/status", bearer(TOKEN), 0).status == 503,
                "unconfigured API token must fail closed");
        } finally {
            server.close();
        }
        check(!server.isRunning(), "server did not stop cleanly");
        System.out.println("TelemetryHttpServerHarness: PASS (loopback, auth, routes, limits, shutdown)");
    }
}
