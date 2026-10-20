package org.opencharge.teslable;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class TelemetryHttpServerResilienceHarness {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void awaitHeaderRead() throws Exception {
        long until = System.nanoTime() + 1_000_000_000L;
        do {
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                if (!entry.getKey().getName().equals("TeslaBLE-Telemetry-API")) continue;
                for (StackTraceElement frame : entry.getValue()) {
                    if (frame.getClassName().equals(TelemetryHttpServer.class.getName())
                            && frame.getMethodName().equals("readLine")) return;
                }
            }
            Thread.sleep(2);
        } while (System.nanoTime() < until);
        throw new AssertionError("Test setup: server did not start reading the partial request");
    }
    private static void requestMustSucceed(TelemetryHttpServer server, String fixtureBearer) throws Exception {
        try (Socket next = new Socket("127.0.0.1", server.getPort())) {
            next.setSoTimeout(1200);
            String request = "GET /v1/telemetry HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "
                    + fixtureBearer + "\r\n\r\n";
            next.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            try {
                String status = new BufferedReader(new InputStreamReader(next.getInputStream(), StandardCharsets.US_ASCII)).readLine();
                check(status != null && status.startsWith("HTTP/1.1 200 "), "Follow-up authenticated request must receive HTTP 200");
            } catch (SocketTimeoutException e) {
                throw new AssertionError("A reset client must not kill the API worker or stall subsequent authenticated requests", e);
            }
        }
    }
    private static void lifecycleGeneration() throws Exception {
        String fixtureBearer = "fixture-only-" + Long.toUnsignedString(System.nanoTime());
        try (TelemetryHttpServer server = new TelemetryHttpServer(() -> "{}", () -> "{}", () -> fixtureBearer,
                InetAddress.getByName("127.0.0.1"), 0)) {
            server.start();
            java.lang.reflect.Field worker = TelemetryHttpServer.class.getDeclaredField("acceptThread");
            worker.setAccessible(true);
            Thread previousWorker = (Thread) worker.get(server);
            try (Socket previousClient = new Socket("127.0.0.1", server.getPort())) {
                previousClient.getOutputStream().write("GET /v1/status HTTP/1.1\r\nX-Fixture: pending".getBytes(StandardCharsets.US_ASCII));
                awaitHeaderRead();
                server.close();
                server.start();
                previousClient.setSoLinger(true, 0);
            }
            previousWorker.join(900);
            check(!previousWorker.isAlive(), "Old API worker must not consume the new listener after close/start");
            check(server.isRunning(), "Old worker cleanup must not stop the restarted listener");
            requestMustSucceed(server, fixtureBearer);
        }
        System.out.println("PASS close/start isolates listener generations");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("lifecycle")) { lifecycleGeneration(); return; }
        String fixtureBearer = "fixture-only-" + Long.toUnsignedString(System.nanoTime());
        try (TelemetryHttpServer server = new TelemetryHttpServer(() -> "{}", () -> "{}", () -> fixtureBearer,
                InetAddress.getByName("127.0.0.1"), 0)) {
            server.start();
            try (Socket aborted = new Socket("127.0.0.1", server.getPort())) {
                aborted.getOutputStream().write("GET /v1/telemetry HTTP/1.1\r\nX-Fixture: partial".getBytes(StandardCharsets.US_ASCII));
                awaitHeaderRead();
                aborted.setSoLinger(true, 0);
            }
            requestMustSucceed(server, fixtureBearer);
            check(server.isRunning(), "API listener must remain running after a client reset");
        }
        System.out.println("PASS client reset is isolated; next authenticated request succeeds");
    }
}
