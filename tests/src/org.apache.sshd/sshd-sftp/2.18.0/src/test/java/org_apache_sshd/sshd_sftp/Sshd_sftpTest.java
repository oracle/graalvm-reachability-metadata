/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.fs.SftpFileSystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;

public class Sshd_sftpTest {
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void transfersAndManagesFilesOverSftp() throws Exception {
        Path rootDirectory = Path.of(System.getProperty("java.io.tmpdir"));
        String directory = "sshd-sftp-" + UUID.randomUUID();
        SshServer server = createServer(rootDirectory);
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);

        try {
            server.start();
            client.start();

            try (ClientSession session = client.connect("user", "localhost", server.getPort())
                    .verify(CONNECTION_TIMEOUT)
                    .getSession()) {
                session.addPasswordIdentity("password");
                session.auth().verify(CONNECTION_TIMEOUT);

                try (SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session)) {
                    byte[] contents = "SFTP integration content".getBytes(StandardCharsets.UTF_8);
                    sftpClient.mkdir(directory);
                    String uploadedFile = directory + "/upload.txt";
                    String renamedFile = directory + "/renamed.txt";
                    try (OutputStream output = sftpClient.write(uploadedFile)) {
                        output.write(contents);
                    }

                    assertThat(sftpClient.stat(uploadedFile).getSize()).isEqualTo(contents.length);
                    assertThat(fileNames(sftpClient, directory)).contains("upload.txt");

                    try (InputStream input = sftpClient.read(uploadedFile)) {
                        assertThat(input.readAllBytes()).isEqualTo(contents);
                    }

                    sftpClient.rename(uploadedFile, renamedFile);
                    assertThat(fileNames(sftpClient, directory)).contains("renamed.txt");

                    sftpClient.remove(renamedFile);
                    assertThat(fileNames(sftpClient, directory)).doesNotContain("renamed.txt");
                    sftpClient.rmdir(directory);
                }
            }
        } finally {
            client.stop();
            server.stop(true);
        }
    }

    @Test
    void managesRemoteFilesThroughNioFileSystemProvider() throws Exception {
        Path rootDirectory = Path.of(System.getProperty("java.io.tmpdir"));
        SshServer server = createServer(rootDirectory);
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);

        try {
            server.start();
            client.start();

            try (ClientSession session = client.connect("user", "localhost", server.getPort())
                    .verify(CONNECTION_TIMEOUT)
                    .getSession()) {
                session.addPasswordIdentity("password");
                session.auth().verify(CONNECTION_TIMEOUT);

                try (SftpFileSystem fileSystem = SftpClientFactory.instance().createSftpFileSystem(session)) {
                    Path remoteFile = fileSystem.getPath("nio-" + UUID.randomUUID() + ".txt");
                    String contents = "NIO file system content";
                    Files.writeString(remoteFile, contents, StandardCharsets.UTF_8);

                    assertThat(Files.readString(remoteFile, StandardCharsets.UTF_8)).isEqualTo(contents);
                    BasicFileAttributes attributes = Files.readAttributes(remoteFile, BasicFileAttributes.class);
                    assertThat(attributes.isRegularFile()).isTrue();
                    assertThat(attributes.size()).isEqualTo(contents.getBytes(StandardCharsets.UTF_8).length);

                    Files.delete(remoteFile);
                }
            }
        } finally {
            client.stop();
            server.stop(true);
        }
    }

    private static SshServer createServer(Path rootDirectory) {
        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator(AcceptAllPasswordAuthenticator.INSTANCE);
        server.setFileSystemFactory(new VirtualFileSystemFactory(rootDirectory));
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        return server;
    }

    private static List<String> fileNames(SftpClient sftpClient, String directory) throws IOException {
        List<String> fileNames = new ArrayList<>();
        for (SftpClient.DirEntry entry : sftpClient.readDir(directory)) {
            fileNames.add(entry.getFilename());
        }
        return fileNames;
    }
}
