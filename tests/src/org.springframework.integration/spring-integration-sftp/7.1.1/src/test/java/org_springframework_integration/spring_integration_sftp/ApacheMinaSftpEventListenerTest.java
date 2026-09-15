/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.sftp.server.ApacheMinaSftpEvent;
import org.springframework.integration.sftp.server.ApacheMinaSftpEventListener;
import org.springframework.integration.sftp.server.DirectoryCreatedEvent;
import org.springframework.integration.sftp.server.FileWrittenEvent;
import org.springframework.integration.sftp.server.PathMovedEvent;
import org.springframework.integration.sftp.server.PathRemovedEvent;
import org.springframework.integration.sftp.server.SessionClosedEvent;
import org.springframework.integration.sftp.server.SessionOpenedEvent;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;

@Timeout(value = 55, unit = TimeUnit.SECONDS)
public class ApacheMinaSftpEventListenerTest {

    private static final String USERNAME = "event-user";

    private static final String PASSWORD = "event-password";

    private static final int SFTP_TIMEOUT_MILLIS = 15_000;

    @Test
    void publishesSpringEventsForServerSideSftpActivity(@TempDir Path testDirectory) throws Exception {
        List<ApacheMinaSftpEvent> events = new CopyOnWriteArrayList<>();
        ApacheMinaSftpEventListener eventListener = new ApacheMinaSftpEventListener();
        eventListener.setBeanName("sftpEvents");
        eventListener.setApplicationEventPublisher(event -> events.add((ApacheMinaSftpEvent) event));
        eventListener.afterPropertiesSet();

        SftpSubsystemFactory subsystemFactory = new SftpSubsystemFactory();
        subsystemFactory.addSftpEventListener(eventListener);
        Path remoteRoot = Files.createDirectory(testDirectory.resolve("remote-root"));
        SshServer server = newSftpServer(remoteRoot, subsystemFactory);
        DefaultSftpSessionFactory client = null;
        byte[] payload = "event-content".getBytes(StandardCharsets.UTF_8);

        try {
            server.start();
            client = newClient(server.getPort());
            try (Session<DirEntry> session = client.getSession()) {
                session.mkdir("/events");
                session.write(new ByteArrayInputStream(payload), "/events/original.txt");
                session.rename("/events/original.txt", "/events/renamed.txt");
                session.remove("/events/renamed.txt");
                session.rmdir("/events");
            }
        } finally {
            if (client != null) {
                client.destroy();
            }
            server.stop(true);
        }

        SessionOpenedEvent opened = onlyEvent(events, SessionOpenedEvent.class);
        SessionClosedEvent closed = onlyEvent(events, SessionClosedEvent.class);
        assertThat(opened.getClientVersion()).isGreaterThanOrEqualTo(3);
        assertThat(closed.getSession()).isSameAs(opened.getSession());

        DirectoryCreatedEvent created = onlyEvent(events, DirectoryCreatedEvent.class);
        assertThat(created.getPath().getFileName().toString()).isEqualTo("events");
        assertThat(created.getAttrs()).isEmpty();

        List<FileWrittenEvent> writes = eventsOfType(events, FileWrittenEvent.class);
        assertThat(writes).isNotEmpty();
        assertThat(writes).allSatisfy(write -> {
            assertThat(write.getFile().getFileName().toString()).isEqualTo("original.txt");
            assertThat(write.getRemoteHandle()).isNotBlank();
        });
        assertThat(writes.stream().mapToInt(FileWrittenEvent::getDataLen).sum()).isEqualTo(payload.length);

        PathMovedEvent moved = onlyEvent(events, PathMovedEvent.class);
        assertThat(moved.getSrcPath().getFileName().toString()).isEqualTo("original.txt");
        assertThat(moved.getDstPath().getFileName().toString()).isEqualTo("renamed.txt");

        List<PathRemovedEvent> removals = eventsOfType(events, PathRemovedEvent.class);
        assertThat(removals).anySatisfy(removal -> {
            assertThat(removal.isDirectory()).isFalse();
            assertThat(removal.getPath().getFileName().toString()).isEqualTo("renamed.txt");
        });
        assertThat(removals).anySatisfy(removal -> {
            assertThat(removal.isDirectory()).isTrue();
            assertThat(removal.getPath().getFileName().toString()).isEqualTo("events");
        });
        assertThat(eventListener.toString()).contains("sftpEvents");
    }

    private static SshServer newSftpServer(Path remoteRoot, SftpSubsystemFactory subsystemFactory) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair hostKey = keyPairGenerator.generateKeyPair();

        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
        server.setPasswordAuthenticator(
                (username, password, session) -> USERNAME.equals(username) && PASSWORD.equals(password));
        server.setSubsystemFactories(List.of(subsystemFactory));
        server.setFileSystemFactory(new VirtualFileSystemFactory(remoteRoot));
        return server;
    }

    private static DefaultSftpSessionFactory newClient(int port) {
        DefaultSftpSessionFactory client = new DefaultSftpSessionFactory();
        client.setHost("127.0.0.1");
        client.setPort(port);
        client.setUser(USERNAME);
        client.setPassword(PASSWORD);
        client.setAllowUnknownKeys(true);
        client.setTimeout(SFTP_TIMEOUT_MILLIS);
        return client;
    }

    private static <E extends ApacheMinaSftpEvent> E onlyEvent(
            List<ApacheMinaSftpEvent> events, Class<E> eventType) {
        List<E> matchingEvents = eventsOfType(events, eventType);
        assertThat(matchingEvents).hasSize(1);
        return matchingEvents.get(0);
    }

    private static <E extends ApacheMinaSftpEvent> List<E> eventsOfType(
            List<ApacheMinaSftpEvent> events, Class<E> eventType) {
        return events.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .toList();
    }
}
