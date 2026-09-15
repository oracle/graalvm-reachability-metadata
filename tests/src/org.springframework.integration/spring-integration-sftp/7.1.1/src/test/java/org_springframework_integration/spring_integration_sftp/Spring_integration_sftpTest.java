/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.remote.AbstractFileInfo;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.sftp.filters.SftpSimplePatternFileListFilter;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizer;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.sftp.inbound.SftpStreamingMessageSource;
import org.springframework.integration.sftp.outbound.SftpMessageHandler;
import org.springframework.integration.sftp.outbound.SftpOutboundGateway;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpFileInfo;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;

@Timeout(value = 55, unit = TimeUnit.SECONDS)
public class Spring_integration_sftpTest {

    private static final String HOST = "127.0.0.1";

    private static final String USERNAME = "spring";

    private static final String PASSWORD = "integration-secret";

    private static final int SFTP_TIMEOUT_MILLIS = 15_000;

    private static final AtomicInteger REMOTE_DIRECTORY_SEQUENCE = new AtomicInteger();

    private static int hostPort;

    private static Process sftpServer;

    private static DefaultSftpSessionFactory sessionFactory;

    @BeforeAll
    static void startSftpServer() throws Exception {
        hostPort = findAvailablePort();
        sftpServer = new ProcessBuilder(
                "docker", "run", "--rm", "-p", HOST + ":" + hostPort + ":22",
                "atmoz/sftp:alpine", USERNAME + ":" + PASSWORD + ":1001::upload")
                .inheritIO()
                .start();
        sessionFactory = newSessionFactory();

        try {
            waitUntilSftpIsReady();
        } catch (Exception exception) {
            stopSftpServer();
            throw exception;
        }
    }

