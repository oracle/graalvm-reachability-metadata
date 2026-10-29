/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/// An `atmoz/sftp:alpine` container whose OpenSSH also accepts the legacy algorithms jsch speaks.
///
/// jsch 0.1.27 offers only `diffie-hellman-group1-sha1`, `ssh-rsa` with SHA-1 signatures, CBC
/// ciphers and the `hmac-md5`/`hmac-sha1` families, all of which current OpenSSH disables by
/// default. The container appends them to `sshd_config`, allows TCP forwarding, and queues an
/// optional authorized key before handing over to the image's own entrypoint. Every connection
/// arrives from the same container gateway address, so per-source penalties are turned off.
final class SftpServer implements AutoCloseable {
    static final String HOST = "127.0.0.1";
    static final String USER = "jsch";
    static final String PASSWORD = "jsch-secret";
    static final String UPLOAD_DIRECTORY = "/upload";
    static final int TIMEOUT_MILLIS = 20_000;

    private static final String IMAGE = "atmoz/sftp:alpine";
    private static final String SETUP = """
            set -e
            cat >> /etc/ssh/sshd_config <<'EOF'
            PerSourcePenalties no
            KexAlgorithms +diffie-hellman-group1-sha1
            HostKeyAlgorithms +ssh-rsa
            PubkeyAcceptedAlgorithms +ssh-rsa
            Ciphers +3des-cbc,aes128-cbc,aes192-cbc,aes256-cbc
            MACs +hmac-md5,hmac-md5-96,hmac-sha1-96
            EOF
            sed -i 's/^AllowTcpForwarding no$/AllowTcpForwarding yes/' /etc/ssh/sshd_config
            if [ -n "$AUTHORIZED_KEY" ]; then
              mkdir -p /home/jsch/.ssh/keys
              printf '%s\\n' "$AUTHORIZED_KEY" > /home/jsch/.ssh/keys/client.pub
            fi
            exec /entrypoint jsch:jsch-secret:1001::upload
            """;

    private final String name;
    private final int port;
    private final Process container;

    private SftpServer(String name, int port, Process container) {
        this.name = name;
        this.port = port;
        this.container = container;
    }

    /// Starts a server that accepts password authentication only.
    static SftpServer start() throws Exception {
        return start("");
    }

    /// Starts a server that also accepts the given `ssh-rsa ...` public key line for the user.
    static SftpServer start(String authorizedKey) throws Exception {
        int port = findAvailablePort();
        String name = "jsch-sftp-" + UUID.randomUUID();
        Process container = new ProcessBuilder(
                "docker", "run", "--rm", "--name", name, "-p", HOST + ":" + port + ":22",
                "-e", "AUTHORIZED_KEY=" + authorizedKey, "--entrypoint", "/bin/sh",
                IMAGE, "-c", SETUP)
                .inheritIO()
                .start();
        SftpServer server = new SftpServer(name, port, container);
        try {
            server.awaitSshBanner();
        } catch (Exception exception) {
            server.close();
            throw exception;
        }
        return server;
    }

    int port() {
        return port;
    }

    /// Returns a session for the test user that skips host-key checking unless `config` says otherwise.
    Session newSession(JSch jsch, Properties config) throws Exception {
        Session session = jsch.getSession(USER, HOST, port);
        Properties properties = new Properties();
        properties.setProperty("StrictHostKeyChecking", "no");
        properties.putAll(config);
        session.setConfig(properties);
        return session;
    }

    /// Connects with password authentication and the given algorithm overrides.
    Session connect(Properties config) throws Exception {
        Session session = newSession(new JSch(), config);
        session.setPassword(PASSWORD);
        session.connect(TIMEOUT_MILLIS);
        return session;
    }

    Session connect() throws Exception {
        return connect(new Properties());
    }

    /// Returns the Base64 blob of the server's RSA host key, read from inside the container.
    String rsaHostKey() throws Exception {
        Process cat = new ProcessBuilder("docker", "exec", name, "cat", "/etc/ssh/ssh_host_rsa_key.pub")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        String line = new String(cat.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
        if (!cat.waitFor(30, TimeUnit.SECONDS) || cat.exitValue() != 0) {
            throw new IllegalStateException("Cannot read the host key of container " + name);
        }
        return line.split(" ")[1];
    }

    private void awaitSshBanner() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (!container.isAlive()) {
                throw new IllegalStateException("SFTP container exited with code " + container.exitValue());
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, port), TIMEOUT_MILLIS);
                socket.setSoTimeout(TIMEOUT_MILLIS);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String banner = reader.readLine();
                if (banner != null && banner.startsWith("SSH-2.0-")) {
                    return;
                }
            } catch (IOException exception) {
                lastFailure = exception;
            }
            Thread.sleep(250L);
        }
        throw new IllegalStateException("SSH server did not become ready on " + HOST + ":" + port, lastFailure);
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    public void close() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", name)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        remove.waitFor(30, TimeUnit.SECONDS);
        if (!container.waitFor(30, TimeUnit.SECONDS)) {
            container.destroyForcibly();
            container.waitFor(10, TimeUnit.SECONDS);
        }
    }
}
