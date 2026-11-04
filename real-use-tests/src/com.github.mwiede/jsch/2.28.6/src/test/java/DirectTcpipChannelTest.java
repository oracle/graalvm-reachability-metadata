/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede_jsch;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct TCP/IP channels let an application reach a service behind an SSH host without opening a
 * local listener for the forwarded connection.
 */
public class DirectTcpipChannelTest {
    private static final String HOST = "127.0.0.1";
    private static final String USER = "reporting";
    private static final String PASSWORD = "reporting-secret";
    private static final String IMAGE = "atmoz/sftp:alpine";
    private static final int OPERATION_TIMEOUT_MILLIS = 20_000;

    private static SshServer server;

    @BeforeAll
    static void startSshServer() throws Exception {
        server = SshServer.start();
    }

    @AfterAll
    static void stopSshServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @Timeout(180)
    void reachesAServiceThroughADirectTcpIpChannel() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(USER, HOST, server.port());
        session.setPassword(PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(OPERATION_TIMEOUT_MILLIS);
        session.connect(OPERATION_TIMEOUT_MILLIS);

        ChannelDirectTCPIP tunnel = (ChannelDirectTCPIP) session.openChannel("direct-tcpip");
        tunnel.setHost(HOST);
        tunnel.setPort(22);
        tunnel.setOrgIPAddress(HOST);
        tunnel.setOrgPort(0);
        try {
            InputStream remoteService = tunnel.getInputStream();
            tunnel.connect(OPERATION_TIMEOUT_MILLIS);
            String banner = readLine(remoteService);
            assertTrue(banner.startsWith("SSH-2.0-"), "Unexpected service banner: " + banner);
        } finally {
            tunnel.disconnect();
            session.disconnect();
        }
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline && line.length() < 256) {
            if (input.available() == 0) {
                Thread.yield();
                continue;
            }
            int next = input.read();
            if (next < 0) {
                break;
            }
            line.append((char) next);
            if (next == '\n') {
                break;
            }
        }
        if (line.isEmpty()) {
            throw new IOException("Direct TCP/IP service did not send a banner before the deadline");
        }
        return line.toString();
    }

    /** Starts the real OpenSSH server used by the application scenario. */
    private static final class SshServer implements AutoCloseable {
        private final String containerName;
        private final int port;
        private final Process container;

        private SshServer(String containerName, int port, Process container) {
            this.containerName = containerName;
            this.port = port;
            this.container = container;
        }

        static SshServer start() throws Exception {
            int port = findAvailablePort();
            String name = "jsch-real-use-" + UUID.randomUUID();
            Process container = new ProcessBuilder(
                    "docker", "run", "--rm", "--name", name,
                    "-p", HOST + ":" + port + ":22",
                    "-e", "SFTP_USERS=" + USER + ":" + PASSWORD + ":::upload", IMAGE,
                    "/usr/sbin/sshd", "-D", "-e", "-o", "AllowTcpForwarding=yes")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            SshServer server = new SshServer(name, port, container);
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
                    throw new IllegalStateException("SSH container exited with code "
                            + container.exitValue());
                }
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(HOST, port), 1_000);
                    socket.setSoTimeout(1_000);
                    if (readLine(socket.getInputStream()).startsWith("SSH-2.0-")) {
                        return;
                    }
                } catch (IOException exception) {
                    lastFailure = exception;
                }
            }
            throw new IllegalStateException("SSH server did not become ready on " + HOST + ":" + port,
                    lastFailure);
        }

        private static String readLine(InputStream input) throws IOException {
            StringBuilder line = new StringBuilder();
            int next;
            while (line.length() < 256 && (next = input.read()) >= 0) {
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
            if (!remove.waitFor(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("docker rm timed out");
            }
            if (remove.exitValue() != 0) {
                throw new IllegalStateException("docker rm failed with code " + remove.exitValue());
            }
            if (!container.waitFor(30, TimeUnit.SECONDS)) {
                container.destroyForcibly();
                if (!container.waitFor(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("SSH container did not stop");
                }
            }
        }
    }
}
