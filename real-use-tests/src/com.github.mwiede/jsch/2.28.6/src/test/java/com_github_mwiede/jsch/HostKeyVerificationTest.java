/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
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
 * SSH host-key verification support lets an application learn a server key once and require it
 * on later sessions before exchanging application data.
 */
public class HostKeyVerificationTest {
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
    void reconnectsOnlyToAPreviouslyTrustedServer() throws Exception {
        JSch bootstrap = new JSch();
        Session bootstrapSession = bootstrap.getSession(USER, HOST, server.port());
        bootstrapSession.setPassword(PASSWORD);
        bootstrapSession.setConfig("StrictHostKeyChecking", "no");
        bootstrapSession.connect(OPERATION_TIMEOUT_MILLIS);
        bootstrapSession.disconnect();

        HostKey[] learnedKeys = bootstrap.getHostKeyRepository().getHostKey();
        assertTrue(learnedKeys.length > 0, "the initial session did not learn a host key");

        JSch trusted = new JSch();
        for (HostKey learnedKey : learnedKeys) {
            trusted.getHostKeyRepository().add(learnedKey, null);
        }

        Session trustedSession = trusted.getSession(USER, HOST, server.port());
        trustedSession.setPassword(PASSWORD);
        trustedSession.setConfig("StrictHostKeyChecking", "yes");
        try {
            trustedSession.connect(OPERATION_TIMEOUT_MILLIS);
            assertTrue(trustedSession.isConnected());
            assertTrue(trustedSession.getServerVersion().startsWith("SSH-2.0-"));
        } finally {
            trustedSession.disconnect();
        }
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
