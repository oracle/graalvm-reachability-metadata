/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SFTP file-transfer support uploads a report, verifies its remote metadata, downloads it for
 * processing, and atomically moves it to an archive after the application has accepted it.
 */
public class SftpDocumentExchangeTest {
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
    void exchangesAndArchivesAReportOverSftp() throws Exception {
        byte[] report = "account,total\nA-1042,125.50\n".getBytes(StandardCharsets.UTF_8);

        JSch jsch = new JSch();
        Session session = jsch.getSession(USER, HOST, server.port());
        session.setPassword(PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(OPERATION_TIMEOUT_MILLIS);
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(OPERATION_TIMEOUT_MILLIS);
        try {
            sftp.mkdir("/upload/archive");
            String pending = "/upload/daily-report.csv";
            String archived = "/upload/archive/daily-report.csv";
            sftp.put(new ByteArrayInputStream(report), pending);

            SftpATTRS uploadedAttributes = sftp.stat(pending);
            assertEquals(report.length, uploadedAttributes.getSize());
            assertEquals(List.of("archive", "daily-report.csv"), fileNames(sftp, "/upload"));

            ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
            sftp.get(pending, downloaded);
            assertArrayEquals(report, downloaded.toByteArray());

            sftp.rename(pending, archived);
            assertEquals(List.of("archive"), fileNames(sftp, "/upload"));
            assertEquals(List.of("daily-report.csv"), fileNames(sftp, "/upload/archive"));
            assertEquals(report.length, sftp.stat(archived).getSize());
        } finally {
            sftp.disconnect();
            session.disconnect();
        }
    }

    private static List<String> fileNames(ChannelSftp sftp, String directory) throws Exception {
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(directory);
        List<String> names = new ArrayList<>();
        for (ChannelSftp.LsEntry entry : entries) {
            if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                names.add(entry.getFilename());
            }
        }
        names.sort(String::compareTo);
        return names;
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
                    StringBuilder banner = new StringBuilder();
                    int next;
                    while (banner.length() < 256 && (next = socket.getInputStream().read()) >= 0) {
                        banner.append((char) next);
                        if (next == '\n') {
                            break;
                        }
                    }
                    if (banner.toString().startsWith("SSH-2.0-")) {
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
