/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway.Option;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizer;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.sftp.inbound.SftpStreamingMessageSource;
import org.springframework.integration.sftp.outbound.SftpMessageHandler;
import org.springframework.integration.sftp.outbound.SftpOutboundGateway;
import org.springframework.integration.sftp.server.ApacheMinaSftpEvent;
import org.springframework.integration.sftp.server.ApacheMinaSftpEventListener;
import org.springframework.integration.sftp.server.DirectoryCreatedEvent;
import org.springframework.integration.sftp.server.FileWrittenEvent;
import org.springframework.integration.sftp.server.PathMovedEvent;
import org.springframework.integration.sftp.server.PathRemovedEvent;
import org.springframework.integration.sftp.server.SessionClosedEvent;
import org.springframework.integration.sftp.server.SessionOpenedEvent;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

public class Spring_integration_sftpTest {

    private static final String USERNAME = "integration-user";

    private static final String PASSWORD = "integration-password";

    private static final int IO_TIMEOUT_MILLIS = 10_000;

    @Test
    @Timeout(59)
    void outboundAdapterUploadsAFile(@TempDir Path testDirectory) throws Exception {
        withSftpServer(testDirectory, (remoteRoot, sessionFactory) -> {
            Path localFile = Files.writeString(testDirectory.resolve("outbound-source.txt"), "outbound payload");
            SftpMessageHandler handler = new SftpMessageHandler(sessionFactory);
            handler.setRemoteDirectoryExpressionString("'/outbound'");
            handler.setAutoCreateDirectory(true);
            handler.setFileNameGenerator(message -> "uploaded.txt");
            initialize(handler, "sftpOutboundAdapter");

            handler.handleMessage(new GenericMessage<>(localFile.toFile()));

            assertThat(Files.readString(remoteRoot.resolve("outbound/uploaded.txt")))
                    .isEqualTo("outbound payload");
            assertThat(handler.isChmodCapable()).isTrue();
        });
    }

    @Test
    @Timeout(59)
    void inboundAdapterDownloadsAFile(@TempDir Path testDirectory) throws Exception {
        withSftpServer(testDirectory, (remoteRoot, sessionFactory) -> {
            Path inboundDirectory = Files.createDirectories(remoteRoot.resolve("inbound"));
            Files.writeString(inboundDirectory.resolve("incoming.txt"), "inbound payload");
            Path localDirectory = testDirectory.resolve("downloads");

            SftpInboundFileSynchronizer synchronizer = new SftpInboundFileSynchronizer(sessionFactory);
            synchronizer.setRemoteDirectory("/inbound");
            synchronizer.setBeanFactory(integrationBeanFactory());
            synchronizer.afterPropertiesSet();

            SftpInboundFileSynchronizingMessageSource source =
                    new SftpInboundFileSynchronizingMessageSource(synchronizer);
            source.setLocalDirectory(localDirectory.toFile());
            source.setAutoCreateLocalDirectory(true);
            source.setBeanName("sftpInboundAdapter");
            source.setBeanFactory(integrationBeanFactory());
            source.afterPropertiesSet();

            Message<File> received = source.receive();
            synchronizer.close();

            assertThat(received).isNotNull();
            assertThat(received.getPayload().getName()).isEqualTo("incoming.txt");
            assertThat(Files.readString(received.getPayload().toPath())).isEqualTo("inbound payload");
            assertThat(Files.readString(inboundDirectory.resolve("incoming.txt"))).isEqualTo("inbound payload");
            assertThat(source.getComponentType()).isEqualTo("sftp:inbound-channel-adapter");
        });
    }

    @Test
    @Timeout(59)
    void streamingInboundAdapterReadsRemoteFileWithoutDownloadingIt(@TempDir Path testDirectory) throws Exception {
        withSftpServer(testDirectory, (remoteRoot, sessionFactory) -> {
            Path streamingDirectory = Files.createDirectories(remoteRoot.resolve("streaming"));
            Files.writeString(streamingDirectory.resolve("streamed.txt"), "streamed payload");

            SftpRemoteFileTemplate remoteFileTemplate = new SftpRemoteFileTemplate(sessionFactory);
            SftpStreamingMessageSource source = new SftpStreamingMessageSource(remoteFileTemplate);
            source.setRemoteDirectory("/streaming");
            source.setBeanName("sftpStreamingInboundAdapter");
            source.setBeanFactory(integrationBeanFactory());
            source.afterPropertiesSet();
            source.start();

            try {
                Message<InputStream> received = source.receive();

                assertThat(received).isNotNull();
                assertThat(received.getHeaders().get(FileHeaders.REMOTE_FILE)).isEqualTo("streamed.txt");
                assertThat(received.getHeaders().get(FileHeaders.REMOTE_DIRECTORY)).isEqualTo("/streaming");
                assertThat(received.getHeaders().get(IntegrationMessageHeaderAccessor.CLOSEABLE_RESOURCE))
                        .isInstanceOf(Closeable.class);
                assertThat(source.getComponentType()).isEqualTo("sftp:inbound-streaming-channel-adapter");

                Closeable session = (Closeable) received.getHeaders()
                        .get(IntegrationMessageHeaderAccessor.CLOSEABLE_RESOURCE);
                try (session; InputStream payload = received.getPayload()) {
                    assertThat(new String(payload.readAllBytes(), StandardCharsets.UTF_8))
                            .isEqualTo("streamed payload");
                }
            } finally {
                source.stop();
            }
        });
    }

