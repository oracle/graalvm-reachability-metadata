/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.Executions;
import io.lettuce.core.cluster.api.sync.NodeSelection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ClusterFutureSyncInvocationHandlerTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void clusterSynchronousProxySupportsCommandsConnectionsAndSelections() throws Exception {
        try (FakeClusterRedisServer server = new FakeClusterRedisServer()) {
            RedisClusterClient client = RedisClusterClient.create(server.redisUri());
            client.setOptions(ClusterClientOptions.builder()
                    .autoReconnect(false)
                    .build());
            StatefulRedisClusterConnection<String, String> connection = null;
            try {
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
                RedisAdvancedClusterCommands<String, String> commands = connection.sync();

                assertThat(commands.ping()).isEqualTo("PONG");
                assertThat(commands.set("cluster-key", "cluster-value")).isEqualTo("OK");
                assertThat(commands.get("cluster-key")).isEqualTo("cluster-value");
                assertThat(commands.clusterMyId()).isEqualTo(server.nodeId());

                RedisClusterCommands<String, String> nodeCommands = commands.getConnection(server.nodeId());
                assertThat(nodeCommands.ping()).isEqualTo("PONG");
                assertThat(nodeCommands.get("cluster-key")).isEqualTo("cluster-value");

                NodeSelection<String, String> staticSelection = commands.nodes(node -> node.getNodeId().equals(server.nodeId()));
                assertThat(staticSelection.size()).isEqualTo(1);
                assertThat(staticSelection.node(0).getNodeId()).isEqualTo(server.nodeId());
                Executions<String> pings = staticSelection.commands().ping();
                assertThat(pings.nodes()).hasSize(1);
                assertThat(pings).containsExactly("PONG");

                NodeSelection<String, String> dynamicSelection = commands.nodes(node -> true, true);
                assertThat(dynamicSelection.size()).isEqualTo(1);
                NodeSelection<String, String> readSelection = commands.readonly(node -> node.getNodeId().equals(server.nodeId()));
                assertThat(readSelection.size()).isEqualTo(1);

                assertThat(server.commands()).anySatisfy(command -> assertThat(isClusterCommand(command, "NODES")).isTrue());
                assertThat(server.commands()).anySatisfy(command -> assertThat(isClusterCommand(command, "MYID")).isTrue());
            } finally {
                if (connection != null) {
                    connection.close();
                }
                client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            }
        }
    }

    private static boolean isClusterCommand(List<String> command, String subcommand) {
        return command.size() == 2 && command.get(0).equals("CLUSTER") && command.get(1).equalsIgnoreCase(subcommand);
    }

    private static final class FakeClusterRedisServer implements Closeable {
        private static final String NODE_ID = "07c37dfeb2352e0b5f58947f5c2d669acc4d57c2";

        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final CountDownLatch started = new CountDownLatch(1);
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final List<Thread> handlerThreads = new CopyOnWriteArrayList<>();
        private final Map<String, String> strings = Collections.synchronizedMap(new LinkedHashMap<>());
        private volatile boolean closed;

        FakeClusterRedisServer() throws Exception {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(500);
            acceptThread = new Thread(this::acceptConnections, "fake-cluster-redis-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        }

        RedisURI redisUri() {
            return RedisURI.Builder.redis(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort())
                    .withTimeout(COMMAND_TIMEOUT)
                    .build();
        }

        String nodeId() {
            return NODE_ID;
        }

        List<List<String>> commands() {
            return commands;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            for (Socket socket : sockets) {
                socket.close();
            }
            serverSocket.close();
            join(acceptThread);
            for (Thread handlerThread : handlerThreads) {
                join(handlerThread);
            }
        }

        private void acceptConnections() {
            started.countDown();
            while (!closed) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(5_000);
                    sockets.add(socket);
                    Thread handlerThread = new Thread(() -> handleQuietly(socket), "fake-cluster-redis-connection");
                    handlerThread.setDaemon(true);
                    handlerThreads.add(handlerThread);
                    handlerThread.start();
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    if (!closed) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        private void handleQuietly(Socket socket) {
            try (socket) {
                handle(socket);
            } catch (IOException e) {
                if (!closed) {
                    throw new IllegalStateException(e);
                }
            } finally {
                sockets.remove(socket);
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            while (!closed && !socket.isClosed()) {
                List<String> command;
                try {
                    command = readCommand(input);
                } catch (EOFException | SocketTimeoutException e) {
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
            String name = command.get(0);
            switch (name) {
                case "PING":
                    writeSimple(output, "PONG");
                    break;
                case "HELLO":
                    writeHelloResponse(output);
                    break;
                case "CLUSTER":
                    writeClusterResponse(output, command);
                    break;
                case "INFO":
                    writeBulk(output, """
                            redis_version:7.0.0
                            cluster_enabled:1
                            connected_clients:1
                            master_repl_offset:0
                            """);
                    break;
                case "SET":
                    strings.put(command.get(1), command.get(2));
                    writeSimple(output, "OK");
                    break;
                case "GET":
                    writeBulk(output, strings.get(command.get(1)));
                    break;
                case "AUTH":
                case "ASKING":
                case "CLIENT":
                case "READONLY":
                case "READWRITE":
                case "SELECT":
                case "QUIT":
                    writeSimple(output, "OK");
                    break;
                default:
                    writeSimple(output, "OK");
                    break;
            }
        }

        private void writeClusterResponse(OutputStream output, List<String> command) throws IOException {
            String subcommand = command.size() > 1 ? command.get(1).toUpperCase() : "";
            switch (subcommand) {
                case "NODES":
                    writeBulk(output, clusterNodes());
                    break;
                case "MYID":
                    writeBulk(output, NODE_ID);
                    break;
                case "SLOTS":
                    writeClusterSlots(output);
                    break;
                default:
                    writeSimple(output, "OK");
                    break;
            }
        }

        private String clusterNodes() {
            return NODE_ID + " " + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort()
                    + "@" + (serverSocket.getLocalPort() + 10_000)
                    + " myself,master - 0 0 1 connected 0-16383\n";
        }

        private void writeClusterSlots(OutputStream output) throws IOException {
            output.write("*1\r\n".getBytes(StandardCharsets.UTF_8));
            output.write("*3\r\n".getBytes(StandardCharsets.UTF_8));
            writeInteger(output, 0);
            writeInteger(output, 16_383);
            output.write("*3\r\n".getBytes(StandardCharsets.UTF_8));
            writeBulk(output, serverSocket.getInetAddress().getHostAddress());
            writeInteger(output, serverSocket.getLocalPort());
            writeBulk(output, NODE_ID);
        }

        private void writeHelloResponse(OutputStream output) throws IOException {
            output.write("%7\r\n".getBytes(StandardCharsets.UTF_8));
            writeSimple(output, "server");
            writeBulk(output, "redis");
            writeSimple(output, "version");
            writeBulk(output, "7.0.0");
            writeSimple(output, "proto");
            writeInteger(output, 3);
            writeSimple(output, "id");
            writeInteger(output, 1);
            writeSimple(output, "mode");
            writeBulk(output, "cluster");
            writeSimple(output, "role");
            writeBulk(output, "master");
            writeSimple(output, "modules");
            output.write("*0\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private void writeSimple(OutputStream output, String value) throws IOException {
            output.write(("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeInteger(OutputStream output, long value) throws IOException {
            output.write((":" + Long.toString(value) + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeBulk(OutputStream output, String value) throws IOException {
            if (value == null) {
                output.write("$-1\r\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private void join(Thread thread) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
