/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_loki4j.loki_logback_appender;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/// A local stand-in for the Loki push endpoint that records every request it receives.
public final class LokiPushServer implements AutoCloseable {

    public static final String PUSH_PATH = "/loki/api/v1/push";

    private final HttpServer server;
    private final BlockingQueue<PushRequest> requests = new LinkedBlockingQueue<>();
    private final Deque<Integer> plannedStatuses = new ArrayDeque<>();

    private LokiPushServer(HttpServer server) {
        this.server = server;
    }

    /// Starts a server that answers `204 No Content` unless other statuses are planned.
    public static LokiPushServer start() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        LokiPushServer pushServer = new LokiPushServer(httpServer);
        httpServer.createContext(PUSH_PATH, pushServer::handle);
        httpServer.start();
        return pushServer;
    }

    /// Makes the next requests receive the given statuses, in order, before falling back to 204.
    public synchronized void planStatuses(Integer... statuses) {
        plannedStatuses.addAll(List.of(statuses));
    }

    public String pushUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + PUSH_PATH;
    }

    /// Waits up to 30 seconds for the next recorded request.
    public PushRequest takeRequest() throws InterruptedException {
        PushRequest request = requests.poll(30, TimeUnit.SECONDS);
        if (request == null) {
            throw new AssertionError("Loki push request was not received within 30 seconds");
        }
        return request;
    }

    public int receivedRequestCount() {
        return requests.size();
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream input = exchange.getRequestBody()) {
            body = input.readAllBytes();
        }
        int status = nextStatus();
        requests.add(new PushRequest(
                exchange.getRequestMethod(),
                Map.copyOf(exchange.getRequestHeaders()),
                new String(body, StandardCharsets.UTF_8),
                status));
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private synchronized int nextStatus() {
        Integer planned = plannedStatuses.poll();
        return planned == null ? 204 : planned;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /// One request received by the push endpoint.
    public record PushRequest(String method, Map<String, List<String>> headers, String body, int status) {

        public String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }
    }
}
