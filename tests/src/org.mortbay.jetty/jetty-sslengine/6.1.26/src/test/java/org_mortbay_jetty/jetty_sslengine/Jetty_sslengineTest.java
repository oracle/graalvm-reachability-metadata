/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_sslengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.HttpSchemes;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.security.SslSelectChannelConnector;
import org.mortbay.thread.QueuedThreadPool;

public class Jetty_sslengineTest {
    private static final String PASSWORD = "changeit";
    private static final String[] TLS12_RSA_CIPHER_SUITE_PREFERENCES = {
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_256_GCM_SHA384"
    };
    private static final String LOCALHOST_KEYSTORE = """
            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
            fXRkkNc2rSbWfcGc+/PUmK0NXJvJmiRISrVTzTg=
            """;

    @Test
    void exposesSslConfigurationThroughPublicProperties() {
        SslSelectChannelConnector connector = new SslSelectChannelConnector();
        String[] excludedCipherSuites = {"TLS_FAKE_CIPHER_FOR_CONFIGURATION_TEST"};

        connector.setKeystore("/tmp/server.jks");
        connector.setKeystoreType("PKCS12");
        connector.setTruststore("/tmp/trust.jks");
        connector.setTruststoreType("JKS");
        connector.setAlgorithm("SunX509");
        connector.setProtocol("TLSv1.2");
        connector.setProvider("SunJSSE");
        connector.setSecureRandomAlgorithm("SHA1PRNG");
        connector.setSslKeyManagerFactoryAlgorithm("SunX509");
        connector.setSslTrustManagerFactoryAlgorithm("PKIX");
        connector.setNeedClientAuth(true);
        connector.setWantClientAuth(true);
        connector.setAllowRenegotiate(true);
        connector.setExcludeCipherSuites(excludedCipherSuites);
        connector.setPassword(PASSWORD);
        connector.setKeyPassword(PASSWORD);
        connector.setTrustPassword(PASSWORD);

        assertThat(connector.getKeystore()).isEqualTo("/tmp/server.jks");
        assertThat(connector.getKeystoreType()).isEqualTo("PKCS12");
        assertThat(connector.getTruststore()).isEqualTo("/tmp/trust.jks");
        assertThat(connector.getTruststoreType()).isEqualTo("JKS");
        assertThat(connector.getAlgorithm()).isEqualTo("SunX509");
        assertThat(connector.getProtocol()).isEqualTo("TLSv1.2");
        assertThat(connector.getProvider()).isEqualTo("SunJSSE");
        assertThat(connector.getSecureRandomAlgorithm()).isEqualTo("SHA1PRNG");
        assertThat(connector.getSslKeyManagerFactoryAlgorithm()).isEqualTo("SunX509");
        assertThat(connector.getSslTrustManagerFactoryAlgorithm()).isEqualTo("PKIX");
        assertThat(connector.getNeedClientAuth()).isTrue();
        assertThat(connector.getWantClientAuth()).isTrue();
        assertThat(connector.isAllowRenegotiate()).isTrue();
        assertThat(connector.getExcludeCipherSuites()).containsExactly(excludedCipherSuites);
    }

    @Test
    void evaluatesIntegralAndConfidentialPortsAgainstRequestPort() {
        SslSelectChannelConnector connector = new SslSelectChannelConnector();
        Request request = new Request();

        request.setServerPort(8443);
        assertThat(connector.isConfidential(request)).isTrue();
        assertThat(connector.isIntegral(request)).isTrue();

        connector.setConfidentialPort(9443);
        connector.setIntegralPort(9444);
        assertThat(connector.isConfidential(request)).isFalse();
        assertThat(connector.isIntegral(request)).isFalse();

        request.setServerPort(9443);
        assertThat(connector.isConfidential(request)).isTrue();
        assertThat(connector.isIntegral(request)).isFalse();

        request.setServerPort(9444);
        assertThat(connector.isConfidential(request)).isFalse();
        assertThat(connector.isIntegral(request)).isTrue();
    }

