/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultSaslConfig;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.SocketConfigurators;
import com.rabbitmq.client.impl.nio.NioParams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Secures broker connections with TLS: a CA-verified server certificate with
/// hostname verification, mutual TLS authenticated through the EXTERNAL SASL
/// mechanism, TLS over the NIO transport, and refusal of an untrusted broker.
///
/// The scenario is a billing service that only talks to its broker over port
/// 5671. The broker's CA, server certificate and the service's client
/// certificate are issued inside the broker container at startup; the service
/// loads them into a `KeyStore`, builds an `SSLContext`, and audits the peer
/// certificate it negotiated with.
public class TlsConnectionTest {

    private static final String IMAGE = "rabbitmq:4.4";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String CLIENT_NAME = "billing-service";

    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

    /// Issues the certificates, enables the TLS listener and EXTERNAL
    /// mechanism, then hands over to the image's own entrypoint.
    private static final String BROKER_SETUP = """
            set -e
            mkdir -p /tls && cd /tls
            openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=Billing Test CA" \\
                -keyout ca.key -out ca.pem
            openssl req -newkey rsa:2048 -nodes -subj "/CN=broker" -keyout server.key -out server.csr
            printf 'subjectAltName=DNS:localhost,IP:127.0.0.1\\nextendedKeyUsage=serverAuth\\n' > server.ext
            openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial -days 2 \\
                -extfile server.ext -out server.pem
            openssl req -newkey rsa:2048 -nodes -subj "/CN=billing-service" -keyout client.key -out client.csr
            printf 'extendedKeyUsage=clientAuth\\n' > client.ext
            openssl x509 -req -in client.csr -CA ca.pem -CAkey ca.key -CAcreateserial -days 2 \\
                -extfile client.ext -out client.pem
            openssl pkcs12 -export -in client.pem -inkey client.key -name billing-service \\
                -passout pass:changeit -out client.p12
            chown -R rabbitmq /tls
            cat > /etc/rabbitmq/conf.d/30-tls.conf <<EOF
            listeners.ssl.default = 5671
            ssl_options.cacertfile = /tls/ca.pem
            ssl_options.certfile = /tls/server.pem
            ssl_options.keyfile = /tls/server.key
            ssl_options.verify = verify_peer
            ssl_options.fail_if_no_peer_cert = false
            ssl_cert_login_from = common_name
            auth_mechanisms.1 = PLAIN
            auth_mechanisms.2 = EXTERNAL
            EOF
            rabbitmq-plugins enable --offline rabbitmq_auth_mechanism_ssl
            exec docker-entrypoint.sh rabbitmq-server
            """;

    private static String containerId;

    private static int port;

    private static KeyStore trustStore;

    private static KeyStore clientKeys;

