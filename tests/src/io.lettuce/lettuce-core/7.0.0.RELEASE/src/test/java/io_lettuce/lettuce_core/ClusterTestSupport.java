/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.protocol.ProtocolVersion;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class ClusterTestSupport {
    static final String NODE_ID = "0123456789abcdef0123456789abcdef01234567";

    private ClusterTestSupport() {
    }

    static RedisClusterClient createClient(FakeRedisClusterServer server) {
        RedisClusterClient client = RedisClusterClient.create(server.redisUri());
        client.setOptions(ClusterClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .validateClusterNodeMembership(false)
                .build());
        return client;
    }

    static void shutdown(RedisClusterClient client) {
        client.shutdown(Duration.ZERO, LettuceTestSupport.TIMEOUT);
    }

    static final class FakeRedisClusterServer implements Closeable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final List<Socket> clients = new CopyOnWriteArrayList<>();
        private final List<Thread> clientThreads = new CopyOnWriteArrayList<>();
        private volatile boolean closed;

        FakeRedisClusterServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            acceptThread = new Thread(this::acceptConnections, "fake-redis-cluster-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        RedisURI redisUri() {
            return RedisURI.Builder.redis(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort())
                    .withTimeout(LettuceTestSupport.TIMEOUT)
                    .build();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
            for (Socket client : clients) {
                client.close();
            }
            join(acceptThread);
            for (Thread clientThread : clientThreads) {
                join(clientThread);
            }
        }

        private void acceptConnections() {
            while (!closed) {
                try {
                    Socket client = serverSocket.accept();
                    clients.add(client);
                    Thread thread = new Thread(() -> handle(client), "fake-redis-cluster-client");
                    thread.setDaemon(true);
                    clientThreads.add(thread);
                    thread.start();
                } catch (SocketException e) {
                    if (!closed) {
                        throw new IllegalStateException(e);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        private void handle(Socket socket) {
            try (socket;
                    BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                    BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream())) {
                socket.setSoTimeout((int) LettuceTestSupport.TIMEOUT.toMillis());
                while (!closed) {
                    List<String> command = readCommand(input);
                    writeResponse(output, command);
                    output.flush();
                }
            } catch (IOException e) {
                if (!closed && !(e instanceof EOFException) && !(e instanceof SocketException)) {
                    throw new IllegalStateException(e);
                }
            } finally {
                clients.remove(socket);
            }
        }

        private List<String> readCommand(BufferedInputStream input) throws IOException {
            String firstLine = readLine(input);
            int count = Integer.parseInt(firstLine.substring(1));
            List<String> command = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int length = Integer.parseInt(readLine(input).substring(1));
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
                    if (input.read() != '\n') {
                        throw new IOException("Expected LF after CR");
                    }
                    return bytes.toString(StandardCharsets.UTF_8);
                }
                bytes.write(next);
            }
        }

        private void writeResponse(BufferedOutputStream output, List<String> command) throws IOException {
            String name = command.get(0);
            if (name.equals("CLUSTER") && command.size() > 1 && command.get(1).equalsIgnoreCase("NODES")) {
                writeClusterNodes(output);
            } else if (name.equals("CLUSTER") && command.size() > 1 && command.get(1).equalsIgnoreCase("SLOTS")) {
                writeClusterSlots(output);
            } else if (name.equals("CLUSTER") && command.size() > 1 && command.get(1).equalsIgnoreCase("MYID")) {
                writeBulk(output, NODE_ID);
            } else if (name.equals("INFO")) {
                writeBulk(output, "# Server\r\nredis_version:7.0.0\r\n");
            } else if (name.equals("PING")) {
                writeSimple(output, "PONG");
            } else {
                writeSimple(output, "OK");
            }
        }

        private void writeClusterNodes(BufferedOutputStream output) throws IOException {
            String address = serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort();
            writeBulk(output, NODE_ID + " " + address + "@" + serverSocket.getLocalPort()
                    + " myself,master - 0 0 1 connected 0-16383\n");
        }

        private void writeClusterSlots(BufferedOutputStream output) throws IOException {
            output.write("*1\r\n*3\r\n:0\r\n:16383\r\n*3\r\n".getBytes(StandardCharsets.UTF_8));
            writeBulk(output, serverSocket.getInetAddress().getHostAddress());
            output.write((":" + serverSocket.getLocalPort() + "\r\n").getBytes(StandardCharsets.UTF_8));
            writeBulk(output, NODE_ID);
        }

        private void writeSimple(BufferedOutputStream output, String value) throws IOException {
            output.write(("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private void writeBulk(BufferedOutputStream output, String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        private void join(Thread thread) {
            try {
                thread.join(LettuceTestSupport.TIMEOUT.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