    @Test
    void servesHttpsRequestAndAddsServletSslAttributes() throws Exception {
        Path keystore = writeKeystore();
        SslSelectChannelConnector connector = new SslSelectChannelConnector();
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setMaxIdleTime(5_000);
        connector.setKeystore(keystore.toString());
        connector.setTruststore(keystore.toString());
        connector.setPassword(PASSWORD);
        connector.setKeyPassword(PASSWORD);
        connector.setTrustPassword(PASSWORD);
        connector.setNeedClientAuth(false);
        connector.setWantClientAuth(false);
        connector.setProtocol("TLSv1.2");
        connector.setExcludeCipherSuites(new String[] {"TLS_FAKE_CIPHER_FOR_INTEGRATION_TEST"});

        QueuedThreadPool threadPool = new QueuedThreadPool(8);
        threadPool.setMinThreads(2);
        threadPool.setDaemon(true);

        AtomicReference<String> requestScheme = new AtomicReference<>();
        AtomicReference<String> cipherSuite = new AtomicReference<>();
        AtomicReference<Integer> keySize = new AtomicReference<>();
        Server server = new Server();
        server.setThreadPool(threadPool);
        server.addConnector(connector);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                    throws IOException, ServletException {
                if (dispatch == Handler.REQUEST) {
                    requestScheme.set(request.getScheme());
                    cipherSuite.set((String) request.getAttribute("javax.servlet.request.cipher_suite"));
                    keySize.set((Integer) request.getAttribute("javax.servlet.request.key_size"));
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/plain");
                    byte[] body = "secure-response".getBytes(StandardCharsets.UTF_8);
                    response.setContentLength(body.length);
                    try (OutputStream output = response.getOutputStream()) {
                        output.write(body);
                    }
                    ((Request) request).setHandled(true);
                }
            }
        });

        try {
            server.start();
            assertThat(connector.getLocalPort()).isPositive();

            String body = get("https://127.0.0.1:" + connector.getLocalPort() + "/ssl-check");

            assertThat(body).isEqualTo("secure-response");
            assertThat(requestScheme.get()).isEqualTo(HttpSchemes.HTTPS);
            assertThat(cipherSuite.get()).startsWith("TLS_");
            assertThat(keySize.get()).isGreaterThan(0);
            assertThat(connector.getConnectionsOpen()).isGreaterThanOrEqualTo(0);
            assertThatCode(connector::statsReset).doesNotThrowAnyException();
        } finally {
            if (server.isStarted() || server.isStarting()) {
                server.stop();
            }
            server.destroy();
        }
    }

    @Test
    void requiresClientCertificateWhenNeedClientAuthIsEnabled() throws Exception {
        Path keystore = writeKeystore();
        Path truststore = writeTruststoreForCertificate(keystore);
        SslSelectChannelConnector connector = new SslSelectChannelConnector();
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setMaxIdleTime(5_000);
        connector.setKeystore(keystore.toString());
        connector.setTruststore(truststore.toString());
        connector.setPassword(PASSWORD);
        connector.setKeyPassword(PASSWORD);
        connector.setTrustPassword(PASSWORD);
        connector.setNeedClientAuth(true);
        connector.setWantClientAuth(false);
        connector.setProtocol("TLSv1.2");

        QueuedThreadPool threadPool = new QueuedThreadPool(8);
        threadPool.setMinThreads(2);
        threadPool.setDaemon(true);

        Server server = new Server();
        server.setThreadPool(threadPool);
        server.addConnector(connector);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                    throws IOException, ServletException {
                if (dispatch == Handler.REQUEST) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/plain");
                    byte[] body = "client-auth-response".getBytes(StandardCharsets.UTF_8);
                    response.setContentLength(body.length);
                    try (OutputStream output = response.getOutputStream()) {
                        output.write(body);
                    }
                    ((Request) request).setHandled(true);
                }
            }
        });

        try {
            server.start();
            assertThat(connector.getLocalPort()).isPositive();

            String url = "https://127.0.0.1:" + connector.getLocalPort() + "/client-auth-check";
            assertThatThrownBy(() -> get(url)).isInstanceOf(IOException.class);

            String body = get(url, sslContextWithClientCertificate(keystore));

            assertThat(body).isEqualTo("client-auth-response");
        } finally {
            if (server.isStarted() || server.isStarting()) {
                server.stop();
            }
            server.destroy();
        }
    }

    @Test
    void excludesConfiguredCipherSuitesDuringHandshake() throws Exception {
        Path keystore = writeKeystore();
        String expectedCipherSuite = selectEnabledTls12RsaCipherSuite();
        String[] excludedCipherSuites = enabledTls12CipherSuitesExcept(expectedCipherSuite);

        SslSelectChannelConnector connector = new SslSelectChannelConnector();
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setMaxIdleTime(5_000);
        connector.setKeystore(keystore.toString());
        connector.setTruststore(keystore.toString());
        connector.setPassword(PASSWORD);
        connector.setKeyPassword(PASSWORD);
        connector.setTrustPassword(PASSWORD);
        connector.setNeedClientAuth(false);
        connector.setWantClientAuth(false);
        connector.setProtocol("TLSv1.2");
        connector.setExcludeCipherSuites(excludedCipherSuites);

        QueuedThreadPool threadPool = new QueuedThreadPool(8);
        threadPool.setMinThreads(2);
        threadPool.setDaemon(true);

        AtomicReference<String> negotiatedCipherSuite = new AtomicReference<>();
        Server server = new Server();
        server.setThreadPool(threadPool);
        server.addConnector(connector);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                    throws IOException, ServletException {
                if (dispatch == Handler.REQUEST) {
                    negotiatedCipherSuite.set((String) request.getAttribute("javax.servlet.request.cipher_suite"));
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/plain");
                    byte[] body = "cipher-suite-response".getBytes(StandardCharsets.UTF_8);
                    response.setContentLength(body.length);
                    try (OutputStream output = response.getOutputStream()) {
                        output.write(body);
                    }
                    ((Request) request).setHandled(true);
                }
            }
        });

        try {
            server.start();
            assertThat(connector.getLocalPort()).isPositive();

            String body = get("https://127.0.0.1:" + connector.getLocalPort() + "/cipher-suite-check");

            assertThat(body).isEqualTo("cipher-suite-response");
            assertThat(negotiatedCipherSuite.get()).isEqualTo(expectedCipherSuite);
        } finally {
            if (server.isStarted() || server.isStarting()) {
                server.stop();
            }
            server.destroy();
        }
    }

    private static Path writeKeystore() throws IOException {
        Path file = Files.createTempFile("jetty-sslengine-test-", ".jks");
        byte[] keystore = Base64.getMimeDecoder().decode(LOCALHOST_KEYSTORE);
        Files.write(file, keystore);
        file.toFile().deleteOnExit();
        return file;
    }

    private static Path writeTruststoreForCertificate(Path keystore) throws Exception {
        KeyStore sourceKeyStore = loadKeyStore(keystore);
        Enumeration<String> aliases = sourceKeyStore.aliases();
        assertThat(aliases.hasMoreElements()).isTrue();
        Certificate certificate = sourceKeyStore.getCertificate(aliases.nextElement());
        assertThat(certificate).isNotNull();

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, PASSWORD.toCharArray());
        trustStore.setCertificateEntry("client", certificate);

        Path file = Files.createTempFile("jetty-sslengine-truststore-test-", ".jks");
        try (OutputStream output = Files.newOutputStream(file)) {
            trustStore.store(output, PASSWORD.toCharArray());
        }
        file.toFile().deleteOnExit();
        return file;
    }

    private static String selectEnabledTls12RsaCipherSuite() throws Exception {
        List<String> enabledCipherSuites = Arrays.asList(enabledTls12CipherSuites());
        for (String cipherSuite : TLS12_RSA_CIPHER_SUITE_PREFERENCES) {
            if (enabledCipherSuites.contains(cipherSuite)) {
                return cipherSuite;
            }
        }
        throw new IllegalStateException("No enabled TLS 1.2 RSA cipher suite found: " + enabledCipherSuites);
    }

    private static String[] enabledTls12CipherSuitesExcept(String retainedCipherSuite) throws Exception {
        List<String> excludedCipherSuites = new ArrayList<>(Arrays.asList(enabledTls12CipherSuites()));
        assertThat(excludedCipherSuites.remove(retainedCipherSuite)).isTrue();
        return excludedCipherSuites.toArray(new String[0]);
    }

    private static String[] enabledTls12CipherSuites() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, null, new SecureRandom());
        return sslContext.createSSLEngine().getEnabledCipherSuites();
    }

    private static SSLContext sslContextWithClientCertificate(Path keystore) throws Exception {
        KeyStore clientKeyStore = loadKeyStore(keystore);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(clientKeyStore, PASSWORD.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(
                keyManagerFactory.getKeyManagers(), new TrustManager[] {trustAllCertificates()}, new SecureRandom());
        return sslContext;
    }

    private static KeyStore loadKeyStore(Path keystore) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(keystore)) {
            keyStore.load(input, PASSWORD.toCharArray());
        }
        return keyStore;
    }

    private static String get(String url) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, new TrustManager[] {trustAllCertificates()}, new SecureRandom());
        return get(url, sslContext);
    }

    private static String get(String url, SSLContext sslContext) throws Exception {
        HostnameVerifier verifier = (host, session) -> "127.0.0.1".equals(host) || "localhost".equals(host);
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier(verifier);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        try {
            return new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static X509TrustManager trustAllCertificates() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                assertThat(chain).isNotNull();
                assertThat(authType).isNotBlank();
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                assertThat(chain).isNotEmpty();
                assertThat(authType).isNotBlank();
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
