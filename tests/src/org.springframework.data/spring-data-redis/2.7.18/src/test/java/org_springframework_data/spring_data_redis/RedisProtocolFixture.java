/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/// A small RESP endpoint for exercising real Lettuce connections without an external service.
final class RedisProtocolFixture implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final List<Socket> clients = new CopyOnWriteArrayList<>();
    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private volatile boolean closed;
    final LettuceConnectionFactory factory;

    RedisProtocolFixture() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        workers.submit(this::accept);
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                server.getInetAddress().getHostAddress(), server.getLocalPort());
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder().protocolVersion(ProtocolVersion.RESP2).build())
                .commandTimeout(Duration.ofSeconds(10))
                .shutdownTimeout(Duration.ofSeconds(10))
                .build();
        factory = new LettuceConnectionFactory(configuration, client);
        factory.afterPropertiesSet();
    }

    StringRedisTemplate template() {
        return new StringRedisTemplate(factory);
    }

    private void accept() {
        try {
            while (!closed) {
                Socket socket = server.accept();
                socket.setSoTimeout(30_000);
                clients.add(socket);
                workers.submit(() -> serve(socket));
            }
        } catch (IOException e) {
            if (!closed) {
                failure.compareAndSet(null, e);
            }
        }
    }

    private void serve(Socket socket) {
        try (socket;
                BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream())) {
            List<List<String>> queued = new ArrayList<>();
            boolean transaction = false;
            while (!closed) {
                int first = input.read();
                if (first == -1) {
                    return;
                }
                if (first != '*') {
                    throw new IOException("Expected a RESP array");
                }
                int size = Integer.parseInt(line(input));
                List<String> command = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    if (input.read() != '$') {
                        throw new IOException("Expected a bulk string");
                    }
                    int length = Integer.parseInt(line(input));
                    byte[] bytes = input.readNBytes(length);
                    if (bytes.length != length || input.read() != '\r' || input.read() != '\n') {
                        throw new EOFException();
                    }
                    command.add(new String(bytes, StandardCharsets.UTF_8));
                }
                String name = command.get(0).toUpperCase(Locale.ROOT);
                String response;
                if (name.equals("MULTI")) {
                    transaction = true;
                    response = "+OK\r\n";
                } else if (name.equals("EXEC")) {
                    StringBuilder results = new StringBuilder("*" + queued.size() + "\r\n");
                    for (List<String> pending : queued) {
                        results.append(execute(pending));
                    }
                    queued.clear();
                    transaction = false;
                    response = results.toString();
                } else if (name.equals("DISCARD")) {
                    queued.clear();
                    transaction = false;
                    response = "+OK\r\n";
                } else if (transaction) {
                    queued.add(command);
                    response = "+QUEUED\r\n";
                } else {
                    response = execute(command);
                }
                output.write(response.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        } catch (IOException e) {
            if (!closed) {
                failure.compareAndSet(null, e);
            }
        }
    }

    private String execute(List<String> command) {
        return switch (command.get(0).toUpperCase(Locale.ROOT)) {
            case "PING" -> "+PONG\r\n";
            case "CLIENT", "SELECT", "QUIT" -> "+OK\r\n";
            case "SET" -> {
                values.put(command.get(1), command.get(2));
                yield "+OK\r\n";
            }
            case "GET" -> {
                String value = values.get(command.get(1));
                yield value == null ? "$-1\r\n"
                        : "$" + value.getBytes(StandardCharsets.UTF_8).length + "\r\n" + value + "\r\n";
            }
            default -> "-ERR unsupported command\r\n";
        };
    }

    private static String line(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int next = input.read(); next != -1; next = input.read()) {
            if (next == '\r') {
                if (input.read() != '\n') {
                    throw new IOException("Expected LF");
                }
                return bytes.toString(StandardCharsets.UTF_8);
            }
            bytes.write(next);
        }
        throw new EOFException();
    }

    @Override
    public void close() throws Exception {
        try {
            factory.destroy();
        } finally {
            closed = true;
            server.close();
            for (Socket client : clients) {
                client.close();
            }
            workers.shutdownNow();
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new AssertionError("RESP workers did not stop");
            }
        }
        if (failure.get() != null) {
            throw new AssertionError("RESP endpoint failed", failure.get());
        }
    }
}
