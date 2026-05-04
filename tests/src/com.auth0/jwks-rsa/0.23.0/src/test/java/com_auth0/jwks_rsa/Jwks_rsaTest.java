/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_auth0.jwks_rsa;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwk.NetworkException;
import com.auth0.jwk.RateLimitReachedException;
import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwk.UrlJwkProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class Jwks_rsaTest {
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    @Test
    void createsJwkFromValuesAndBuildsRsaPublicKey() throws Exception {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kid", "rsa-key");
        values.put("kty", "RSA");
        values.put("alg", "RS256");
        values.put("use", "sig");
        values.put("key_ops", List.of("verify", "encrypt"));
        values.put("x5u", "https://issuer.example/certs");
        values.put("x5c", List.of("certificate-one", "certificate-two"));
        values.put("x5t", "thumbprint");
        values.put("n", base64Url(rsaPublicKey.getModulus()));
        values.put("e", base64Url(rsaPublicKey.getPublicExponent()));
        values.put("custom_claim", "custom-value");

        Jwk jwk = Jwk.fromValues(values);

        assertThat(jwk.getId()).isEqualTo("rsa-key");
        assertThat(jwk.getType()).isEqualTo("RSA");
        assertThat(jwk.getAlgorithm()).isEqualTo("RS256");
        assertThat(jwk.getUsage()).isEqualTo("sig");
        assertThat(jwk.getOperations()).isEqualTo("verify,encrypt");
        assertThat(jwk.getOperationsAsList()).containsExactly("verify", "encrypt");
        assertThat(jwk.getCertificateUrl()).isEqualTo("https://issuer.example/certs");
        assertThat(jwk.getCertificateChain()).containsExactly("certificate-one", "certificate-two");
        assertThat(jwk.getCertificateThumbprint()).isEqualTo("thumbprint");
        assertThat(jwk.getAdditionalAttributes())
                .containsEntry("n", base64Url(rsaPublicKey.getModulus()))
                .containsEntry("e", base64Url(rsaPublicKey.getPublicExponent()))
                .containsEntry("custom_claim", "custom-value")
                .doesNotContainKeys("kid", "kty", "alg", "use", "key_ops", "x5u", "x5c", "x5t");
        assertThat(jwk.toString())
                .contains("rsa-key")
                .contains("RSA")
                .contains("RS256")
                .contains("custom_claim=custom-value");
        assertThat(jwk.getPublicKey().getEncoded()).isEqualTo(rsaPublicKey.getEncoded());
    }

    @Test
    void acceptsSingleKeyOperationString() {
        Map<String, Object> values = Map.of(
                "kid", "key-with-single-operation",
                "kty", "RSA",
                "key_ops", "verify",
                "n", "AQAB",
                "e", "AQAB");

        Jwk jwk = Jwk.fromValues(values);

        assertThat(jwk.getOperations()).isEqualTo("verify");
        assertThat(jwk.getOperationsAsList()).containsExactly("verify");
    }

    @Test
    void createsEcPublicKeyForSupportedCurve() throws Exception {
        ECPublicKey ecPublicKey = (ECPublicKey) generateEcKeyPair().getPublic();
        int fieldSizeBytes = (ecPublicKey.getParams().getCurve().getField().getFieldSize() + Byte.SIZE - 1)
                / Byte.SIZE;
        Map<String, Object> values = Map.of(
                "kid", "ec-key",
                "kty", "EC",
                "crv", "P-256",
                "x", base64Url(ecPublicKey.getW().getAffineX(), fieldSizeBytes),
                "y", base64Url(ecPublicKey.getW().getAffineY(), fieldSizeBytes));

        PublicKey publicKey = Jwk.fromValues(values).getPublicKey();

        assertThat(publicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(publicKey.getEncoded()).isEqualTo(ecPublicKey.getEncoded());
    }

    @Test
    void rejectsInvalidJwkValuesAndUnsupportedPublicKeys() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Jwk.fromValues(Map.of("kid", "missing-type")))
                .withMessageContaining("not from a valid jwk");

        assertThatExceptionOfType(InvalidPublicKeyException.class)
                .isThrownBy(() -> Jwk.fromValues(Map.of("kid", "oct-key", "kty", "oct")).getPublicKey())
                .withMessageContaining("not supported");

        assertThatExceptionOfType(InvalidPublicKeyException.class)
                .isThrownBy(() -> Jwk.fromValues(Map.of(
                        "kid", "unsupported-curve",
                        "kty", "EC",
                        "crv", "P-999",
                        "x", "AQAB",
                        "y", "AQAB")).getPublicKey())
                .withMessageContaining("Invalid or unsupported curve type");
    }

    @Test
    void urlProviderFetchesAllKeysAndFindsKeysByKid() throws Exception {
        RSAPublicKey firstKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        RSAPublicKey secondKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        try (LocalJwksServer server = new LocalJwksServer(exchange -> sendJson(exchange, jwksJson(
                rsaJwkJson("first", firstKey),
                rsaJwkJson("second", secondKey))))) {
            UrlJwkProvider provider = new UrlJwkProvider(server.url());

            List<Jwk> keys = provider.getAll();
            Jwk second = provider.get("second");

            assertThat(keys).extracting(Jwk::getId).containsExactly("first", "second");
            assertThat(second.getId()).isEqualTo("second");
            assertThat(second.getPublicKey().getEncoded()).isEqualTo(secondKey.getEncoded());
            assertThat(server.requests()).isEqualTo(2);
        }
    }

    @Test
    void urlProviderReturnsOnlyKeyWhenKidIsNull() throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        try (LocalJwksServer server = new LocalJwksServer(
                exchange -> sendJson(exchange, jwksJson(rsaJwkJson("only", publicKey))))) {
            Jwk key = new UrlJwkProvider(server.url()).get(null);

            assertThat(key.getId()).isEqualTo("only");
            assertThat(key.getPublicKey().getEncoded()).isEqualTo(publicKey.getEncoded());
        }
    }

    @Test
    void urlProviderStringDomainFetchesWellKnownJwksEndpoint() throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        try (LocalJwksServer server = new LocalJwksServer(exchange -> {
            requestedUri.set(exchange.getRequestURI());
            sendJson(exchange, jwksJson(rsaJwkJson("domain-key", publicKey)));
        })) {
            String domain = "http://%s:%d"
                    .formatted(server.address().getHostString(), server.address().getPort());

            Jwk key = new UrlJwkProvider(domain).get("domain-key");

            assertThat(key.getId()).isEqualTo("domain-key");
            assertThat(key.getPublicKey().getEncoded()).isEqualTo(publicKey.getEncoded());
            assertThat(requestedUri.get().getPath()).isEqualTo("/.well-known/jwks.json");
            assertThat(server.requests()).isEqualTo(1);
        }
    }

    @Test
    void urlProviderRefreshesWhenKeyIsMissingFromCachedSet() throws Exception {
        RSAPublicKey firstKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        RSAPublicKey secondKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        AtomicInteger request = new AtomicInteger();
        try (LocalJwksServer server = new LocalJwksServer(exchange -> {
            if (request.incrementAndGet() == 1) {
                sendJson(exchange, jwksJson(rsaJwkJson("first", firstKey)));
            } else {
                sendJson(exchange, jwksJson(rsaJwkJson("second", secondKey)));
            }
        })) {
            Jwk key = new UrlJwkProvider(server.url()).get("second");

            assertThat(key.getId()).isEqualTo("second");
            assertThat(server.requests()).isEqualTo(2);
        }
    }

    @Test
    void urlProviderReportsEmptyMalformedMissingAndUnavailableJwks() throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        try (LocalJwksServer emptyKeysServer = new LocalJwksServer(exchange -> sendJson(exchange, "{\"keys\":[]}"));
                LocalJwksServer malformedKeyServer = new LocalJwksServer(
                        exchange -> sendJson(exchange, "{\"keys\":[{\"kid\":\"broken\"}]}")
                );
                LocalJwksServer missingKeyServer = new LocalJwksServer(
                        exchange -> sendJson(exchange, jwksJson(rsaJwkJson("present", publicKey))))) {
            assertThatExceptionOfType(SigningKeyNotFoundException.class)
                    .isThrownBy(() -> new UrlJwkProvider(emptyKeysServer.url()).getAll())
                    .withMessageContaining("No keys found");

            assertThatExceptionOfType(SigningKeyNotFoundException.class)
                    .isThrownBy(() -> new UrlJwkProvider(malformedKeyServer.url()).getAll())
                    .withMessageContaining("Failed to parse jwk");

            assertThatExceptionOfType(SigningKeyNotFoundException.class)
                    .isThrownBy(() -> new UrlJwkProvider(missingKeyServer.url()).get("absent"))
                    .withMessageContaining("No key found")
                    .withMessageContaining("absent");
            assertThat(missingKeyServer.requests()).isEqualTo(2);
        }

        LocalJwksServer stoppedServer = new LocalJwksServer(exchange -> sendJson(exchange, "{}"));
        URL stoppedUrl = stoppedServer.url();
        stoppedServer.close();

        assertThatExceptionOfType(NetworkException.class)
                .isThrownBy(() -> new UrlJwkProvider(stoppedUrl, 200, 200).getAll())
                .withMessageContaining("Cannot obtain jwks");
    }

    @Test
    void builderSendsCustomHeadersAndCachesKeys() throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        AtomicReference<String> observedHeader = new AtomicReference<>();
        try (LocalJwksServer server = new LocalJwksServer(exchange -> {
            observedHeader.set(exchange.getRequestHeaders().getFirst("X-Test-Header"));
            sendJson(exchange, jwksJson(rsaJwkJson("cached", publicKey)));
        })) {
            JwkProvider provider = new JwkProviderBuilder(server.url())
                    .headers(Map.of("X-Test-Header", "header-value"))
                    .cached(2, Duration.ofMinutes(1))
                    .rateLimited(false)
                    .timeouts(500, 500)
                    .build();

            assertThat(provider.get("cached").getId()).isEqualTo("cached");
            assertThat(provider.get("cached").getId()).isEqualTo("cached");

            assertThat(observedHeader.get()).isEqualTo("header-value");
            assertThat(server.requests()).isEqualTo(1);
        }
    }

    @Test
    void builderCanDisableCacheAndEnforceRateLimit() throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        try (LocalJwksServer server = new LocalJwksServer(
                exchange -> sendJson(exchange, jwksJson(rsaJwkJson("limited", publicKey))))) {
            JwkProvider provider = new JwkProviderBuilder(server.url())
                    .cached(false)
                    .rateLimited(1, 1, TimeUnit.HOURS)
                    .timeouts(500, 500)
                    .build();

            assertThat(provider.get("limited").getId()).isEqualTo("limited");
            assertThatExceptionOfType(RateLimitReachedException.class)
                    .isThrownBy(() -> provider.get("limited"))
                    .satisfies(exception -> assertThat(exception.getAvailableIn()).isGreaterThan(0L));
            assertThat(server.requests()).isEqualTo(1);
        }
    }

    @Test
    void builderRoutesRequestsThroughConfiguredProxy() throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) generateRsaKeyPair().getPublic();
        AtomicReference<URI> proxiedUri = new AtomicReference<>();
        try (LocalJwksServer proxyServer = new LocalJwksServer(exchange -> {
            proxiedUri.set(exchange.getRequestURI());
            sendJson(exchange, jwksJson(rsaJwkJson("proxied", publicKey)));
        })) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyServer.address());
            URL jwksUrl = URI.create("http://issuer.example/.well-known/jwks.json").toURL();
            JwkProvider provider = new JwkProviderBuilder(jwksUrl)
                    .proxied(proxy)
                    .cached(false)
                    .rateLimited(false)
                    .timeouts(500, 500)
                    .build();

            Jwk jwk = provider.get("proxied");

            assertThat(jwk.getPublicKey().getEncoded()).isEqualTo(publicKey.getEncoded());
            assertThat(proxiedUri.get()).isEqualTo(jwksUrl.toURI());
            assertThat(proxyServer.requests()).isEqualTo(1);
        }
    }

    @Test
    void validatesProviderConstructionArguments() throws Exception {
        assertThatIllegalStateException()
                .isThrownBy(() -> new JwkProviderBuilder((URL) null))
                .withMessageContaining("without url");
        assertThatIllegalStateException()
                .isThrownBy(() -> new JwkProviderBuilder((String) null))
                .withMessageContaining("without domain");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UrlJwkProvider((URL) null))
                .withMessageContaining("non-null url");

        URL exampleUrl = URI.create("http://127.0.0.1/.well-known/jwks.json").toURL();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UrlJwkProvider(exampleUrl, -1, 100))
                .withMessageContaining("Invalid connect timeout");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UrlJwkProvider(exampleUrl, 100, -1))
                .withMessageContaining("Invalid read timeout");
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        return generator.generateKeyPair();
    }

    private static KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String rsaJwkJson(String kid, RSAPublicKey publicKey) {
        return """
                {
                  "kid": "%s",
                  "kty": "RSA",
                  "alg": "RS256",
                  "use": "sig",
                  "n": "%s",
                  "e": "%s"
                }
                """.formatted(kid, base64Url(publicKey.getModulus()), base64Url(publicKey.getPublicExponent()));
    }

    private static String jwksJson(String... keys) {
        return "{\"keys\":[" + String.join(",", keys) + "]}";
    }

    private static String base64Url(BigInteger value) {
        return BASE64_URL.encodeToString(toUnsignedBytes(value));
    }

    private static String base64Url(BigInteger value, int length) {
        byte[] unsigned = toUnsignedBytes(value);
        if (unsigned.length == length) {
            return BASE64_URL.encodeToString(unsigned);
        }
        byte[] padded = new byte[length];
        System.arraycopy(unsigned, 0, padded, length - unsigned.length, unsigned.length);
        return BASE64_URL.encodeToString(padded);
    }

    private static byte[] toUnsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] unsigned = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
            return unsigned;
        }
        return bytes;
    }

    private static void sendJson(HttpExchange exchange, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(payload);
        }
    }

    private static final class LocalJwksServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicInteger requests = new AtomicInteger();

        private LocalJwksServer(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                handler.handle(exchange);
            });
            executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
            server.setExecutor(executor);
            server.start();
        }

        private URL url() throws IOException {
            return URI.create("http://%s:%d/.well-known/jwks.json"
                            .formatted(server.getAddress().getHostString(), server.getAddress().getPort()))
                    .toURL();
        }

        private InetSocketAddress address() {
            return server.getAddress();
        }

        private int requests() {
            return requests.get();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "jwks-rsa-test-server");
            thread.setDaemon(true);
            return thread;
        }
    }
}
