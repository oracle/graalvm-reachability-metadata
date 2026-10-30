/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SessionTest {
    private static SftpServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = SftpServer.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void connectsWithTheDefaultAlgorithmsUsingPasswordAuthentication() throws Exception {
        // The defaults overlap with the server only on group1, ssh-rsa, 3des-cbc and hmac-md5.
        Session session = server.connect();
        try {
            assertThat(session.isConnected()).isTrue();
            assertThat(session.getServerVersion()).startsWith("SSH-2.0-OpenSSH");
            assertThat(session.getHostKey().getType()).isEqualTo("ssh-rsa");
            assertThat(session.getUserName()).isEqualTo(SftpServer.USER);
            assertRoundTrip(session, "default.bin");
        } finally {
            session.disconnect();
        }
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    void encryptsWithAes128CbcAndHmacSha1() throws Exception {
        assertAlgorithmsCarryData("aes128-cbc", "hmac-sha1");
    }

    @Test
    void encryptsWithAes192CbcAndHmacSha196() throws Exception {
        assertAlgorithmsCarryData("aes192-cbc", "hmac-sha1-96");
    }

    @Test
    void encryptsWithAes256CbcAndHmacMd596() throws Exception {
        assertAlgorithmsCarryData("aes256-cbc", "hmac-md5-96");
    }

    @Test
    void rekeysAnEstablishedSession() throws Exception {
        Session session = server.connect();
        try {
            session.rekey();
            assertRoundTrip(session, "rekeyed.bin");
            assertThat(session.isConnected()).isTrue();
        } finally {
            session.disconnect();
        }
    }

    @Test
    void forwardsALocalPortThroughTheSession() throws Exception {
        Session session = server.connect();
        try {
            int localPort = findAvailablePort();
            session.setPortForwardingL(localPort, "127.0.0.1", 22);
            assertThat(session.getPortForwardingL()).containsExactly(localPort + ":127.0.0.1:22");
            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress(SftpServer.HOST, localPort), SftpServer.TIMEOUT_MILLIS);
                client.setSoTimeout(SftpServer.TIMEOUT_MILLIS);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                // The forwarded connection reaches the SSH daemon inside the container.
                assertThat(reader.readLine()).startsWith("SSH-2.0-OpenSSH");
            }
            session.delPortForwardingL(localPort);
            assertThat(session.getPortForwardingL()).isEmpty();
        } finally {
            session.disconnect();
        }
    }

    private static void assertAlgorithmsCarryData(String cipher, String mac) throws Exception {
        Properties config = new Properties();
        config.setProperty("cipher.c2s", cipher);
        config.setProperty("cipher.s2c", cipher);
        config.setProperty("mac.c2s", mac);
        config.setProperty("mac.s2c", mac);
        Session session = server.connect(config);
        try {
            assertRoundTrip(session, cipher + "-" + mac + ".bin");
        } finally {
            session.disconnect();
        }
    }

    /// Sends a multi-packet payload to the server and back, so both directions are decrypted and verified.
    private static void assertRoundTrip(Session session, String fileName) throws Exception {
        byte[] payload = new byte[96 * 1024];
        new Random(fileName.hashCode()).nextBytes(payload);
        String path = SftpServer.UPLOAD_DIRECTORY + "/" + fileName;
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        try {
            sftp.put(new ByteArrayInputStream(payload), path);
            ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
            sftp.get(path, downloaded);
            assertThat(downloaded.toByteArray()).isEqualTo(payload);
            sftp.rm(path);
        } finally {
            sftp.disconnect();
        }
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
