/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/// Loopback ONS wire fixture: acknowledges a subscription and publishes a controlled service event.
final class OnsPeer implements AutoCloseable {
    private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Future<String> subscription;
    private volatile Socket socket;

    OnsPeer() throws IOException {
        server.setSoTimeout(30000);
        subscription = executor.submit(this::subscribe);
    }

    String configuration() {
        return "nodes=127.0.0.1:" + server.getLocalPort();
    }

    private String subscribe() throws IOException {
        socket = server.accept();
        socket.setSoTimeout(30000);
        BufferedReader input = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        Map<String, String> connect = readMessage(input);
        assertThat(connect.get("request")).isEqualTo("POST /connect HTTP/1.1");
        send("status", Map.of("Result", "success", "Version", connect.get("Version"),
                "instanceId", "metadata-ons"));
        Map<String, String> request = readMessage(input);
        assertThat(request.get("request")).isEqualTo("POST /subscribe HTTP/1.1");
        assertThat(request.get("Subscription")).contains("database/event/service");
        String subscriber = request.get("SubscriberID");
        assertThat(subscriber).isNotBlank();
        send("status", Map.of("Result", "success", "SubscriberID", subscriber));
        return subscriber;
    }

    void serviceDown(String service, String database) throws Exception {
        String subscriber = subscription.get(30, TimeUnit.SECONDS);
        send("event", Map.of("SubscriberID", subscriber, "eventType", "database/event/service",
                "service", service, "database", database, "status", "down", "reason", "failure"));
    }

    private static Map<String, String> readMessage(BufferedReader input) throws IOException {
        Map<String, String> message = new HashMap<>();
        String request = input.readLine();
        if (request == null) {
            throw new EOFException("ONS client closed before sending its request");
        }
        message.put("request", request);
        for (String line = input.readLine(); line != null; line = input.readLine()) {
            if (line.isEmpty()) {
                assertThat(message.get("Content-Length")).isEqualTo("0");
                return message;
            }
            int colon = line.indexOf(':');
            assertThat(colon).isPositive();
            message.put(line.substring(0, colon), line.substring(colon + 1).strip());
        }
        throw new EOFException("Incomplete ONS request headers");
    }

    private void send(String verb, Map<String, String> headers) throws IOException {
        StringBuilder message = new StringBuilder("POST /" + verb + " HTTP/1.1\r\n");
        headers.forEach((name, value) -> message.append(name).append(": ").append(value).append("\r\n"));
        message.append("Content-Length: 0\r\n\r\n");
        socket.getOutputStream().write(message.toString().getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    @Override
    public void close() throws Exception {
        try {
            server.close();
            Socket accepted = socket;
            if (accepted != null) {
                accepted.close();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        if (subscription.isDone() && !subscription.isCancelled()) {
            subscription.get(30, TimeUnit.SECONDS);
        }
    }
}
