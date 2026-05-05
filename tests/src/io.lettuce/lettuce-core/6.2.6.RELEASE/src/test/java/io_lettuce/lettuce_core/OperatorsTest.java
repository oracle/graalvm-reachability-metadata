/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Hooks;

public class OperatorsTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final String HOOK_KEY = "lettuce-operators-test";

    @Test
    void globalOperatorErrorHookIsReadWhenRedisPublisherQueueRejectsBufferedData() throws Exception {
        AtomicInteger hookCalls = new AtomicInteger();
        AtomicReference<Object> hookData = new AtomicReference<>();
        CountDownLatch hookInvoked = new CountDownLatch(1);
        Hooks.onOperatorError(HOOK_KEY, (throwable, data) -> {
            hookCalls.incrementAndGet();
            hookData.set(data);
            hookInvoked.countDown();
            return new IllegalStateException("mapped overflow for " + data, throwable);
        });
        Hooks.addQueueWrapper(HOOK_KEY, rejectLettuceOperatorQueues());

        try (FakeRedisServer server = new FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisReactiveCommands<String, String> reactive = connection.reactive();
                CapturingSubscriber<String> subscriber = new CapturingSubscriber<>();

                reactive.lrange("letters", 0, -1).subscribe(subscriber);

                assertThat(hookInvoked.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(subscriber.values()).containsExactly("one");
                assertThat(subscriber.error()).isNull();
                assertThat(subscriber.completed()).isFalse();
                assertThat(hookData).hasValue("two");
                assertThat(hookCalls).hasValue(1);
                assertThat(server.commands()).extracting(command -> command.get(0)).contains("LRANGE");
            } finally {
                if (connection != null) {
                    connection.close();
                }
                client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            }
        } finally {
            Hooks.removeQueueWrapper(HOOK_KEY);
            Hooks.resetOnOperatorError(HOOK_KEY);
        }
    }

    private static Function<Queue<?>, Queue<?>> rejectLettuceOperatorQueues() {
        return queue -> isLettuceOperatorQueueCreation() ? new RejectingQueue<>(queue) : queue;
    }

    private static boolean isLettuceOperatorQueueCreation() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            boolean operatorsClass = "io.lettuce.core.Operators".equals(element.getClassName());
            boolean newQueueMethod = "newQueue".equals(element.getMethodName());
            if (operatorsClass && newQueueMethod) {
                return true;
            }
        }
        return false;
    }

    private static final class RejectingQueue<E> extends AbstractQueue<E> {
        private final Queue<E> delegate;

        private RejectingQueue(Queue<E> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Iterator<E> iterator() {
            return delegate.iterator();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean offer(E e) {
            return false;
        }

        @Override
        public E poll() {
            return delegate.poll();
        }

        @Override
        public E peek() {
            return delegate.peek();
        }
    }

    private static final class CapturingSubscriber<T> implements Subscriber<T> {
        private final List<T> values = new CopyOnWriteArrayList<>();
        private volatile Throwable error;
        private volatile boolean completed;

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        private List<T> values() {
            return values;
        }

        private Throwable error() {
            return error;
        }

        private boolean completed() {
            return completed;
        }
    }

    private static final class FakeRedisServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private volatile boolean closed;

        private FakeRedisServer() throws Exception {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(500);
            thread = new Thread(this::acceptConnections, "operators-test-redis-server");
            thread.setDaemon(true);
            thread.start();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        }

        private RedisURI redisUri() {
            return RedisURI.Builder.redis(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort())
                    .withTimeout(COMMAND_TIMEOUT)
                    .build();
        }

        private List<List<String>> commands() {
            return commands;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void acceptConnections() {
            started.countDown();
            while (!closed) {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout(5_000);
                    handle(socket);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (!closed) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            while (!closed && !socket.isClosed()) {
                List<String> command;
                try {
                    command = readCommand(input);
                } catch (EOFException e) {
                    return;
                } catch (SocketTimeoutException e) {
                    return;
                }
                commands.add(command);
                writeResponse(output, command);
                output.flush();
            }
        }

        private List<String> readCommand(BufferedInputStream input) throws IOException {
            String firstLine = readLine(input);
            if (!firstLine.startsWith("*")) {
                throw new IOException("Expected RESP array but got: " + firstLine);
            }
            int count = Integer.parseInt(firstLine.substring(1));
            List<String> command = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String bulkHeader = readLine(input);
                if (!bulkHeader.startsWith("$")) {
                    throw new IOException("Expected RESP bulk string but got: " + bulkHeader);
                }
                int length = Integer.parseInt(bulkHeader.substring(1));
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length || input.read() != '\r' || input.read() != '\n') {
                    throw new EOFException();
                }
                String value = new String(bytes, StandardCharsets.UTF_8);
                command.add(i == 0 ? value.toUpperCase() : value);
            }
            return command;
        }

        private String readLine(BufferedInputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                int next = input.read();
                if (next == -1) {
                    throw new EOFException();
                }
                if (next == '\r') {
                    int lineFeed = input.read();
                    if (lineFeed != '\n') {
                        throw new IOException("Expected LF after CR");
                    }
                    return bytes.toString(StandardCharsets.UTF_8);
                }
                bytes.write(next);
            }
        }

        private void writeResponse(OutputStream output, List<String> command) throws IOException {
            switch (command.get(0)) {
                case "HELLO":
                    writeHelloResponse(output);
                    break;
                case "PING":
                    writeSimple(output, "PONG");
                    break;
                case "AUTH":
                case "CLIENT":
                case "SELECT":
                case "QUIT":
                    writeSimple(output, "OK");
                    break;
                case "LRANGE":
                    writeArray(output, List.of("one", "two"));
                    break;
                default:
                    writeSimple(output, "OK");
                    break;
            }
        }

        private void writeHelloResponse(OutputStream output) throws IOException {
            output.write("%6\r\n".getBytes(StandardCharsets.UTF_8));
            writeBulk(output, "server");
            writeBulk(output, "redis");
            writeBulk(output, "version");
            writeBulk(output, "7.0.0");
            writeBulk(output, "proto");
            writeInteger(output, 3);
            writeBulk(output, "id");
            writeInteger(output, 1);
            writeBulk(output, "mode");
            writeBulk(output, "standalone");
            writeBulk(output, "role");
            writeBulk(output, "master");
        }

        private void writeSimple(OutputStream output, String value) throws IOException {
            output.write(("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeInteger(OutputStream output, long value) throws IOException {
            output.write((":" + Long.toString(value) + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeBulk(OutputStream output, String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private void writeArray(OutputStream output, List<String> values) throws IOException {
            output.write(("*" + values.size() + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (String value : values) {
                writeBulk(output, value);
            }
        }
    }
}
