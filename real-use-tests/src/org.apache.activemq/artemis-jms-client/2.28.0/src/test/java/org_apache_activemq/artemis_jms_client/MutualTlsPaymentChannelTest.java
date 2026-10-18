/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.Queue;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Secures the Artemis Netty transport with mutual TLS: the broker acceptor and the JMS client
 * connection factory are configured through `sslEnabled`, PKCS12 key and trust stores,
 * `needClientAuth` and `verifyHost` URL parameters.
 *
 * A payment gateway issues itself a broker certificate and a terminal client certificate,
 * accepts card authorizations only over TLS from terminals it trusts, and the terminal checks
 * that the broker certificate names the host it dialed. A client that does not trust the
 * broker, and a client that presents no certificate, are both refused a connection.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MutualTlsPaymentChannelTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String PASSWORD = "changeit";

    private Path stores;

    private ActiveMQServer broker;

    private int port;

    private Path brokerKeyStore;

    private Path brokerTrustStore;

    private Path terminalKeyStore;

    private Path terminalTrustStore;

    private Path strangerTrustStore;

    @BeforeAll
    void startBroker(@TempDir Path storeDirectory) throws Exception {
        stores = storeDirectory;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair brokerKeys = generator.generateKeyPair();
        KeyPair terminalKeys = generator.generateKeyPair();
        KeyPair strangerKeys = generator.generateKeyPair();
        X509Certificate brokerCertificate = selfSigned(brokerKeys, "CN=payments-broker", true);
        X509Certificate terminalCertificate = selfSigned(terminalKeys, "CN=terminal-0042", false);
        X509Certificate strangerCertificate = selfSigned(strangerKeys, "CN=payments-broker", true);

        brokerKeyStore = keyStore("broker-keystore.p12", brokerKeys, brokerCertificate);
        brokerTrustStore = trustStore("broker-truststore.p12", terminalCertificate);
        terminalKeyStore = keyStore("terminal-keystore.p12", terminalKeys, terminalCertificate);
        terminalTrustStore = trustStore("terminal-truststore.p12", brokerCertificate);
        strangerTrustStore = trustStore("stranger-truststore.p12", strangerCertificate);

        port = freePort();
        String acceptor = "tcp://127.0.0.1:" + port
                + "?sslEnabled=true"
                + ";keyStorePath=" + brokerKeyStore
                + ";keyStorePassword=" + PASSWORD
                + ";keyStoreType=PKCS12"
                + ";trustStorePath=" + brokerTrustStore
                + ";trustStorePassword=" + PASSWORD
                + ";trustStoreType=PKCS12"
                + ";needClientAuth=true"
                + ";enabledProtocols=TLSv1.2,TLSv1.3";
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("tls", acceptor);
        broker = ActiveMQServers.newActiveMQServer(configuration);
        broker.start();
        assertThat(broker.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void authorizesPaymentsOnlyOverMutuallyAuthenticatedTls() throws Exception {
        try (ActiveMQConnectionFactory terminal = new ActiveMQConnectionFactory(terminalUrl(terminalTrustStore))) {
            assertThat(terminal.getStaticConnectors()).hasSize(1);
            assertThat(terminal.getStaticConnectors()[0].getParams())
                    .containsEntry("sslEnabled", "true")
                    .containsEntry("verifyHost", "true")
                    .containsEntry("keyStoreType", "PKCS12");

            try (JMSContext context = terminal.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
                Queue authorizations = context.createQueue("payments.authorizations");
                try (JMSConsumer gateway = context.createConsumer(authorizations)) {
                    context.createProducer()
                            .setProperty("amountCents", 12_999L)
                            .setProperty("currency", "EUR")
                            .send(authorizations, "4111-XXXX-XXXX-1111");
                    context.createProducer()
                            .setProperty("amountCents", 450L)
                            .setProperty("currency", "CHF")
                            .send(authorizations, "5500-XXXX-XXXX-0004");

                    Message first = gateway.receive(10_000);
                    assertThat(first.getBody(String.class)).isEqualTo("4111-XXXX-XXXX-1111");
                    assertThat(first.getLongProperty("amountCents")).isEqualTo(12_999L);
                    assertThat(first.getStringProperty("currency")).isEqualTo("EUR");
                    Message second = gateway.receive(10_000);
                    assertThat(second.getBody(String.class)).isEqualTo("5500-XXXX-XXXX-0004");
                    assertThat(second.getLongProperty("amountCents")).isEqualTo(450L);
                    assertThat(gateway.receiveNoWait()).isNull();
                }
            }
        }

        try (ActiveMQConnectionFactory impostorBroker = new ActiveMQConnectionFactory(terminalUrl(strangerTrustStore))) {
            assertThatThrownBy(impostorBroker::createContext).isInstanceOf(JMSRuntimeException.class);
        }

        String anonymousUrl = "tcp://127.0.0.1:" + port
                + "?sslEnabled=true"
                + "&trustStorePath=" + terminalTrustStore
                + "&trustStorePassword=" + PASSWORD
                + "&trustStoreType=PKCS12"
                + "&callTimeout=10000";
        try (ActiveMQConnectionFactory anonymous = new ActiveMQConnectionFactory(anonymousUrl)) {
            assertThatThrownBy(anonymous::createContext).isInstanceOf(JMSRuntimeException.class);
        }
    }

    private String terminalUrl(Path trustStore) {
        return "tcp://127.0.0.1:" + port
                + "?sslEnabled=true"
                + "&keyStorePath=" + terminalKeyStore
                + "&keyStorePassword=" + PASSWORD
                + "&keyStoreType=PKCS12"
                + "&trustStorePath=" + trustStore
                + "&trustStorePassword=" + PASSWORD
                + "&trustStoreType=PKCS12"
                + "&verifyHost=true"
                + "&callTimeout=10000";
    }

    /** Issues a one-day self-signed certificate; the broker one names `127.0.0.1` for `verifyHost`. */
    private static X509Certificate selfSigned(KeyPair keys, String subject, boolean loopbackHost)
            throws Exception {
        Instant now = Instant.now();
        X500Name name = new X500Name(subject);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(Duration.ofHours(1))),
                Date.from(now.plus(Duration.ofDays(1))),
                name,
                keys.getPublic());
        if (loopbackHost) {
            builder.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
        }
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
    }

    private Path keyStore(String file, KeyPair keys, X509Certificate certificate) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("identity", keys.getPrivate(), PASSWORD.toCharArray(), new Certificate[] {certificate});
        return save(store, file);
    }

    private Path trustStore(String file, X509Certificate certificate) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("trusted", certificate);
        return save(store, file);
    }

    private Path save(KeyStore store, String file) throws Exception {
        Path path = stores.resolve(file);
        try (OutputStream out = Files.newOutputStream(path)) {
            store.store(out, PASSWORD.toCharArray());
        }
        return path;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
