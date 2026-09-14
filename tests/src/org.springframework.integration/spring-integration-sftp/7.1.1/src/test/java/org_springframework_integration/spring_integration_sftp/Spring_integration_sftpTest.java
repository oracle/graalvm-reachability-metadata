/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway.Option;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizer;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.sftp.outbound.SftpMessageHandler;
import org.springframework.integration.sftp.outbound.SftpOutboundGateway;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
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
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
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
