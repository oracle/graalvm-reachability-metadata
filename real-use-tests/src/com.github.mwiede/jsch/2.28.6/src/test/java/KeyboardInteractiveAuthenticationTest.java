/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede_jsch;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keyboard-interactive authentication lets an application answer an SSH server's login prompts
 * without exposing a password authentication method to the session.
 */
public class KeyboardInteractiveAuthenticationTest {
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
    void answersLoginPromptBeforeRunningACommand() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(USER, HOST, server.port());
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "keyboard-interactive");
        session.setTimeout(OPERATION_TIMEOUT_MILLIS);
        LoginPrompts prompts = new LoginPrompts();
        session.setUserInfo(prompts);
        session.connect(OPERATION_TIMEOUT_MILLIS);

        ChannelExec command = (ChannelExec) session.openChannel("exec");
        try {
            command.setCommand("printf 'keyboard-authenticated\\n'; exit 0");
            InputStream output = command.getInputStream();
            command.connect(OPERATION_TIMEOUT_MILLIS);
            CommandResult result = readResult(command, output);
            assertTrue(prompts.wasAnswered(), "SSH server did not request keyboard-interactive input");
            assertEquals("keyboard-authenticated\n", result.output());
            assertEquals(0, result.exitStatus());
        } finally {
            command.disconnect();
            session.disconnect();
        }
    }

    private static CommandResult readResult(ChannelExec command, InputStream output) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!command.isClosed() && System.nanoTime() < deadline) {
            int available = output.available();
            if (available > 0) {
                bytes.write(output.readNBytes(available));
            } else {
                Thread.yield();
            }
        }
        while (output.available() > 0) {
            bytes.write(output.readNBytes(output.available()));
        }
        if (!command.isClosed()) {
            throw new IllegalStateException("SSH command did not finish before the deadline");
        }
        return new CommandResult(bytes.toString(StandardCharsets.UTF_8), command.getExitStatus());
    }

    private record CommandResult(String output, int exitStatus) {
    }

    /** Supplies the credential in response to the server's real login prompt. */
    private static final class LoginPrompts implements UserInfo, UIKeyboardInteractive {
        private volatile boolean answered;

        boolean wasAnswered() {
            return answered;
        }

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return false;
        }

        @Override
        public void showMessage(String message) {
        }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name, String instruction,
                                                   String[] prompt, boolean[] echo) {
            if (prompt.length != 1 || echo.length != 1 || echo[0]) {
                return null;
            }
            answered = true;
            return new String[]{PASSWORD};
        }
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
                    "/bin/sh", "-c", "sed -i -e '/^ForceCommand internal-sftp$/d' "
                            + "-e '/^ChrootDirectory %h$/d' /etc/ssh/sshd_config"
                            + " && exec /usr/sbin/sshd -D -e -o AllowTcpForwarding=yes -o UsePAM=yes")
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
                    if (readLine(socket).startsWith("SSH-2.0-")) {
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