    @BeforeAll
    static void startBroker() throws Exception {
        containerId = docker("run", "-d", "--rm", "-p", "127.0.0.1::5671", "--entrypoint", "bash", IMAGE,
                "-c", BROKER_SETUP);
        String mapping = docker("port", containerId, "5671/tcp").lines().findFirst().orElseThrow();
        port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        awaitBroker();

        CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        Certificate authority = certificates.generateCertificate(
                new ByteArrayInputStream(docker("exec", containerId, "cat", "/tls/ca.pem")
                        .getBytes(StandardCharsets.US_ASCII)));
        trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("billing-ca", authority);

        byte[] pkcs12 = Base64.getDecoder().decode(docker("exec", containerId, "base64", "-w0", "/tls/client.p12"));
        clientKeys = KeyStore.getInstance("PKCS12");
        clientKeys.load(new ByteArrayInputStream(pkcs12), KEYSTORE_PASSWORD);

        docker("exec", containerId, "rabbitmqctl", "add_user", CLIENT_NAME, "temporary-password");
        docker("exec", containerId, "rabbitmqctl", "clear_password", CLIENT_NAME);
        docker("exec", containerId, "rabbitmqctl", "set_permissions", "-p", "/", CLIENT_NAME, ".*", ".*", ".*");
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerId != null) {
            docker("stop", containerId);
        }
    }

    @Test
    void verifiedServerCertificateCarriesPasswordLogin() throws Exception {
        AtomicReference<X509Certificate> peer = new AtomicReference<>();
        ConnectionFactory factory = passwordFactory();
        factory.setSocketConfigurator(SocketConfigurators.defaultConfigurator().andThen(socket ->
                ((SSLSocket) socket).addHandshakeCompletedListener(event -> {
                    try {
                        peer.set((X509Certificate) event.getPeerCertificates()[0]);
                    } catch (SSLException e) {
                        throw new IllegalStateException(e);
                    }
                })));
        factory.useSslProtocol(sslContext("TLSv1.3", false));
        factory.enableHostnameVerification();
        assertThat(factory.isSSL()).isTrue();

        try (Connection connection = factory.newConnection("billing-password")) {
            assertThat(connection.getPort()).isEqualTo(port);
            assertThat(roundTrip(connection, "invoice 2024-001 issued")).isEqualTo("invoice 2024-001 issued");

            X509Certificate certificate = peer.get();
            assertThat(certificate).isNotNull();
            assertThat(certificate.getSubjectX500Principal().getName()).isEqualTo("CN=broker");
            assertThat(certificate.getIssuerX500Principal().getName()).isEqualTo("CN=Billing Test CA");

            assertThat(brokerView()).startsWith("guest", "PLAIN", "true", "tlsv1.3");
        }
    }

    @Test
    void clientCertificateAuthenticatesThroughExternalMechanism() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(port);
        factory.setConnectionTimeout(10_000);
        factory.setAutomaticRecoveryEnabled(false);
        factory.useSslProtocol(sslContext("TLSv1.3", true));
        factory.enableHostnameVerification();
        factory.setSaslConfig(DefaultSaslConfig.EXTERNAL);

        try (Connection connection = factory.newConnection("billing-mtls")) {
            assertThat(roundTrip(connection, "payment 88 settled")).isEqualTo("payment 88 settled");
            assertThat(brokerView()).containsExactly(CLIENT_NAME, "EXTERNAL", "true", "tlsv1.3", "CN=billing-service");
        }

        ConnectionFactory withoutCertificate = factory.clone();
        withoutCertificate.useSslProtocol(sslContext("TLSv1.3", false));
        assertThatThrownBy(() -> withoutCertificate.newConnection("billing-anonymous")).isInstanceOf(IOException.class);
    }

    @Test
    void nioTransportStreamsLargeMessagesOverTls() throws Exception {
        ConnectionFactory factory = passwordFactory();
        factory.useNio();
        NioParams nio = new NioParams();
        nio.setReadByteBufferSize(16 * 1024);
        nio.setWriteByteBufferSize(16 * 1024);
        factory.setNioParams(nio);
        // The 5.12 NIO handshake loop predates TLS 1.3 post-handshake messages, so pin TLS 1.2.
        factory.useSslProtocol(sslContext("TLSv1.2", true));
        factory.enableHostnameVerification();

        // The 5.12 NIO reader loses its place when a TLS record arrives split across socket reads,
        // so the statement stays within one 16 KiB record instead of streaming many.
        byte[] statement = new byte[8 * 1024];
        for (int i = 0; i < statement.length; i++) {
            statement[i] = (byte) ('a' + i % 26);
        }
        try (Connection connection = factory.newConnection("billing-nio")) {
            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare().getQueue();
            channel.confirmSelect();
            channel.basicPublish("", queue, MessageProperties.MINIMAL_PERSISTENT_BASIC, statement);
            channel.waitForConfirmsOrDie(10_000);

            GetResponse response = awaitMessage(channel, queue);
            assertThat(response.getBody()).hasSize(statement.length);
            assertThat(Arrays.equals(response.getBody(), statement)).isTrue();
            assertThat(brokerView()).containsExactly("guest", "PLAIN", "true", "tlsv1.2", "CN=billing-service");
        }
    }

    @Test
    void brokerOutsideTheTrustStoreIsRefused() throws Exception {
        ConnectionFactory factory = passwordFactory();
        factory.useSslProtocol(SSLContext.getDefault());
        factory.enableHostnameVerification();

        assertThatThrownBy(() -> factory.newConnection("billing-untrusted"))
                .isInstanceOf(IOException.class)
                .satisfies(error -> assertThat(causedBy(error, SSLHandshakeException.class)).isTrue());
    }

    private static ConnectionFactory passwordFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(port);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setConnectionTimeout(10_000);
        factory.setAutomaticRecoveryEnabled(false);
        return factory;
    }

    private static SSLContext sslContext(String protocol, boolean presentClientCertificate) throws Exception {
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trustStore);
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(clientKeys, KEYSTORE_PASSWORD);
        SSLContext context = SSLContext.getInstance(protocol);
        context.init(presentClientCertificate ? keys.getKeyManagers() : null, trust.getTrustManagers(), null);
        return context;
    }

    private static String roundTrip(Connection connection, String text) throws Exception {
        Channel channel = connection.createChannel();
        String queue = channel.queueDeclare().getQueue();
        channel.basicPublish("", queue, MessageProperties.TEXT_PLAIN, text.getBytes(StandardCharsets.UTF_8));
        String received = new String(awaitMessage(channel, queue).getBody(), StandardCharsets.UTF_8);
        channel.close();
        return received;
    }

    /// Returns the broker's record of the only open connection: user, mechanism,
    /// TLS flag, protocol, and the subject of the certificate the client presented.
    private static List<String> brokerView() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String listing = "";
        while (System.nanoTime() < deadline) {
            listing = docker("exec", containerId, "rabbitmqctl", "-q", "list_connections", "--no-table-headers",
                    "user", "auth_mechanism", "ssl", "ssl_protocol", "peer_cert_subject");
            List<String> rows = listing.lines().filter(line -> !line.isBlank()).toList();
            if (rows.size() == 1) {
                return List.of(rows.get(0).split("\t"));
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Expected exactly one broker connection in\n" + listing);
    }

    private static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static GetResponse awaitMessage(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            GetResponse response = channel.basicGet(queue, true);
            if (response != null) {
                return response;
            }
            Thread.sleep(50);
        }
        throw new TimeoutException("No message arrived on " + queue);
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        ConnectionFactory probe = passwordFactory();
        probe.useSslProtocol();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = probe.newConnection("startup-probe")) {
                if (connection.isOpen()) {
                    return;
                }
            } catch (IOException | TimeoutException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("RabbitMQ did not accept TLS connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }
}