    @Test
    @Timeout(59)
    void outboundGatewayListsRemoteFiles(@TempDir Path testDirectory) throws Exception {
        withSftpServer(testDirectory, (remoteRoot, sessionFactory) -> {
            Path gatewayDirectory = Files.createDirectories(remoteRoot.resolve("gateway"));
            Files.writeString(gatewayDirectory.resolve("alpha.txt"), "alpha");
            Files.writeString(gatewayDirectory.resolve("beta.txt"), "beta");

            QueueChannel replies = new QueueChannel(1);
            SftpOutboundGateway gateway = new SftpOutboundGateway(sessionFactory, "ls", "'/gateway'");
            gateway.setOption(Option.NAME_ONLY);
            gateway.setOutputChannel(replies);
            initialize(gateway, "sftpOutboundGateway");

            gateway.handleMessage(new GenericMessage<>("list files"));

            Message<?> reply = replies.receive(IO_TIMEOUT_MILLIS);
            assertThat(reply).isNotNull();
            assertThat(reply.getPayload()).isInstanceOf(List.class);
            assertThat(reply.getPayload()).isEqualTo(List.of("alpha.txt", "beta.txt"));
            assertThat(gateway.getComponentType()).isEqualTo("sftp:outbound-gateway");
        });
    }

    @Test
    @Timeout(59)
    void remoteFileTemplateExecutesMultipleOperationsInOneSession(@TempDir Path testDirectory) throws Exception {
        withSftpServer(testDirectory, (remoteRoot, sessionFactory) -> {
            SftpRemoteFileTemplate remoteFileTemplate = new SftpRemoteFileTemplate(sessionFactory);

            List<String> remoteFiles = remoteFileTemplate.execute(session -> {
                assertThat(session.mkdir("/template")).isTrue();
                session.write(new ByteArrayInputStream("template payload".getBytes(StandardCharsets.UTF_8)),
                        "/template/message.txt");
                assertThat(session.exists("/template/message.txt")).isTrue();
                return Arrays.stream(session.listNames("/template"))
                        .filter(name -> !name.equals(".") && !name.equals(".."))
                        .toList();
            });

            assertThat(remoteFiles).containsExactly("message.txt");
            assertThat(Files.readString(remoteRoot.resolve("template/message.txt")))
                    .isEqualTo("template payload");
        });
    }

