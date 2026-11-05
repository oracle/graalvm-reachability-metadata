/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.OpenSSHConfig;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenSSH configuration support lets an application connect through a named host profile while
 * keeping the endpoint and connection policy in one reusable configuration.
 */
public class OpenSshConfigurationTest {
    private static final String HOST = "127.0.0.1";
    private static final String USER = "reporting";
    private static final String PASSWORD = "reporting-secret";
    private static final String IMAGE = "atmoz/sftp:alpine";
    private static final int OPERATION_TIMEOUT_MILLIS = 20_000;

    private static SftpServer server;

    @BeforeAll
    static void startSftpServer() throws Exception {
        server = SftpServer.start();
    }

    @AfterAll
    static void stopSftpServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @Timeout(180)
    void connectsUsingAnOpenSshHostProfile() throws Exception {
        String configuration = "Host reporting-server\n"
                + "  HostName " + HOST + "\n"
                + "  Port " + server.port() + "\n"
                + "  User " + USER + "\n"
                + "  StrictHostKeyChecking no\n";

        JSch jsch = new JSch();
        jsch.setConfigRepository(OpenSSHConfig.parse(configuration));
        Session session = jsch.getSession("reporting-server");
        session.setPassword(PASSWORD);
        assertEquals(HOST, session.getHost());
        assertEquals(USER, session.getUserName());
        assertEquals(server.port(), session.getPort());

        try {
            session.connect(OPERATION_TIMEOUT_MILLIS);
            assertTrue(session.isConnected());
            assertTrue(session.getServerVersion().startsWith("SSH-2.0-"));
        } finally {
            session.disconnect();
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
                    "-p", HOST + ":" + port + ":22", IMAGE,
                    USER + ":" + PASSWORD + ":::upload")
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
