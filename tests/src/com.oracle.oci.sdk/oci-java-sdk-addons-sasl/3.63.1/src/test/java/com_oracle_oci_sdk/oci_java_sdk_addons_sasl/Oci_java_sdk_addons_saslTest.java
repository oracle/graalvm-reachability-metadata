/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_addons_sasl;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.sasl.InstancePrincipalsLoginModule;
import com.oracle.bmc.auth.sasl.OciAuthProviderCallback;
import com.oracle.bmc.auth.sasl.OciLoginModule;
import com.oracle.bmc.auth.sasl.OciMechanism;
import com.oracle.bmc.auth.sasl.OciSaslClient;
import com.oracle.bmc.auth.sasl.OciSaslClientProvider;
import com.oracle.bmc.auth.sasl.UserPrincipalsLoginModule;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Challenge;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Key;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Response;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class Oci_java_sdk_addons_saslTest {
    private static final String INTENT = "stream-pool-message-endpoint";
    private static final String TENANCY_ID = "ocid1.tenancy.oc1..example";
    private static final String USER_ID = "ocid1.user.oc1..example";
    private static final String FINGERPRINT = "01:23:45:67:89:ab:cd:ef";

    @Test
    void registeredProviderCompletesAValidatedSignedSaslExchange() throws Exception {
        KeyPair keyPair = createRsaKeyPair();
        byte[] privateKey = encodePrivateKey(keyPair);
        BasicAuthenticationDetailsProvider authenticationProvider =
                SimpleAuthenticationDetailsProvider.builder()
                        .tenantId(TENANCY_ID)
                        .userId(USER_ID)
                        .fingerprint(FINGERPRINT)
                        .privateKeySupplier(new ByteArrayPrivateKeySupplier(privateKey))
                        .build();
        String keyId = authenticationProvider.getKeyId();
        OciMechanism mechanism = OciMechanism.OCI_RSA_SHA256;

        assertThat(mechanism.mechanismName()).isEqualTo("OCI-RSA-SHA256");
        assertThat(OciMechanism.fromMechanismName(mechanism.mechanismName())).isSameAs(mechanism);
        assertThat(OciMechanism.mechanismNames()).containsExactly(mechanism.mechanismName());
        assertThat(OciMechanism.isOci(mechanism.mechanismName())).isTrue();
        assertThat(OciMechanism.isOci("PLAIN")).isFalse();

        OciSaslClientProvider.initialize();
        SaslClient client =
                Sasl.createSaslClient(
                        new String[] {mechanism.mechanismName()},
                        null,
                        "oci",
                        "localhost",
                        Collections.emptyMap(),
                        new TestCallbackHandler(INTENT, authenticationProvider));

        assertThat(client).isInstanceOf(OciSaslClient.class);
        try {
            assertThat(client.hasInitialResponse()).isTrue();
            assertThat(client.isComplete()).isFalse();
            assertThat(client.getMechanismName()).isEqualTo(mechanism.mechanismName());

            Key key = Key.parseFrom(client.evaluateChallenge(new byte[0]));
            assertThat(key.getKeyId()).isEqualTo(keyId);
            assertThat(key.getIntent()).isEqualTo(INTENT);

            byte[] challengeBytes = new byte[64];
            Arrays.fill(challengeBytes, (byte) 0x5a);
            Challenge challenge =
                    Challenge.newBuilder()
                            .setChallenge(ByteString.copyFrom(challengeBytes))
                            .build();
            Response response =
                    Response.parseFrom(client.evaluateChallenge(challenge.toByteArray()));

            assertThat(response.getTime()).isPositive();
            assertThat(response.getSignature().size()).isGreaterThan(0);
            assertThat(client.isComplete()).isTrue();
            assertThat(verifySignature(keyPair, challengeBytes, response)).isTrue();
            assertThat(client.evaluateChallenge(new byte[0])).isEmpty();
        } finally {
            client.dispose();
        }
    }

    @Test
    void standardPasswordCallbackResolvesCachedAuthenticationProvider() throws Exception {
        KeyPair keyPair = createRsaKeyPair();
        byte[] privateKey = encodePrivateKey(keyPair);
        BasicAuthenticationDetailsProvider authenticationProvider =
                SimpleAuthenticationDetailsProvider.builder()
                        .tenantId(TENANCY_ID)
                        .userId(USER_ID)
                        .fingerprint(FINGERPRINT)
                        .privateKeySupplier(new ByteArrayPrivateKeySupplier(privateKey))
                        .build();
        Subject subject = new Subject();
        OciLoginModule loginModule = new TestLoginModule(authenticationProvider);
        loginModule.initialize(
                subject,
                null,
                Collections.emptyMap(),
                Collections.singletonMap("intent", INTENT));
        String cachedProviderKey =
                subject.getPrivateCredentials(String.class).iterator().next();

        SaslClient client =
                Sasl.createSaslClient(
                        new String[] {OciMechanism.OCI_RSA_SHA256.mechanismName()},
                        null,
                        "oci",
                        "localhost",
                        Collections.emptyMap(),
                        new StandardCallbackHandler(INTENT, cachedProviderKey));

        assertThat(client).isInstanceOf(OciSaslClient.class);
        try {
            Key key = Key.parseFrom(client.evaluateChallenge(new byte[0]));
            assertThat(key.getKeyId()).isEqualTo(authenticationProvider.getKeyId());
            assertThat(key.getIntent()).isEqualTo(INTENT);
        } finally {
            client.dispose();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void instancePrincipalsLoginModuleLoadsCredentialsFromMetadataService() throws Exception {
        try (InstanceMetadataServer metadataServer = new InstanceMetadataServer()) {
            Map<String, String> options = new HashMap<>();
            options.put("intent", INTENT);
            options.put("metadataBaseUrl", metadataServer.endpoint());
            Subject subject = new Subject();
            InstancePrincipalsLoginModule loginModule = new InstancePrincipalsLoginModule();

            loginModule.initialize(subject, null, Collections.emptyMap(), options);

            Set<InstancePrincipalsAuthenticationDetailsProvider> providers =
                    subject.getPrivateCredentials(
                            InstancePrincipalsAuthenticationDetailsProvider.class);
            assertThat(providers).hasSize(1);
            InstancePrincipalsAuthenticationDetailsProvider provider =
                    providers.iterator().next();
            assertThat(provider.getRegion()).isEqualTo(Region.US_PHOENIX_1);
            assertThat(metadataServer.requestedPaths())
                    .containsExactlyInAnyOrder(
                            "/opc/v2/instance/region",
                            "/opc/v2/identity/cert.pem",
                            "/opc/v2/identity/key.pem",
                            "/opc/v2/identity/intermediate.pem");
        }
    }

    @Test
    void userPrincipalsLoginModuleLoadsCredentialsFromConfiguration(@TempDir Path directory)
            throws Exception {
        KeyPair keyPair = createRsaKeyPair();
        byte[] privateKey = encodePrivateKey(keyPair);
        Path privateKeyFile = directory.resolve("oci-private-key.pem");
        Path configFile = directory.resolve("oci-config");
        Files.write(privateKeyFile, privateKey);
        Files.writeString(
                configFile,
                String.join(
                        System.lineSeparator(),
                        "[INTEGRATION]",
                        "tenancy=" + TENANCY_ID,
                        "user=" + USER_ID,
                        "fingerprint=" + FINGERPRINT,
                        "region=us-phoenix-1",
                        "key_file=" + privateKeyFile.toAbsolutePath(),
                        ""),
                StandardCharsets.UTF_8);

        Map<String, String> options = new HashMap<>();
        options.put("intent", INTENT);
        options.put("config", configFile.toAbsolutePath().toString());
        options.put("profile", "INTEGRATION");
        Subject subject = new Subject();
        UserPrincipalsLoginModule loginModule = new UserPrincipalsLoginModule();

        loginModule.initialize(subject, null, Collections.emptyMap(), options);

        assertThat(subject.getPublicCredentials(String.class)).containsExactly(INTENT);
        assertThat(subject.getPrivateCredentials(String.class)).hasSize(1);
        String cachedProviderKey =
                subject.getPrivateCredentials(String.class).iterator().next();
        assertThat(cachedProviderKey).isNotBlank();
        assertThat(subject.getPrivateCredentials(BasicAuthenticationDetailsProvider.class))
                .hasSize(1);
        BasicAuthenticationDetailsProvider loadedProvider =
                subject.getPrivateCredentials(BasicAuthenticationDetailsProvider.class)
                        .iterator()
                        .next();
        assertThat(loadedProvider.getKeyId())
                .contains(TENANCY_ID)
                .contains(USER_ID)
                .contains(FINGERPRINT);
        try (InputStream loadedPrivateKey = loadedProvider.getPrivateKey()) {
            assertThat(loadedPrivateKey.readAllBytes()).isEqualTo(privateKey);
        }
        assertThat(loginModule.login()).isTrue();
        assertThat(loginModule.commit()).isTrue();
        assertThat(loginModule.logout()).isTrue();
    }

    private static boolean verifySignature(
            KeyPair keyPair, byte[] challenge, Response response) throws Exception {
        byte[] intent = INTENT.getBytes(StandardCharsets.UTF_8);
        byte[] signedMessage =
                ByteBuffer.allocate(challenge.length + intent.length + Long.BYTES)
                        .put(challenge)
                        .put(intent)
                        .putLong(response.getTime())
                        .array();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(signedMessage);
        return verifier.verify(response.getSignature().toByteArray());
    }

    private static KeyPair createRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] encodePrivateKey(KeyPair keyPair) {
        String encoded =
                Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(keyPair.getPrivate().getEncoded());
        return ("-----BEGIN PRIVATE KEY-----\n"
                        + encoded
                        + "\n-----END PRIVATE KEY-----\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static final class InstanceMetadataServer implements AutoCloseable {
        private static final String CERTIFICATE =
                """
                -----BEGIN CERTIFICATE-----
                MIIDZzCCAk+gAwIBAgIUQNDAKyf0s5e1J77FLb1MkZTNv2QwDQYJKoZIhvcNAQEL
                BQAwQzERMA8GA1UEAwwIaW5zdGFuY2UxLjAsBgNVBAsMJW9wYy10ZW5hbnQ6b2Np
                ZDEudGVuYW5jeS5vYzEuLmV4YW1wbGUwHhcNMjYwODI5MDA1MjM2WhcNMzYwODI2
                MDA1MjM2WjBDMREwDwYDVQQDDAhpbnN0YW5jZTEuMCwGA1UECwwlb3BjLXRlbmFu
                dDpvY2lkMS50ZW5hbmN5Lm9jMS4uZXhhbXBsZTCCASIwDQYJKoZIhvcNAQEBBQAD
                ggEPADCCAQoCggEBAOwDmSustBPX2GCHYCt1gRBQO2wSc9gVjDLP+5cJ/p2s0TPA
                bj0DZDd1gR8UnIvyagd4AazRLIj3iIrdeHoejtxYuty72xkCmyWhc8icLuYWjfr0
                ktvH2n7uQMIsZ6+ha85ylBUnIBDm6moJgn2dWwM63IxTJ0LtHPXrJcGNVqUdSlrp
                zReiU3et5hiq+5ZM7bbmzH1fKPghIFecRxmOVlBM4a8sewxG4mFsaMB+dczAJI/6
                +ANTw4CL7ASGclca7Y/eJ9UCWL58kiUlQ6qUY+Gfq9H4Cz4HfJ/zjnaZ7uvp/kNM
                4utBf/IB4qZU1p8vLD0+K6d3/Xe3Dmvt+snDvIcCAwEAAaNTMFEwHQYDVR0OBBYE
                FKD903SKhoClfSglWt4bWITItL/OMB8GA1UdIwQYMBaAFKD903SKhoClfSglWt4b
                WITItL/OMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAG0SIzbL
                yhM2Y45DFoslmkx4Wx5fqbRMMgUfiR8qMwcMxPbe41/TB+VUY54a1EF6xArc3S37
                bKZXhfQO1QLzk6+R1TVZtQFRy4Of7o5/gzg3G/skqWrrAL+ta9d0y7pjAV2DPSUW
                9tDBupxrzy8Rt/txroE54hMzK8+yvNRsiBAyq3LhwhjMgtpxgGv0cXniZSil0Iej
                orhLdJ0vFS03Ri++bdy8D4TtLb/1EFmgYDx84wScDBK/IdTFZY99atZKGPDz8e22
                B06wTHfIvnwLrOIlh9bPZYYW+7bPWqQeWNKunS2Epv3TZ3zobjoJD7VQ42v9tNoR
                Yh4RdxFhJ9UoH4A=
                -----END CERTIFICATE-----
                """;
        private static final String PRIVATE_KEY =
                """
                -----BEGIN PRIVATE KEY-----
                MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDsA5krrLQT19hg
                h2ArdYEQUDtsEnPYFYwyz/uXCf6drNEzwG49A2Q3dYEfFJyL8moHeAGs0SyI94iK
                3Xh6Ho7cWLrcu9sZApsloXPInC7mFo369JLbx9p+7kDCLGevoWvOcpQVJyAQ5upq
                CYJ9nVsDOtyMUydC7Rz16yXBjValHUpa6c0XolN3reYYqvuWTO225sx9Xyj4ISBX
                nEcZjlZQTOGvLHsMRuJhbGjAfnXMwCSP+vgDU8OAi+wEhnJXGu2P3ifVAli+fJIl
                JUOqlGPhn6vR+As+B3yf8452me7r6f5DTOLrQX/yAeKmVNafLyw9Piund/13tw5r
                7frJw7yHAgMBAAECggEAVSA/IOeb1ARoQPjTERnCwXWO2T4Wlnu/I5ysrB+ovIpW
                sonXuR3+CZrSRbmTdU6sO6FnSUPxAj31+9BB4hofgQ5n57HCJtUvzHTdZCAhMlA0
                Sa3pQmhlQJ8CsIZ/p3NbhQ0CqFaCVFZVeoWPkWsuJo4Wem6LnLIVXgXAin1GISss
                Eqz6LgkV2aaZZ+Yeh7tZYcjMUd5RBgzOymFILXRYfsh5o/naBQDqpB12/e7e2VpT
                Fng8Vx4sT2OPkMOb6CZm+rtS64eGC93rA3+xw2Poq2cyIXBSSSE4FGO1LhhIf4Aa
                sEL1RjEMkNdN4eD5Bkn9LQlh0o48spk+aQgxOSgZwQKBgQD3MU1dGp6HAh1C1IUp
                etfNPyJu1zhiaQWHxTseBW6ekMCirH5ETqJfrcghoUFlSUy/SzDH/8BjmpKP/Ndc
                c4EczPuDzpg4x9pZ580y/zYIOnEgQfQqMDTZmHVTcKxVfWyCb6yS64m6jPNVcBse
                f1uc6oYOEkpmINBB3kHLNOq89wKBgQD0bFWUBQl5juyNMHE29UAiYiBxBw01BXA7
                ++KCF+ijB21Pnmv0sKh6appYdOJB1LpqAO+sHIA2L5OGeuydSeBOgdZBRIfeYieM
                oinv7elqMcOi4jA96qFvDKaF3Fi+wRLIx+PrIyZyF2gx1kMhroMDEDH3M6n4oJby
                /zrK9Ljo8QKBgDohigP/IpC1WpRAzh/3F5DY7AwM6OGbuQU4yLJCrMT1XZfj4L3H
                kD/X6lyeQ3bCSh4iXJJr/p5t11GtMCg5sX9IZU2V5A5WUW8bKBJ6GgbNV5UybW0O
                cR9KzyyULrLcEAyMnpTed0E7rG3HM1l0seKw9F2Sx5RE2zTiQ665/wn5AoGBANl9
                CE0UilDCz1P2ldbsNWwi2nEYcDUMqMiHIg7WTWe7dRXShocNJmz/LGWnY6hmLJWk
                TZ9dIOyWOvP/r0lp8hCJUWd0Hl3QAxcNOLnIfdjDfSwTjg+aFplkrUwRPFpIHHnR
                +8k/1rbQgyNNXyC6UtNH9t3a99RGuOpyFxN+3IZRAoGAYBb0st5PiZQUJBfIpQVC
                h8ljdtQiRNCNPSTPCBEIxY3sQZJwLX0vEBOa5kTisxYZKZJ+8+xtBCvxc1Bmelbb
                5UTXpS152mQMytAFZHrjXwVXS0wko9ON8fA0tPb4VZc+J+DCZzBUsgmPxkANTof4
                7k9vzl0SNucH4p4CtebnRJc=
                -----END PRIVATE KEY-----
                """;

        private final HttpServer server;
        private final ExecutorService executor;
        private final Set<String> requestedPaths = ConcurrentHashMap.newKeySet();

        private InstanceMetadataServer() throws IOException {
            InetSocketAddress address =
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            server = HttpServer.create(address, 0);
            executor =
                    Executors.newSingleThreadExecutor(
                            runnable -> {
                                Thread thread =
                                        new Thread(runnable, "oci-instance-metadata-test-server");
                                thread.setDaemon(true);
                                return thread;
                            });
            server.setExecutor(executor);
            server.createContext("/opc/v2/", this::handle);
            server.start();
        }

        private String endpoint() {
            InetAddress address = server.getAddress().getAddress();
            String host = address.getHostAddress();
            if (host.contains(":")) {
                host = "[" + host + "]";
            }
            return "http://" + host + ":" + server.getAddress().getPort() + "/opc/v2/";
        }

        private Set<String> requestedPaths() {
            return requestedPaths;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                requestedPaths.add(path);
                String response = responseFor(path);
                int status = response == null ? 404 : 200;
                if (!"GET".equals(exchange.getRequestMethod())
                        || !"Bearer Oracle"
                                .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    status = 401;
                    response = "Unauthorized";
                } else if (response == null) {
                    response = "Not found";
                }
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            }
        }

        private static String responseFor(String path) {
            switch (path) {
                case "/opc/v2/instance/region":
                    return "us-phoenix-1";
                case "/opc/v2/identity/cert.pem":
                case "/opc/v2/identity/intermediate.pem":
                    return CERTIFICATE;
                case "/opc/v2/identity/key.pem":
                    return PRIVATE_KEY;
                default:
                    return null;
            }
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class ByteArrayPrivateKeySupplier implements Supplier<InputStream> {
        private final byte[] privateKey;

        private ByteArrayPrivateKeySupplier(byte[] privateKey) {
            this.privateKey = privateKey.clone();
        }

        @Override
        public InputStream get() {
            return new ByteArrayInputStream(privateKey);
        }
    }

    private static final class TestLoginModule extends OciLoginModule {
        private final BasicAuthenticationDetailsProvider authenticationProvider;

        private TestLoginModule(BasicAuthenticationDetailsProvider authenticationProvider) {
            this.authenticationProvider = authenticationProvider;
        }

        @Override
        protected BasicAuthenticationDetailsProvider loadAuthenticationProvider(
                Map<String, ?> options) {
            return authenticationProvider;
        }
    }

    private static final class StandardCallbackHandler implements CallbackHandler {
        private final String intent;
        private final String cachedProviderKey;

        private StandardCallbackHandler(String intent, String cachedProviderKey) {
            this.intent = intent;
            this.cachedProviderKey = cachedProviderKey;
        }

        @Override
        public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(intent);
                } else if (callback instanceof PasswordCallback) {
                    ((PasswordCallback) callback).setPassword(cachedProviderKey.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }

    private static final class TestCallbackHandler implements CallbackHandler {
        private final String intent;
        private final BasicAuthenticationDetailsProvider authenticationProvider;

        private TestCallbackHandler(
                String intent, BasicAuthenticationDetailsProvider authenticationProvider) {
            this.intent = intent;
            this.authenticationProvider = authenticationProvider;
        }

        @Override
        public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(intent);
                } else if (callback instanceof OciAuthProviderCallback) {
                    ((OciAuthProviderCallback) callback)
                            .authProvider(authenticationProvider);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }
}
