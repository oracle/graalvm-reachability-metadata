/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.EncryptionScheme;
import org.bouncycastle.asn1.pkcs.KeyDerivationFunc;
import org.bouncycastle.asn1.pkcs.PBES2Parameters;
import org.bouncycastle.asn1.pkcs.PBKDF2Params;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serves a partner portal over the TLS-enabled HTTP/1.1 NIO connector, where an `SSLHostConfig`
 * loads the server certificate and an encrypted PKCS#8 key from PEM files, trusts partner
 * certificates from a PKCS#12 truststore, and asks clients for an optional certificate.
 *
 * A logistics company exposes its partner portal straight from an embedded Tomcat. The operations
 * team issues PEM key material with BouncyCastle, protects the private key with a passphrase and
 * restricts the connector to TLS 1.3. Partners that present a certificate the portal trusts are
 * greeted by the certificate subject the servlet reads from the request, anonymous TLS callers
 * see the public page, callers that do not trust the portal certificate fail the handshake, and
 * a client that speaks plain HTTP to the TLS port is told that the port requires TLS.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MutualTlsPortalConnectorTest {

    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    private static final String KEY_PASSPHRASE = "portal-key-passphrase";

    private static final String PARTNER_SUBJECT = "CN=acme-freight,O=Acme Freight,C=DE";

    private Path baseDir;

    private Tomcat tomcat;

    private int port;

    private IssuedCertificate serverCertificate;

    private IssuedCertificate partnerCertificate;

    @BeforeAll
    void startPortal() throws Exception {
        baseDir = Files.createTempDirectory("partner-portal-tomcat");
        serverCertificate = IssuedCertificate.selfSigned("CN=localhost,O=Portal Operations", true);
        partnerCertificate = IssuedCertificate.selfSigned(PARTNER_SUBJECT, false);

        Path certificateFile = baseDir.resolve("portal-cert.pem");
        Path keyFile = baseDir.resolve("portal-key.pem");
        serverCertificate.writeCertificatePem(certificateFile);
        serverCertificate.writeEncryptedKeyPem(keyFile, KEY_PASSPHRASE);

        Path truststoreFile = baseDir.resolve("partners.p12");
        KeyStore partners = KeyStore.getInstance("PKCS12");
        partners.load(null, null);
        partners.setCertificateEntry("acme-freight", partnerCertificate.certificate());
        try (OutputStream out = Files.newOutputStream(truststoreFile)) {
            partners.store(out, STORE_PASSWORD);
        }

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setProtocols("TLSv1.3");
        sslHostConfig.setCertificateVerification("optional");
        sslHostConfig.setTruststoreFile(truststoreFile.toString());
        sslHostConfig.setTruststorePassword(new String(STORE_PASSWORD));
        sslHostConfig.setTruststoreType("PKCS12");
        SSLHostConfigCertificate certificate =
                new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.RSA);
        certificate.setCertificateFile(certificateFile.toString());
        certificate.setCertificateKeyFile(keyFile.toString());
        certificate.setCertificateKeyPassword(KEY_PASSPHRASE);
        sslHostConfig.addCertificate(certificate);

        Connector connector = new Connector();
        connector.setPort(0);
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setProperty("SSLEnabled", "true");
        connector.addSslHostConfig(sslHostConfig);

        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setConnector(connector);
        Context context = tomcat.addContext("/portal", baseDir.toString());
        Tomcat.addServlet(context, "welcome", new WelcomeServlet());
        context.addServletMappingDecoded("/welcome", "welcome");

        tomcat.start();
        port = connector.getLocalPort();
    }

    @AfterAll
    void stopPortal() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void greetsAPartnerByTheSubjectOfItsClientCertificate() throws Exception {
        HttpClient client = httpClient(clientContext(partnerCertificate));

        HttpResponse<String> response = client.send(welcomeRequest(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(response.body()).isEqualTo("""
                scheme=https secure=true protocol=TLSv1.3
                partner=%s
                """.formatted(PARTNER_SUBJECT));
        assertThat(response.sslSession()).hasValueSatisfying(session -> {
            assertThat(session.getProtocol()).isEqualTo("TLSv1.3");
            assertThat(session.getLocalCertificates()).hasSize(1);
        });
    }

    @Test
    void servesThePublicPageToAnAnonymousTlsCaller() throws Exception {
        HttpClient client = httpClient(clientContext(null));

        HttpResponse<String> response = client.send(welcomeRequest(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("""
                scheme=https secure=true protocol=TLSv1.3
                partner=anonymous
                """);
        assertThat(response.sslSession()).isPresent();
        Certificate[] presented = response.sslSession().get().getPeerCertificates();
        assertThat(presented).hasSize(1);
        assertThat(((X509Certificate) presented[0]).getSubjectX500Principal().getName())
                .isEqualTo("CN=localhost,O=Portal Operations");
    }

    @Test
    void failsTheHandshakeForACallerThatDoesNotTrustThePortal() throws Exception {
        KeyStore strangers = KeyStore.getInstance("PKCS12");
        strangers.load(null, null);
        strangers.setCertificateEntry("unrelated",
                IssuedCertificate.selfSigned("CN=unrelated-portal,O=Elsewhere", true).certificate());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(strangers);
        SSLContext untrusting = SSLContext.getInstance("TLS");
        untrusting.init(null, trust.getTrustManagers(), null);
        HttpClient client = httpClient(untrusting);

        assertThatThrownBy(() -> client.send(welcomeRequest(), HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(SSLHandshakeException.class);
    }

    @Test
    void tellsAPlainHttpClientThatThePortRequiresTls() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 10_000);
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write("GET /portal/welcome HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String answer = readUntilClosed(socket.getInputStream());

            assertThat(answer).startsWith("HTTP/1.1 400");
            assertThat(answer).contains("This combination of host and port requires TLS.");
        }
    }

    private HttpRequest welcomeRequest() {
        return HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/portal/welcome"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
    }

    private static HttpClient httpClient(SSLContext sslContext) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(sslContext)
                .build();
    }

    /** Trusts the portal certificate and, when given one, presents the partner's identity. */
    private SSLContext clientContext(IssuedCertificate identity) throws GeneralSecurityException, IOException {
        KeyStore trusted = KeyStore.getInstance("PKCS12");
        trusted.load(null, null);
        trusted.setCertificateEntry("portal", serverCertificate.certificate());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trusted);

        KeyManagerFactory keys = null;
        if (identity != null) {
            KeyStore identityStore = KeyStore.getInstance("PKCS12");
            identityStore.load(null, null);
            identityStore.setKeyEntry("partner", identity.keyPair().getPrivate(), STORE_PASSWORD,
                    new Certificate[] {identity.certificate()});
            keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(identityStore, STORE_PASSWORD);
        }

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys == null ? null : keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }

    private static String readUntilClosed(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /** Reports the TLS facts the connector attached to the request and the partner, if any. */
    static final class WelcomeServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            X509Certificate[] chain =
                    (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
            String partner = chain == null || chain.length == 0
                    ? "anonymous"
                    : chain[0].getSubjectX500Principal().getName();
            response.setContentType("text/plain");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            PrintWriter writer = response.getWriter();
            writer.print("scheme=" + request.getScheme()
                    + " secure=" + request.isSecure()
                    + " protocol=" + request.getAttribute("jakarta.servlet.request.secure_protocol") + "\n");
            writer.print("partner=" + partner + "\n");
        }
    }

    /** A self-signed RSA certificate and its key pair, issued with BouncyCastle. */
    record IssuedCertificate(KeyPair keyPair, X509Certificate certificate) {

        static IssuedCertificate selfSigned(String subject, boolean forLocalhost)
                throws GeneralSecurityException, IOException, OperatorCreationException {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            X500Principal name = new X500Principal(subject);
            Instant now = Instant.now();
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name,
                    BigInteger.valueOf(now.toEpochMilli()),
                    Date.from(now.minus(Duration.ofDays(1))),
                    Date.from(now.plus(Duration.ofDays(30))),
                    name,
                    keyPair.getPublic());
            if (forLocalhost) {
                builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[] {
                        new GeneralName(GeneralName.dNSName, "localhost"),
                        new GeneralName(GeneralName.iPAddress, "127.0.0.1")}));
            }
            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                    builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));
            return new IssuedCertificate(keyPair, certificate);
        }

        void writeCertificatePem(Path file) throws IOException {
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.US_ASCII);
                    JcaPEMWriter pem = new JcaPEMWriter(out)) {
                pem.writeObject(certificate);
            }
        }

        /** Seals the private key as PBES2 (PBKDF2 with HMAC-SHA256, AES-256-CBC) PKCS#8. */
        void writeEncryptedKeyPem(Path file, String passphrase) throws IOException, GeneralSecurityException {
            byte[] salt = new byte[16];
            byte[] iv = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(salt);
            random.nextBytes(iv);
            int iterations = 10_000;
            byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256))
                    .getEncoded();
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derived, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(keyPair.getPrivate().getEncoded());

            AlgorithmIdentifier pbes2 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_PBES2, new PBES2Parameters(
                    new KeyDerivationFunc(PKCSObjectIdentifiers.id_PBKDF2, new PBKDF2Params(salt, iterations, 32,
                            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_hmacWithSHA256, DERNull.INSTANCE))),
                    new EncryptionScheme(NISTObjectIdentifiers.id_aes256_CBC, new DEROctetString(iv))));
            byte[] sealed = new EncryptedPrivateKeyInfo(pbes2, encrypted).getEncoded();
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.US_ASCII);
                    JcaPEMWriter pem = new JcaPEMWriter(out)) {
                pem.writeObject(new PemObject("ENCRYPTED PRIVATE KEY", sealed));
            }
        }
    }
}
