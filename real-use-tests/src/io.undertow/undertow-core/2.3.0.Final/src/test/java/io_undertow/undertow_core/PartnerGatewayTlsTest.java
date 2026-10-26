/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.util.Headers;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;

/**
 * HTTPS listeners with `Undertow.Builder.addHttpsListener`: TLS termination, HTTP/2 negotiated
 * over ALPN next to HTTP/1.1 on the same port, and optional client-certificate authentication
 * read back through the exchange's `SSLSessionInfo`.
 *
 * A partner gateway serves a status endpoint over TLS with a self-signed server certificate
 * issued by Bouncy Castle at startup. The listener requests, but does not require, a client
 * certificate: an anonymous client is served as `anonymous`, a partner presenting a certificate
 * the gateway trusts is identified by its subject, a client presenting an unknown
 * certificate is never taken for a partner, and a client that does not trust the gateway's
 * certificate aborts the handshake. The client is the JDK `HttpClient`, so ALPN
 * selection and the handshake outcome are observed on the wire.
 */
public class PartnerGatewayTlsTest {

    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    private Undertow server;
    private URI status;
    private Credentials serverCredentials;
    private Credentials partnerCredentials;

    @BeforeEach
    void startGateway() throws Exception {
        serverCredentials = Credentials.issue("CN=gateway.example, O=Gateway", true);
        partnerCredentials = Credentials.issue("CN=acme-billing, O=Acme Corp", false);

        SSLContext serverTls = SSLContext.getInstance("TLS");
        serverTls.init(
                serverCredentials.keyManagers().getKeyManagers(),
                trustManagers(partnerCredentials.certificate()).getTrustManagers(),
                null);

        server = Undertow.builder()
                .addHttpsListener(0, "127.0.0.1", serverTls)
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED)
                .setHandler(PartnerGatewayTlsTest::describeConnection)
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        status = URI.create("https://127.0.0.1:" + address.getPort() + "/status");
    }

    @AfterEach
    void stopGateway() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void anonymousClientNegotiatesHttp2OverTls() throws Exception {
        HttpClient client = client(HttpClient.Version.HTTP_2, null);

        HttpResponse<String> response = get(client);

        assertEquals(200, response.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, response.version());
        Map<String, String> seen = parse(response.body());
        assertEquals("https", seen.get("scheme"));
        assertEquals("HTTP/2.0", seen.get("protocol"));
        assertEquals("anonymous", seen.get("partner"));
        assertTrue(seen.get("cipher").startsWith("TLS_"), seen.get("cipher"));
        X509Certificate presented = (X509Certificate) response.sslSession().orElseThrow()
                .getPeerCertificates()[0];
        assertEquals(serverCredentials.certificate(), presented);
    }

    @Test
    void http11ClientIsServedOnTheSameListener() throws Exception {
        HttpClient client = client(HttpClient.Version.HTTP_1_1, null);

        HttpResponse<String> response = get(client);

        assertEquals(200, response.statusCode());
        assertEquals(HttpClient.Version.HTTP_1_1, response.version());
        Map<String, String> seen = parse(response.body());
        assertEquals("https", seen.get("scheme"));
        assertEquals("HTTP/1.1", seen.get("protocol"));
        assertEquals("anonymous", seen.get("partner"));
    }

    @Test
    void trustedPartnerIsIdentifiedByItsCertificate() throws Exception {
        HttpClient client = client(HttpClient.Version.HTTP_2, partnerCredentials);

        HttpResponse<String> first = get(client);
        HttpResponse<String> second = get(client);

        assertEquals(200, first.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, first.version());
        Map<String, String> seen = parse(first.body());
        assertEquals(partnerCredentials.certificate().getSubjectX500Principal().getName(),
                seen.get("partner"));
        assertTrue(seen.get("partner").contains("CN=acme-billing"), seen.get("partner"));
        assertEquals(partnerCredentials.certificate().getSerialNumber().toString(16),
                seen.get("serial"));
        assertEquals(seen.get("partner"), parse(second.body()).get("partner"));
    }

    @Test
    void unknownClientCertificateIsNeverTakenForAPartner() throws Exception {
        Credentials stranger = Credentials.issue("CN=stranger, O=Unknown", false);
        HttpClient client = client(HttpClient.Version.HTTP_2, stranger);

        HttpResponse<String> response = get(client);

        assertEquals(200, response.statusCode());
        Map<String, String> seen = parse(response.body());
        assertEquals("anonymous", seen.get("partner"));
        assertEquals(null, seen.get("serial"));
    }

    @Test
    void clientThatDoesNotTrustTheGatewayAbortsTheHandshake() throws Exception {
        Credentials impostor = Credentials.issue("CN=gateway.example, O=Impostor", true);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, trustManagers(impostor.certificate()).getTrustManagers(), null);
        HttpClient client = HttpClient.newBuilder()
                .sslContext(tls)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        assertThrows(IOException.class, () -> get(client));
    }

    private static void describeConnection(HttpServerExchange exchange) {
        Map<String, String> seen = new LinkedHashMap<>();
        seen.put("scheme", exchange.getRequestScheme());
        seen.put("protocol", exchange.getProtocol().toString());
        SSLSessionInfo session = exchange.getConnection().getSslSessionInfo();
        seen.put("cipher", session.getCipherSuite());
        seen.put("partner", "anonymous");
        try {
            Certificate[] chain = session.getPeerCertificates();
            X509Certificate partner = (X509Certificate) chain[0];
            seen.put("partner", partner.getSubjectX500Principal().getName());
            seen.put("serial", partner.getSerialNumber().toString(16));
        } catch (SSLPeerUnverifiedException anonymous) {
            // No client certificate was offered: the listener only requests one.
        } catch (Exception unexpected) {
            throw new IllegalStateException(unexpected);
        }
        StringBuilder body = new StringBuilder();
        seen.forEach((key, value) -> body.append(key).append('=').append(value).append('\n'));
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send(body.toString());
    }

    private HttpClient client(HttpClient.Version version, Credentials identity) throws Exception {
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(
                identity == null ? null : identity.keyManagers().getKeyManagers(),
                trustManagers(serverCredentials.certificate()).getTrustManagers(),
                null);
        return HttpClient.newBuilder()
                .version(version)
                .sslContext(tls)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private HttpResponse<String> get(HttpClient client) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(status)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> parse(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : body.split("\n")) {
            int split = line.indexOf('=');
            fields.put(line.substring(0, split), line.substring(split + 1));
        }
        return fields;
    }

    private static TrustManagerFactory trustManagers(X509Certificate trusted)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("trusted", trusted);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return factory;
    }

    /** A key pair and the self-signed certificate Bouncy Castle issued for it. */
    private record Credentials(KeyPair keys, X509Certificate certificate) {

        private static long nextSerial = 1000;

        static Credentials issue(String subject, boolean loopbackServer) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            KeyPair keys = generator.generateKeyPair();
            X500Name name = new X500Name(subject);
            Instant now = Instant.now();
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name,
                    BigInteger.valueOf(nextSerial()),
                    Date.from(now.minus(Duration.ofHours(1))),
                    Date.from(now.plus(Duration.ofDays(1))),
                    name,
                    keys.getPublic());
            if (loopbackServer) {
                builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(
                        new GeneralName[] {
                            new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                            new GeneralName(GeneralName.dNSName, "localhost")
                        }));
            }
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                    .build(keys.getPrivate());
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));
            return new Credentials(keys, certificate);
        }

        private static synchronized long nextSerial() {
            return nextSerial++;
        }

        KeyManagerFactory keyManagers() throws GeneralSecurityException, IOException {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry("identity", keys.getPrivate(), STORE_PASSWORD,
                    new Certificate[] {certificate});
            KeyManagerFactory factory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            factory.init(store, STORE_PASSWORD);
            return factory;
        }
    }
}
