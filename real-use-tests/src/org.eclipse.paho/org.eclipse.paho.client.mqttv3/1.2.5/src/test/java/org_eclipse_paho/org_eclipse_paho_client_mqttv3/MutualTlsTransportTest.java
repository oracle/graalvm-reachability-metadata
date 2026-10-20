/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises the TLS transport: `ssl://` server URIs with mutual certificate authentication,
/// configured both through an application-built `SSLSocketFactory` and through the
/// `com.ibm.ssl.*` SSL properties that point the client at keystore files.
///
/// The scenario is a smart-meter fleet: a private CA signs the broker's certificate and one
/// certificate per client, and Mosquitto maps each certificate's common name to the MQTT
/// identity its ACL checks. A meter connects from PKCS12 files and may only publish under its
/// own topics, a billing backend connects with an in-memory key store and pins the broker
/// through a hostname verifier, and clients without a trusted chain or a certificate are
/// turned away.
public class MutualTlsTransportTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final String BROKER_CONFIG = """
            listener 8883
            cafile /tmp/tls/ca.crt
            certfile /tmp/tls/broker.crt
            keyfile /tmp/tls/broker.key
            require_certificate true
            use_identity_as_username true
            acl_file /tmp/tls/acl
            """;

    private static final String BROKER_ACL = """
            user billing-backend
            topic read meters/+/energy
            topic write meters/+/commands

            pattern readwrite meters/%u/#
            """;

    private static final String BROKER_SCRIPT = """
            mkdir -p /tmp/tls \
            && printf '%s' "$1" > /tmp/tls/mosquitto.conf \
            && printf '%s' "$2" > /tmp/tls/ca.crt \
            && printf '%s' "$3" > /tmp/tls/broker.crt \
            && printf '%s' "$4" > /tmp/tls/broker.key \
            && printf '%s' "$5" > /tmp/tls/acl \
            && chmod 0700 /tmp/tls && chmod 0600 /tmp/tls/* && chown -R mosquitto /tmp/tls \
            && exec mosquitto -c /tmp/tls/mosquitto.conf""";

    private static final char[] STORE_PASSWORD = "fleet-secret".toCharArray();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    @TempDir
    static Path storeDir;

    private static String containerName;

    private static String serverUri;

    private static Identity authority;

    private static X509Certificate brokerCertificate;

    private static Identity meter;

    private static Identity backend;

    private static Identity stranger;

    @BeforeAll
    static void startBroker() throws Exception {
        authority = Identity.authority("Fleet Root CA");
        Identity broker = authority.issue("broker", KeyPurposeId.id_kp_serverAuth, "127.0.0.1");
        brokerCertificate = broker.certificate;
        meter = authority.issue("meter-17", KeyPurposeId.id_kp_clientAuth, null);
        backend = authority.issue("billing-backend", KeyPurposeId.id_kp_clientAuth, null);
        stranger = Identity.authority("Rogue CA").issue("meter-17", KeyPurposeId.id_kp_clientAuth, null);

        containerName = "paho-tls-" + System.nanoTime();
        docker("run", "-d", "--name", containerName, "-p", "127.0.0.1::8883", IMAGE,
                "sh", "-c", BROKER_SCRIPT, "sh", BROKER_CONFIG, pem("CERTIFICATE", authority.certificate.getEncoded()),
                pem("CERTIFICATE", broker.certificate.getEncoded()),
                pem("PRIVATE KEY", broker.keyPair.getPrivate().getEncoded()), BROKER_ACL);
        String mapping = docker("port", containerName, "8883/tcp").lines().findFirst().orElseThrow();
        serverUri = "ssl://127.0.0.1:" + mapping.substring(mapping.lastIndexOf(':') + 1);
        awaitBroker();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerName != null) {
            docker("rm", "-f", "-v", containerName);
        }
    }

    @Test
    void meterAndBackendExchangeReadingsOverMutualTls() throws Exception {
        Inbox meterInbox = new Inbox();
        MqttClient meterClient = client("meter-17", meterInbox);
        Inbox backendInbox = new Inbox();
        MqttClient backendClient = client("billing-backend", backendInbox);
        PinnedBrokerVerifier pinned = new PinnedBrokerVerifier("CN=broker");
        try {
            MqttConnectOptions meterOptions = options();
            meterOptions.setSSLProperties(fileStoreProperties(meter));
            meterClient.connect(meterOptions);
            assertThat(meterClient.isConnected()).isTrue();
            assertThat(meterClient.getCurrentServerURI()).isEqualTo(serverUri);

            MqttConnectOptions backendOptions = options();
            backendOptions.setSocketFactory(socketFactory(backend, authority));
            backendOptions.setHttpsHostnameVerificationEnabled(false);
            backendOptions.setSSLHostnameVerifier(pinned);
            backendClient.connect(backendOptions);
            assertThat(pinned.verifiedHosts).containsExactly("127.0.0.1");
            assertThat(pinned.peerCertificates).containsExactly(brokerCertificate);

            assertThat(backendClient.subscribeWithResponse("meters/+/energy", 1).getGrantedQos())
                    .containsExactly(1);
            assertThat(meterClient.subscribeWithResponse("meters/meter-17/commands", 1).getGrantedQos())
                    .containsExactly(1);
            meterClient.subscribe("meters/meter-99/commands", 1);

            meterClient.publish("meters/meter-99/energy", bytes("kwh=999.0"), 1, false);
            meterClient.publish("meters/meter-17/energy", bytes("kwh=42.5"), 1, false);
            Received reading = backendInbox.next();
            assertThat(reading.topic).isEqualTo("meters/meter-17/energy");
            assertThat(reading.payload).isEqualTo("kwh=42.5");
            assertThat(reading.qos).isEqualTo(1);

            backendClient.publish("meters/meter-99/commands", bytes("tariff=peak"), 1, false);
            backendClient.publish("meters/meter-17/commands", bytes("tariff=night"), 1, false);
            Received command = meterInbox.next();
            assertThat(command.topic).isEqualTo("meters/meter-17/commands");
            assertThat(command.payload).isEqualTo("tariff=night");

            meterClient.publish("meters/meter-17/energy", bytes("kwh=43.0"), 1, false);
            assertThat(backendInbox.next().payload).isEqualTo("kwh=43.0");
            assertThat(backendInbox.isEmpty()).isTrue();
            assertThat(meterInbox.isEmpty()).isTrue();
            assertThat(meterInbox.lost).isEmpty();
            assertThat(backendInbox.lost).isEmpty();
        } finally {
            closeQuietly(meterClient);
            closeQuietly(backendClient);
        }
    }

    @Test
    void brokerAndClientsRejectUntrustedPeers() throws Exception {
        MqttClient jdkTrustClient = client("jdk-trust", new Inbox());
        try {
            assertThatThrownBy(() -> jdkTrustClient.connect(options()))
                    .isInstanceOf(MqttException.class)
                    .hasCauseInstanceOf(SSLHandshakeException.class);
            assertThat(jdkTrustClient.isConnected()).isFalse();
        } finally {
            closeQuietly(jdkTrustClient);
        }

        MqttClient pinningClient = client("pinning", new Inbox());
        PinnedBrokerVerifier wrongPin = new PinnedBrokerVerifier("CN=other-broker");
        try {
            MqttConnectOptions pinningOptions = options();
            pinningOptions.setSocketFactory(socketFactory(backend, authority));
            pinningOptions.setHttpsHostnameVerificationEnabled(false);
            pinningOptions.setSSLHostnameVerifier(wrongPin);
            assertThatThrownBy(() -> pinningClient.connect(pinningOptions))
                    .isInstanceOf(MqttException.class)
                    .hasCauseInstanceOf(SSLPeerUnverifiedException.class);
            assertThat(wrongPin.peerCertificates).containsExactly(brokerCertificate);
            assertThat(pinningClient.isConnected()).isFalse();
        } finally {
            closeQuietly(pinningClient);
        }

        MqttClient rogueClient = client("rogue", new Inbox());
        try {
            MqttConnectOptions rogueOptions = options();
            rogueOptions.setSocketFactory(socketFactory(stranger, authority));
            assertThatThrownBy(() -> rogueClient.connect(rogueOptions)).isInstanceOf(MqttException.class);
            assertThat(rogueClient.isConnected()).isFalse();
        } finally {
            closeQuietly(rogueClient);
        }
    }

    /// A key pair with its certificate, able to sign further certificates when it is a CA.
    private static final class Identity {

        private final KeyPair keyPair;

        private final X509Certificate certificate;

        private final X509Certificate issuer;

        private Identity(KeyPair keyPair, X509Certificate certificate, X509Certificate issuer) {
            this.keyPair = keyPair;
            this.certificate = certificate;
            this.issuer = issuer;
        }

        static Identity authority(String commonName) throws Exception {
            KeyPair keyPair = newKeyPair();
            X500Name name = new X500Name("CN=" + commonName);
            JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
            X509v3CertificateBuilder builder = builder(name, name, keyPair)
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign))
                    .addExtension(Extension.subjectKeyIdentifier, false,
                            extensions.createSubjectKeyIdentifier(keyPair.getPublic()));
            X509Certificate certificate = sign(builder, keyPair);
            return new Identity(keyPair, certificate, certificate);
        }

        Identity issue(String commonName, KeyPurposeId purpose, String ipAddress) throws Exception {
            KeyPair leafKeys = newKeyPair();
            JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
            X509v3CertificateBuilder builder = builder(
                    X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded()),
                    new X500Name("CN=" + commonName), leafKeys)
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                    .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature))
                    .addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purpose))
                    .addExtension(Extension.authorityKeyIdentifier, false,
                            extensions.createAuthorityKeyIdentifier(certificate))
                    .addExtension(Extension.subjectKeyIdentifier, false,
                            extensions.createSubjectKeyIdentifier(leafKeys.getPublic()));
            if (ipAddress != null) {
                builder.addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(new GeneralName(GeneralName.iPAddress, ipAddress)));
            }
            return new Identity(leafKeys, sign(builder, keyPair), certificate);
        }

        KeyStore keyStore() throws Exception {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry("client", keyPair.getPrivate(), STORE_PASSWORD,
                    new Certificate[] {certificate, issuer});
            return store;
        }

        KeyStore trustStore() throws Exception {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setCertificateEntry("authority", certificate);
            return store;
        }

        private static KeyPair newKeyPair() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        }

        private static X509v3CertificateBuilder builder(X500Name issuer, X500Name subject, KeyPair subjectKeys) {
            Instant now = Instant.now();
            return new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(System.nanoTime()),
                    Date.from(now.minus(Duration.ofHours(1))), Date.from(now.plus(Duration.ofDays(1))),
                    subject, subjectKeys.getPublic());
        }

        private static X509Certificate sign(X509v3CertificateBuilder builder, KeyPair signer) throws Exception {
            return new JcaX509CertificateConverter().getCertificate(
                    builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(signer.getPrivate())));
        }
    }

    /// Pins the broker by the subject of the certificate it presented, recording every check.
    private static final class PinnedBrokerVerifier implements HostnameVerifier {

        private final String expectedSubject;

        private final List<String> verifiedHosts = new ArrayList<>();

        private final List<Certificate> peerCertificates = new ArrayList<>();

        PinnedBrokerVerifier(String expectedSubject) {
            this.expectedSubject = expectedSubject;
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            try {
                Certificate[] chain = session.getPeerCertificates();
                peerCertificates.add(chain[0]);
                X509Certificate presented = (X509Certificate) chain[0];
                boolean matches = expectedSubject.equals(presented.getSubjectX500Principal().getName());
                if (matches) {
                    verifiedHosts.add(hostname);
                }
                return matches;
            } catch (SSLPeerUnverifiedException e) {
                return false;
            }
        }
    }

    /// Queues every message a client receives.
    private static final class Inbox implements MqttCallback {

        private final BlockingQueue<Received> arrived = new LinkedBlockingQueue<>();

        private final List<Throwable> lost = new ArrayList<>();

        @Override
        public void connectionLost(Throwable cause) {
            synchronized (lost) {
                lost.add(cause);
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            arrived.add(new Received(topic, message));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        Received next() throws InterruptedException {
            Received message = arrived.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(message).as("message within %d ms", TIMEOUT_MS).isNotNull();
            return message;
        }

        boolean isEmpty() {
            return arrived.isEmpty();
        }
    }

    /// One message as a client's callback observed it.
    private static final class Received {

        private final String topic;

        private final String payload;

        private final int qos;

        Received(String topic, MqttMessage message) {
            this.topic = topic;
            this.payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            this.qos = message.getQos();
        }
    }

    /// Writes the identity's key and its CA's trust anchor to PKCS12 files and describes them
    /// with the SSL properties the client turns into a socket factory.
    private static Properties fileStoreProperties(Identity identity) throws Exception {
        Path keyStore = storeDir.resolve("meter-keystore.p12");
        Path trustStore = storeDir.resolve("meter-truststore.p12");
        store(identity.keyStore(), keyStore);
        store(authority.trustStore(), trustStore);
        Properties properties = new Properties();
        properties.setProperty("com.ibm.ssl.protocol", "TLSv1.2");
        properties.setProperty("com.ibm.ssl.keyStore", keyStore.toString());
        properties.setProperty("com.ibm.ssl.keyStorePassword", new String(STORE_PASSWORD));
        properties.setProperty("com.ibm.ssl.keyStoreType", "PKCS12");
        properties.setProperty("com.ibm.ssl.trustStore", trustStore.toString());
        properties.setProperty("com.ibm.ssl.trustStorePassword", new String(STORE_PASSWORD));
        properties.setProperty("com.ibm.ssl.trustStoreType", "PKCS12");
        return properties;
    }

    private static SSLSocketFactory socketFactory(Identity identity, Identity trusted) throws Exception {
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(identity.keyStore(), STORE_PASSWORD);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trusted.trustStore());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    private static void store(KeyStore keyStore, Path file) throws Exception {
        try (OutputStream output = Files.newOutputStream(file)) {
            keyStore.store(output, STORE_PASSWORD);
        }
    }

    private static MqttClient client(String role, Inbox inbox) throws MqttException {
        MqttClient client = new MqttClient(serverUri, role + "-" + System.nanoTime(), new MemoryPersistence());
        client.setTimeToWait(TIMEOUT_MS);
        client.setCallback(inbox);
        return client;
    }

    private static MqttConnectOptions options() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        return options;
    }

    private static void closeQuietly(MqttClient client) throws MqttException {
        if (client.isConnected()) {
            client.disconnectForcibly(1_000, 1_000);
        }
        client.close();
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttClient probe = new MqttClient(serverUri, "startup-probe-" + System.nanoTime(), new MemoryPersistence());
            try {
                MqttConnectOptions probeOptions = options();
                probeOptions.setSocketFactory(socketFactory(backend, authority));
                probe.connect(probeOptions);
                probe.disconnect();
                return;
            } catch (MqttException e) {
                last = e;
            } finally {
                closeQuietly(probe);
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Mosquitto did not accept TLS connections in time", last);
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
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

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