    @Test
    @Timeout(59)
    void sftpServerEventsArePublishedAsSpringApplicationEvents(@TempDir Path testDirectory) throws Exception {
        List<ApacheMinaSftpEvent> events = new CopyOnWriteArrayList<>();
        ApacheMinaSftpEventListener eventListener = new ApacheMinaSftpEventListener();
        eventListener.setApplicationEventPublisher(event -> events.add((ApacheMinaSftpEvent) event));
        eventListener.afterPropertiesSet();

        SftpSubsystemFactory subsystemFactory = new SftpSubsystemFactory();
        subsystemFactory.addSftpEventListener(eventListener);
        byte[] payload = "event payload".getBytes(StandardCharsets.UTF_8);

        withSftpServer(testDirectory, subsystemFactory, (remoteRoot, sessionFactory) -> {
            try (Session<SftpClient.DirEntry> session = sessionFactory.getSession()) {
                assertThat(session.mkdir("/events")).isTrue();
                session.write(new ByteArrayInputStream(payload), "/events/original.txt");
                session.rename("/events/original.txt", "/events/renamed.txt");
                assertThat(session.remove("/events/renamed.txt")).isTrue();
                assertThat(session.rmdir("/events")).isTrue();
            }
        });

        SessionOpenedEvent openedEvent = onlyEvent(events, SessionOpenedEvent.class);
        assertThat(openedEvent.getClientVersion()).isGreaterThanOrEqualTo(3);
        DirectoryCreatedEvent createdEvent = onlyEvent(events, DirectoryCreatedEvent.class);
        assertThat(createdEvent.getPath().getFileName().toString()).isEqualTo("events");

        List<FileWrittenEvent> writtenEvents = events.stream()
                .filter(FileWrittenEvent.class::isInstance)
                .map(FileWrittenEvent.class::cast)
                .toList();
        assertThat(writtenEvents).isNotEmpty();
        assertThat(writtenEvents).allSatisfy(event ->
                assertThat(event.getFile().getFileName().toString()).isEqualTo("original.txt"));
        assertThat(writtenEvents.stream().mapToInt(FileWrittenEvent::getDataLen).sum()).isEqualTo(payload.length);

        PathMovedEvent movedEvent = onlyEvent(events, PathMovedEvent.class);
        assertThat(movedEvent.getSrcPath().getFileName().toString()).isEqualTo("original.txt");
        assertThat(movedEvent.getDstPath().getFileName().toString()).isEqualTo("renamed.txt");

        List<PathRemovedEvent> removedEvents = events.stream()
                .filter(PathRemovedEvent.class::isInstance)
                .map(PathRemovedEvent.class::cast)
                .toList();
        PathRemovedEvent removedFile = removedEvents.stream()
                .filter(event -> !event.isDirectory())
                .findFirst()
                .orElseThrow();
        assertThat(removedFile.getPath().getFileName().toString()).isEqualTo("renamed.txt");
        PathRemovedEvent removedDirectory = removedEvents.stream()
                .filter(PathRemovedEvent::isDirectory)
                .findFirst()
                .orElseThrow();
        assertThat(removedDirectory.getPath().getFileName().toString()).isEqualTo("events");

        SessionClosedEvent closedEvent = onlyEvent(events, SessionClosedEvent.class);
        assertThat(closedEvent.getSession()).isSameAs(openedEvent.getSession());
    }

    private static <E extends ApacheMinaSftpEvent> E onlyEvent(
            List<ApacheMinaSftpEvent> events, Class<E> eventType) {
        List<E> matchingEvents = events.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .toList();
        assertThat(matchingEvents).hasSize(1);
        return matchingEvents.get(0);
    }

    private static void initialize(SftpMessageHandler handler, String beanName) {
        handler.setBeanName(beanName);
        handler.setBeanFactory(integrationBeanFactory());
        handler.afterPropertiesSet();
    }

    private static void initialize(SftpOutboundGateway gateway, String beanName) {
        gateway.setBeanName(beanName);
        gateway.setBeanFactory(integrationBeanFactory());
        gateway.afterPropertiesSet();
    }

    private static DefaultListableBeanFactory integrationBeanFactory() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton(
                IntegrationContextUtils.INTEGRATION_EVALUATION_CONTEXT_BEAN_NAME, new StandardEvaluationContext());
        return beanFactory;
    }

    private static void withSftpServer(Path testDirectory, SftpScenario scenario) throws Exception {
        withSftpServer(testDirectory, new SftpSubsystemFactory(), scenario);
    }

    private static void withSftpServer(
            Path testDirectory, SftpSubsystemFactory subsystemFactory, SftpScenario scenario) throws Exception {
        Path remoteRoot = Files.createDirectories(testDirectory.resolve("remote-root"));
        SimpleGeneratorHostKeyProvider hostKeyProvider =
                new SimpleGeneratorHostKeyProvider(testDirectory.resolve("host-key.pem"));
        hostKeyProvider.setAlgorithm(KeyUtils.RSA_ALGORITHM);
        hostKeyProvider.setKeySize(2048);

        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(hostKeyProvider);
        server.setPasswordAuthenticator(
                (username, password, session) -> USERNAME.equals(username) && PASSWORD.equals(password));
        server.setSubsystemFactories(List.of(subsystemFactory));
        server.setFileSystemFactory(new VirtualFileSystemFactory(remoteRoot));

        DefaultSftpSessionFactory sessionFactory = null;
        try {
            server.start();
            sessionFactory = new DefaultSftpSessionFactory();
            sessionFactory.setHost("127.0.0.1");
            sessionFactory.setPort(server.getPort());
            sessionFactory.setUser(USERNAME);
            sessionFactory.setPassword(PASSWORD);
            sessionFactory.setAllowUnknownKeys(true);
            sessionFactory.setTimeout(IO_TIMEOUT_MILLIS);
            scenario.run(remoteRoot, sessionFactory);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.destroy();
            }
            if (server.isStarted()) {
                server.stop(true);
            }
        }
    }

    @FunctionalInterface
    private interface SftpScenario {

        void run(Path remoteRoot, DefaultSftpSessionFactory sessionFactory) throws Exception;
    }
}
