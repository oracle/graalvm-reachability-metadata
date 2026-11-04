/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede_jsch;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP proxy tunneling lets an application reach an SSH service from a network that only permits
 * outbound connections through a corporate proxy.
 */
public class HttpProxyTunnelTest {
    private static final String HOST = "127.0.0.1";
    private static final String USER = "reporting";
    private static final String PASSWORD = "reporting-secret";
    private static final String IMAGE = "atmoz/sftp:alpine";
    private static final int OPERATION_TIMEOUT_MILLIS = 20_000;

    private static SftpServer server;
    private static HttpConnectProxy proxy;

    @BeforeAll
    static void startServices() throws Exception {
        server = SftpServer.start();
        proxy = HttpConnectProxy.start();
    }

    @AfterAll
    static void stopServices() throws Exception {
        if (proxy != null) {
            proxy.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    @Timeout(180)
    void executesACommandThroughAnHttpConnectProxy() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(USER, HOST, server.port());
        session.setPassword(PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(OPERATION_TIMEOUT_MILLIS);
        session.setProxy(new ProxyHTTP(HOST, proxy.port()));
        session.connect(OPERATION_TIMEOUT_MILLIS);

        ChannelExec command = (ChannelExec) session.openChannel("exec");
        try {
            command.setCommand("printf 'proxy-routed\\n'; exit 0");
            InputStream output = command.getInputStream();
            command.connect(OPERATION_TIMEOUT_MILLIS);

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            output.transferTo(result);
            awaitClosed(command);
            assertEquals("proxy-routed\n", result.toString(StandardCharsets.UTF_8));
            assertEquals(0, command.getExitStatus());
        } finally {
            command.disconnect();
            session.disconnect();
        }
    }

    private static void awaitClosed(ChannelExec command) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!command.isClosed() && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertTrue(command.isClosed(), "SSH command did not close before the deadline");
    }

    /** A small real HTTP CONNECT endpoint used as the application's network proxy. */
    private static final class HttpConnectProxy implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService workers = Executors.newCachedThreadPool();
        private final Thread acceptThread;
        private volatile boolean closed;

        private HttpConnectProxy() throws IOException {
            listener = new ServerSocket(0);
            acceptThread = new Thread(this::acceptConnections, "jsch-http-proxy-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        static HttpConnectProxy start() throws IOException {
            return new HttpConnectProxy();
        }

        int port() {
            return listener.getLocalPort();
        }

        private void acceptConnections() {
            while (!closed) {
                try {
                    Socket client = listener.accept();
                    workers.submit(() -> handle(client));
                } catch (IOException exception) {
                    if (!closed) {
                        throw new IllegalStateException("HTTP proxy accept failed", exception);
                    }
                }
            }
        }

        private void handle(Socket client) {
            try (client) {
                String request = readHeaders(client.getInputStream());
                String[] requestLine = request.substring(0, request.indexOf("\r\n"))
                        .split(" ");
                if (requestLine.length != 3 || !requestLine[0].equals("CONNECT")) {
                    throw new IOException("Expected an HTTP CONNECT request");
                }
                String[] endpoint = requestLine[1].split(":", 2);
                if (endpoint.length != 2) {
                    throw new IOException("CONNECT request omitted the destination port");
                }

                try (Socket target = new Socket()) {
                    target.connect(new InetSocketAddress(endpoint[0], Integer.parseInt(endpoint[1])),
                            OPERATION_TIMEOUT_MILLIS);
                    client.getOutputStream().write(
                            "HTTP/1.1 200 Connection Established\r\n\r\n"
                                    .getBytes(StandardCharsets.US_ASCII));
                    client.getOutputStream().flush();
                    bridge(client, target);
                }
            } catch (IOException exception) {
                if (!closed) {
                    throw new IllegalStateException("HTTP proxy connection failed", exception);
                }
            }
        }

        private static String readHeaders(InputStream input) throws IOException {
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            int previous = -1;
            int current;
            while ((current = input.read()) >= 0) {
                request.write(current);
                if (previous == '\r' && current == '\n' && request.size() >= 4) {
                    byte[] bytes = request.toByteArray();
                    int length = bytes.length;
                    if (bytes[length - 4] == '\r' && bytes[length - 3] == '\n') {
                        return request.toString(StandardCharsets.US_ASCII);
                    }
                }
                previous = current;
                if (request.size() > 16_384) {
                    throw new IOException("HTTP proxy request headers are too large");
                }
            }
            throw new IOException("HTTP proxy closed before the request headers ended");
        }

        private static void bridge(Socket client, Socket target) {
            Thread clientToTarget = new Thread(
                    () -> copy(client, target), "jsch-http-proxy-client-to-target");
            Thread targetToClient = new Thread(
                    () -> copy(target, client), "jsch-http-proxy-target-to-client");
            clientToTarget.start();
            targetToClient.start();
            try {
                clientToTarget.join(TimeUnit.SECONDS.toMillis(30));
                targetToClient.join(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private static void copy(Socket source, Socket destination) {
            try {
                source.getInputStream().transferTo(destination.getOutputStream());
                destination.getOutputStream().flush();
            } catch (IOException ignored) {
                // Closing either side of the tunnel ends the other side as well.
            }
        }

        @Override
        public void close() throws Exception {
            closed = true;
            listener.close();
            acceptThread.join(TimeUnit.SECONDS.toMillis(10));
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS),
                    "HTTP proxy workers did not stop");
        }
    }

    /** Starts the real OpenSSH server used by the application scenario. */
    private static final class SftpServer implements AutoCloseable {
        private final String containerName;
        private final int port;
        private final Process container;

        private SftpServer(String containerName, int port, Process container) {
            this.containerName = containerName;
            this.port = port;
            this.container = container;
        }

        static SftpServer start() throws Exception {
            int port = findAvailablePort();
            String name = "jsch-real-use-" + UUID.randomUUID();
            Process container = new ProcessBuilder(
                    "docker", "run", "--rm", "--name", name,
                    "-p", HOST + ":" + port + ":22",
                    "-e", "SFTP_USERS=" + USER + ":" + PASSWORD + ":::upload", IMAGE,
                    "/bin/sh", "-c", "sed -i -e '/^ForceCommand internal-sftp$/d' "
                            + "-e '/^ChrootDirectory %h$/d' /etc/ssh/sshd_config"
                            + " && exec /usr/sbin/sshd -D -e -o AllowTcpForwarding=yes")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            SftpServer server = new SftpServer(name, port, container);
            try {
                server.awaitSshBanner();
                return server;
            } catch (Exception exception) {
                server.close();
                throw exception;
            }
        }

        int port() {
            return port;
        }

        private void awaitSshBanner() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            IOException lastFailure = null;
            while (System.nanoTime() < deadline) {
                if (!container.isAlive()) {
                    throw new IllegalStateException("SFTP container exited with code "
                            + container.exitValue());
                }
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(HOST, port), 1_000);
                    socket.setSoTimeout(1_000);
                    String banner = readLine(socket);
                    if (banner.startsWith("SSH-2.0-")) {
                        return;
                    }
                } catch (IOException exception) {
                    lastFailure = exception;
                }
            }
            throw new IllegalStateException("SSH server did not become ready on " + HOST + ":" + port,
                    lastFailure);
        }

        private static String readLine(Socket socket) throws IOException {
            StringBuilder line = new StringBuilder();
            int next;
            while (line.length() < 256 && (next = socket.getInputStream().read()) >= 0) {
                line.append((char) next);
                if (next == '\n') {
                    break;
                }
            }
            return line.toString();
        }

        private static int findAvailablePort() throws IOException {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            }
        }

        @Override
        public void close() throws Exception {
            Process remove = new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            assertTrue(remove.waitFor(30, TimeUnit.SECONDS), "docker rm timed out");
            assertEquals(0, remove.exitValue());
            if (!container.waitFor(30, TimeUnit.SECONDS)) {
                container.destroyForcibly();
                assertTrue(container.waitFor(10, TimeUnit.SECONDS), "SFTP container did not stop");
            }
        }
    }
}