    @AfterAll
    static void stopSftpServer() throws InterruptedException {
        try {
            if (sessionFactory != null) {
                sessionFactory.destroy();
            }
        } finally {
            if (sftpServer != null && sftpServer.isAlive()) {
                sftpServer.destroy();
                if (!sftpServer.waitFor(10, TimeUnit.SECONDS)) {
                    sftpServer.destroyForcibly();
                    sftpServer.waitFor(10, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Test
    void sessionFactoryPerformsCompleteRemoteFileLifecycle() throws IOException {
        String remoteDirectory = nextRemoteDirectory("session");
        String originalFile = remoteDirectory + "/report.txt";
        String renamedFile = remoteDirectory + "/report-final.txt";

        try (Session<DirEntry> session = sessionFactory.getSession()) {
            assertThat(session.isOpen()).isTrue();
            assertThat(session.test()).isTrue();
            assertThat(session.getHostPort()).isEqualTo(HOST + ":" + hostPort);
            assertThat(session.mkdir(remoteDirectory)).isTrue();

            session.write(stream("first"), originalFile);
            session.append(stream("-second"), originalFile);

            assertThat(session.exists(originalFile)).isTrue();
            assertThat(session.listNames(remoteDirectory)).contains("report.txt");
            DirEntry[] matches = session.list(remoteDirectory + "/*.txt");
            assertThat(matches).hasSize(1);
            SftpFileInfo fileInfo = new SftpFileInfo(matches[0]);
            assertThat(fileInfo.getFilename()).isEqualTo("report.txt");
            assertThat(fileInfo.getSize()).isEqualTo(12L);
            assertThat(fileInfo.isDirectory()).isFalse();
            assertThat(fileInfo.isLink()).isFalse();
            assertThat(fileInfo.getPermissions()).hasSize(9);

            ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
            session.read(originalFile, downloaded);
            assertThat(downloaded.toString(StandardCharsets.UTF_8)).isEqualTo("first-second");

            session.rename(originalFile, renamedFile);
            assertThat(session.exists(originalFile)).isFalse();
            assertThat(session.exists(renamedFile)).isTrue();
            assertThat(session.remove(renamedFile)).isTrue();
            assertThat(session.remove(renamedFile)).isFalse();
            assertThat(session.rmdir(remoteDirectory)).isTrue();
        }
    }

    @Test
    void outboundAdapterUploadsMessagePayloadAndAppliesPermissions() {
        String remoteDirectory = nextRemoteDirectory("outbound-adapter");
        String remoteFile = remoteDirectory + "/orders.txt";
        SftpMessageHandler handler = new SftpMessageHandler(sessionFactory);
        handler.setRemoteDirectoryExpressionString("'" + remoteDirectory + "'");
        handler.setAutoCreateDirectory(true);
        handler.setChmod(0640);
        handler.setBeanFactory(integrationBeanFactory());
        handler.setBeanName("sftpOutboundAdapter");
        handler.afterPropertiesSet();

        Message<byte[]> message = MessageBuilder.withPayload("order-42".getBytes(StandardCharsets.UTF_8))
                .setHeader(FileHeaders.FILENAME, "orders.txt")
                .build();
        handler.handleMessage(message);

        SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(sessionFactory);
        ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
        assertThat(template.get(remoteFile, inputStream -> inputStream.transferTo(downloaded))).isTrue();
        assertThat(downloaded.toString(StandardCharsets.UTF_8)).isEqualTo("order-42");
        DirEntry uploaded = Arrays.stream(template.list(remoteDirectory))
                .filter(entry -> entry.getFilename().equals("orders.txt"))
                .findFirst()
                .orElseThrow();
        assertThat(new SftpFileInfo(uploaded).getPermissions()).isEqualTo("rw-r-----");
        assertThat(template.exists(remoteFile + ".writing")).isFalse();
    }

    @Test
    void outboundGatewayListsAndDownloadsRemoteFiles(@TempDir Path localDirectory) throws IOException {
        String remoteDirectory = nextRemoteDirectory("outbound-gateway");
        upload(remoteDirectory, "gateway.txt", "gateway-content");

        QueueChannel listReplies = new QueueChannel(1);
        SftpOutboundGateway listGateway = new SftpOutboundGateway(
                sessionFactory, "ls", "'" + remoteDirectory + "'");
        initializeGateway(listGateway, "sftpListGateway", listReplies);
        listGateway.handleMessage(new GenericMessage<>("list"));

        Message<?> listReply = listReplies.receive();
        assertThat(listReply).isNotNull();
        List<?> listedFiles = (List<?>) listReply.getPayload();
        assertThat(listedFiles).hasSize(1);
        AbstractFileInfo<?> listedFile = (AbstractFileInfo<?>) listedFiles.get(0);
        assertThat(listedFile.getFilename()).isEqualTo("gateway.txt");
        assertThat(listedFile.getSize()).isEqualTo("gateway-content".length());
        assertThat(listReply.getHeaders())
                .containsEntry(FileHeaders.REMOTE_DIRECTORY, remoteDirectory + "/")
                .containsEntry(FileHeaders.REMOTE_HOST_PORT, HOST + ":" + hostPort);

        QueueChannel getReplies = new QueueChannel(1);
        SftpOutboundGateway getGateway = new SftpOutboundGateway(sessionFactory, "get", "payload");
        getGateway.setLocalDirectory(localDirectory.toFile());
        initializeGateway(getGateway, "sftpGetGateway", getReplies);
        getGateway.handleMessage(new GenericMessage<>(remoteDirectory + "/gateway.txt"));

        Message<?> getReply = getReplies.receive();
        assertThat(getReply).isNotNull();
        File downloaded = (File) getReply.getPayload();
        assertThat(downloaded.toPath()).hasContent("gateway-content");
        assertThat(getReply.getHeaders())
                .containsEntry(FileHeaders.REMOTE_DIRECTORY, remoteDirectory + "/")
                .containsEntry(FileHeaders.REMOTE_FILE, "gateway.txt")
                .containsEntry(FileHeaders.REMOTE_HOST_PORT, HOST + ":" + hostPort);
    }

    @Test
    void inboundAdapterSynchronizesFilteredRemoteFileAndPublishesHeaders(@TempDir Path localDirectory)
            throws IOException {

        String remoteDirectory = nextRemoteDirectory("inbound-adapter");
        upload(remoteDirectory, "incoming.txt", "inbound-content");
        upload(remoteDirectory, "ignored.log", "ignored-content");

        DefaultListableBeanFactory beanFactory = integrationBeanFactory();
        SftpInboundFileSynchronizer synchronizer = new SftpInboundFileSynchronizer(sessionFactory);
        synchronizer.setRemoteDirectory(remoteDirectory);
        synchronizer.setFilter(new SftpSimplePatternFileListFilter("*.txt"));
        synchronizer.setDeleteRemoteFiles(true);
        synchronizer.setBeanFactory(beanFactory);
        synchronizer.setBeanName("sftpInboundSynchronizer");

        SftpInboundFileSynchronizingMessageSource source =
                new SftpInboundFileSynchronizingMessageSource(synchronizer);
        source.setLocalDirectory(localDirectory.toFile());
        source.setBeanFactory(beanFactory);
        source.setBeanName("sftpInboundSource");
        source.afterPropertiesSet();
        source.start();
        try {
            Message<File> received = source.receive();

            assertThat(received).isNotNull();
            assertThat(received.getPayload().toPath()).hasContent("inbound-content");
            assertThat(received.getHeaders())
                    .containsEntry(FileHeaders.REMOTE_DIRECTORY, remoteDirectory)
                    .containsEntry(FileHeaders.REMOTE_FILE, "incoming.txt")
                    .containsEntry(FileHeaders.REMOTE_HOST_PORT, HOST + ":" + hostPort);
            SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(sessionFactory);
            assertThat(template.exists(remoteDirectory + "/incoming.txt")).isFalse();
            assertThat(template.exists(remoteDirectory + "/ignored.log")).isTrue();
        } finally {
            source.stop();
        }
    }

    @Test
    void streamingInboundAdapterReturnsRemoteStreamAndFileInfo() throws Exception {
        String remoteDirectory = nextRemoteDirectory("streaming-adapter");
        upload(remoteDirectory, "stream.txt", "streamed-content");
        upload(remoteDirectory, "ignored.bin", "ignored-content");

        SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(sessionFactory);
        SftpStreamingMessageSource source = new SftpStreamingMessageSource(template);
        source.setRemoteDirectory(remoteDirectory);
        source.setFilter(new SftpSimplePatternFileListFilter("*.txt"));
        source.setBeanFactory(integrationBeanFactory());
        source.setBeanName("sftpStreamingSource");
        source.afterPropertiesSet();
        source.start();
        try {
            Message<InputStream> received = source.receive();

            assertThat(received).isNotNull();
            Closeable session = (Closeable) received.getHeaders()
                    .get(IntegrationMessageHeaderAccessor.CLOSEABLE_RESOURCE);
            assertThat(session).isNotNull();
            try (InputStream inputStream = received.getPayload()) {
                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo("streamed-content");
            } finally {
                session.close();
            }
            assertThat(received.getHeaders())
                    .containsEntry(FileHeaders.REMOTE_DIRECTORY, remoteDirectory)
                    .containsEntry(FileHeaders.REMOTE_FILE, "stream.txt")
                    .containsEntry(FileHeaders.REMOTE_HOST_PORT, HOST + ":" + hostPort);
            assertThat((String) received.getHeaders().get(FileHeaders.REMOTE_FILE_INFO))
                    .contains("\"filename\":\"stream.txt\"")
                    .contains("\"directory\":false");
        } finally {
            source.stop();
        }
    }

    @Test
    void xmlNamespaceCreatesWorkingOutboundSftpFlow() throws IOException {
        String remoteDirectory = nextRemoteDirectory("xml-flow");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans:beans xmlns:beans="http://www.springframework.org/schema/beans"
                             xmlns:int="http://www.springframework.org/schema/integration"
                             xmlns:int-sftp="http://www.springframework.org/schema/integration/sftp"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="
                                 http://www.springframework.org/schema/beans
                                 https://www.springframework.org/schema/beans/spring-beans.xsd
                                 http://www.springframework.org/schema/integration
                                 https://www.springframework.org/schema/integration/spring-integration.xsd
                                 http://www.springframework.org/schema/integration/sftp
                                 https://www.springframework.org/schema/integration/sftp/spring-integration-sftp.xsd">
                    <beans:bean id="xmlSessionFactory"
                                class="org.springframework.integration.sftp.session.DefaultSftpSessionFactory">
                        <beans:property name="host" value="%s"/>
                        <beans:property name="port" value="%d"/>
                        <beans:property name="user" value="%s"/>
                        <beans:property name="password" value="%s"/>
                        <beans:property name="allowUnknownKeys" value="true"/>
                        <beans:property name="timeout" value="%d"/>
                    </beans:bean>
                    <int:channel id="toSftp"/>
                    <int-sftp:outbound-channel-adapter id="xmlSftpOutboundAdapter"
                                                       channel="toSftp"
                                                       session-factory="xmlSessionFactory"
                                                       remote-directory-expression="'%s'"
                                                       auto-create-directory="true"/>
                </beans:beans>
                """.formatted(
                HOST, hostPort, USERNAME, PASSWORD, SFTP_TIMEOUT_MILLIS, remoteDirectory);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);
            reader.loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
            context.refresh();

            MessageChannel channel = context.getBean("toSftp", MessageChannel.class);
            Message<String> message = MessageBuilder.withPayload("configured-content")
                    .setHeader(FileHeaders.FILENAME, "configured.txt")
                    .build();
            assertThat(channel.send(message, 10_000L)).isTrue();
        }

        SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(sessionFactory);
        ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
        assertThat(template.get(remoteDirectory + "/configured.txt",
                inputStream -> inputStream.transferTo(downloaded))).isTrue();
        assertThat(downloaded.toString(StandardCharsets.UTF_8)).isEqualTo("configured-content");
    }

    private static DefaultSftpSessionFactory newSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
        factory.setHost(HOST);
        factory.setPort(hostPort);
        factory.setUser(USERNAME);
        factory.setPassword(PASSWORD);
        factory.setAllowUnknownKeys(true);
        factory.setTimeout(SFTP_TIMEOUT_MILLIS);
        return factory;
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitUntilSftpIsReady() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
        RuntimeException lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (!sftpServer.isAlive()) {
                throw new IllegalStateException("SFTP container exited with code " + sftpServer.exitValue());
            }
            try (Session<DirEntry> session = sessionFactory.getSession()) {
                if (session.test()) {
                    return;
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
            Thread.sleep(250L);
        }
        throw new IllegalStateException("SFTP server did not become ready on " + HOST + ":" + hostPort, lastFailure);
    }

    private static String nextRemoteDirectory(String purpose) {
        return "/upload/" + purpose + "-" + REMOTE_DIRECTORY_SEQUENCE.incrementAndGet();
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static void upload(String remoteDirectory, String fileName, String content) {
        SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(sessionFactory);
        template.execute(session -> {
            if (!session.exists(remoteDirectory)) {
                session.mkdir(remoteDirectory);
            }
            session.write(stream(content), remoteDirectory + "/" + fileName);
            return null;
        });
    }

    private static void initializeGateway(SftpOutboundGateway gateway, String beanName, QueueChannel outputChannel) {
        gateway.setOutputChannel(outputChannel);
        gateway.setBeanFactory(integrationBeanFactory());
        gateway.setBeanName(beanName);
        gateway.afterPropertiesSet();
    }

    private static DefaultListableBeanFactory integrationBeanFactory() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("integrationEvaluationContext", new StandardEvaluationContext());
        return beanFactory;
    }
}
