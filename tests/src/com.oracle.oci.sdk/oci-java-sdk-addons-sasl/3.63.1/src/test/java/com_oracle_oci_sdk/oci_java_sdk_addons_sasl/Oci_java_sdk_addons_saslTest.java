/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_addons_sasl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.sasl.OciAuthProviderCallback;
import com.oracle.bmc.auth.sasl.OciMechanism;
import com.oracle.bmc.auth.sasl.OciSaslClient;
import com.oracle.bmc.auth.sasl.OciSaslClientProvider;
import com.oracle.bmc.auth.sasl.UserPrincipalsLoginModule;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Challenge;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Key;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Response;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Oci_java_sdk_addons_saslTest {
    @Test
    void createsClientThroughRegisteredProviderAndCompletesChallengeResponse() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        BasicAuthenticationDetailsProvider authProvider =
                new TestAuthenticationDetailsProvider(keyPairGenerator.generateKeyPair());
        CallbackHandler callbackHandler = callbackHandler(authProvider, "streaming");

        OciSaslClientProvider.initialize();
        SaslClient client = Sasl.createSaslClient(
                new String[] {OciMechanism.OCI_RSA_SHA256.mechanismName()},
                null,
                "kafka",
                "example.com",
                Collections.emptyMap(),
                callbackHandler);

        assertThat(client).isInstanceOf(OciSaslClient.class);
        assertThat(client.getMechanismName()).isEqualTo(OciMechanism.OCI_RSA_SHA256.mechanismName());
        assertThat(client.hasInitialResponse()).isTrue();
        assertThat(client.isComplete()).isFalse();

        Key key = Key.parseFrom(client.evaluateChallenge(new byte[0]));
        assertThat(key.getKeyId()).isEqualTo("test-key-id");
        assertThat(key.getIntent()).isEqualTo("streaming");

        byte[] challengeBytes = new byte[OciSaslClient.MIN_CHALLENGE_SIZE];
        for (int index = 0; index < challengeBytes.length; index++) {
            challengeBytes[index] = (byte) index;
        }
        Response response = Response.parseFrom(
                client.evaluateChallenge(Challenge.newBuilder()
                        .setChallenge(ByteString.copyFrom(challengeBytes))
                        .build()
                        .toByteArray()));
        assertThat(response.getTime()).isPositive();
        assertThat(response.getSignature().size()).isPositive();
        assertThat(client.isComplete()).isTrue();
        assertThat(client.evaluateChallenge(challengeBytes)).isEmpty();
        assertThat(client.wrap(new byte[] {1}, 0, 1)).isEmpty();
        assertThat(client.unwrap(new byte[] {1}, 0, 1)).isEmpty();
        assertThat(client.getNegotiatedProperty(Sasl.QOP)).isNull();
        client.dispose();
    }

    @Test
    void rejectsMalformedAndOutOfRangeServerChallenges() throws Exception {
        OciSaslClientProvider.initialize();
        SaslClient client = Sasl.createSaslClient(
                new String[] {OciMechanism.OCI_RSA_SHA256.mechanismName()},
                null,
                "kafka",
                "example.com",
                Collections.emptyMap(),
                callbackHandler(new TestAuthenticationDetailsProvider(newRsaKeyPair()), "intent"));
        client.evaluateChallenge(new byte[0]);

        assertThatThrownBy(() -> client.evaluateChallenge(new byte[] {1, 2, 3}))
                .isInstanceOf(SaslException.class);
        assertThatThrownBy(() -> client.evaluateChallenge(Challenge.newBuilder()
                .setChallenge(ByteString.copyFrom(new byte[OciSaslClient.MIN_CHALLENGE_SIZE - 1]))
                .build()
                .toByteArray())).isInstanceOf(SaslException.class);
        client.dispose();
    }

    @Test
    void initializesUserPrincipalsLoginModuleFromConfiguredCredentials(@TempDir Path temporaryDirectory)
            throws Exception {
        byte[] privateKey = privateKeyPem(newRsaKeyPair());
        Path privateKeyPath = temporaryDirectory.resolve("oci_api_key.pem");
        Files.write(privateKeyPath, privateKey);
        Path configPath = temporaryDirectory.resolve("config");
        Files.writeString(configPath, """
                [DEFAULT]
                user=ocid1.user.oc1..example
                fingerprint=aa:bb:cc:dd
                tenancy=ocid1.tenancy.oc1..example
                region=us-phoenix-1
                key_file=%s
                """.formatted(privateKeyPath), StandardCharsets.UTF_8);

        Subject subject = new Subject();
        UserPrincipalsLoginModule loginModule = new UserPrincipalsLoginModule();
        loginModule.initialize(
                subject,
                null,
                Collections.emptyMap(),
                Map.of("config", configPath.toString(), "profile", "DEFAULT", "intent", "streaming"));

        assertThat(subject.getPublicCredentials(String.class)).containsExactly("streaming");
        BasicAuthenticationDetailsProvider authProvider = subject
                .getPrivateCredentials(BasicAuthenticationDetailsProvider.class)
                .iterator()
                .next();
        assertThat(authProvider.getKeyId()).isNotBlank();
        try (InputStream configuredPrivateKey = authProvider.getPrivateKey()) {
            assertThat(configuredPrivateKey.readAllBytes()).isEqualTo(privateKey);
        }
        String authProviderCacheKey = subject.getPrivateCredentials(String.class).iterator().next();
        assertThat(authProviderCacheKey).isNotBlank();
        assertThat(loginModule.login()).isTrue();
        assertThat(loginModule.commit()).isTrue();
        assertThat(loginModule.abort()).isFalse();
        assertThat(loginModule.logout()).isTrue();
    }

    @Test
    void createsClientFromLoginModuleCredentialCache(@TempDir Path temporaryDirectory) throws Exception {
        byte[] privateKey = privateKeyPem(newRsaKeyPair());
        Path privateKeyPath = temporaryDirectory.resolve("oci_api_key.pem");
        Files.write(privateKeyPath, privateKey);
        Path configPath = temporaryDirectory.resolve("config");
        Files.writeString(configPath, """
                [DEFAULT]
                user=ocid1.user.oc1..example
                fingerprint=aa:bb:cc:dd
                tenancy=ocid1.tenancy.oc1..example
                region=us-phoenix-1
                key_file=%s
                """.formatted(privateKeyPath), StandardCharsets.UTF_8);

        Subject subject = new Subject();
        UserPrincipalsLoginModule loginModule = new UserPrincipalsLoginModule();
        loginModule.initialize(
                subject,
                null,
                Collections.emptyMap(),
                Map.of("config", configPath.toString(), "profile", "DEFAULT", "intent", "database"));
        String intent = subject.getPublicCredentials(String.class).iterator().next();
        String authProviderCacheKey = subject.getPrivateCredentials(String.class).iterator().next();
        CallbackHandler callbackHandler = callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(intent);
                } else if (callback instanceof PasswordCallback) {
                    ((PasswordCallback) callback).setPassword(authProviderCacheKey.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };

        SaslClient client = Sasl.createSaslClient(
                new String[] {OciMechanism.OCI_RSA_SHA256.mechanismName()},
                null,
                "kafka",
                "example.com",
                Collections.emptyMap(),
                callbackHandler);

        assertThat(client).isInstanceOf(OciSaslClient.class);
        Key key = Key.parseFrom(client.evaluateChallenge(new byte[0]));
        assertThat(key.getKeyId()).isNotBlank();
        assertThat(key.getIntent()).isEqualTo("database");
        client.dispose();
    }

    @Test
    void exposesMechanismsAndProtobufMessageRoundTrips() throws Exception {
        OciMechanism mechanism = OciMechanism.OCI_RSA_SHA256;
        assertThat(OciMechanism.mechanismNames()).containsExactly(mechanism.mechanismName());
        assertThat(OciMechanism.fromMechanismName(mechanism.mechanismName())).isSameAs(mechanism);
        assertThat(OciMechanism.isOci(mechanism.mechanismName())).isTrue();
        assertThat(OciMechanism.isOci("PLAIN")).isFalse();
        assertThat(OciMechanism.fromMechanismName("PLAIN")).isNull();
        assertThat(mechanism.algorithm()).isNotNull();

        Key key = Key.newBuilder().setKeyId("key").setIntent("database").build();
        Challenge challenge = Challenge.newBuilder()
                .setChallenge(ByteString.copyFromUtf8("server-challenge"))
                .build();
        Response response = Response.newBuilder()
                .setTime(123456789L)
                .setSignature(ByteString.copyFromUtf8("signature"))
                .build();

        assertThat(Key.parseFrom(key.toByteArray())).isEqualTo(key);
        assertThat(Challenge.parseFrom(challenge.toByteArray())).isEqualTo(challenge);
        assertThat(Response.parseFrom(response.toByteArray())).isEqualTo(response);
    }

    private static CallbackHandler callbackHandler(BasicAuthenticationDetailsProvider authProvider, String intent) {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(intent);
                } else if (callback instanceof OciAuthProviderCallback) {
                    ((OciAuthProviderCallback) callback).authProvider(authProvider);
                } else if (!(callback instanceof PasswordCallback)) {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };
    }

    private static byte[] privateKeyPem(KeyPair keyPair) {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return pem.getBytes(StandardCharsets.US_ASCII);
    }

    private static KeyPair newRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private static final class TestAuthenticationDetailsProvider
            implements BasicAuthenticationDetailsProvider {
        private final byte[] privateKey;

        private TestAuthenticationDetailsProvider(KeyPair keyPair) {
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                            .encodeToString(keyPair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            privateKey = pem.getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public String getKeyId() {
            return "test-key-id";
        }

        @Override
        public InputStream getPrivateKey() {
            return new ByteArrayInputStream(privateKey);
        }

        @Override
        public String getPassPhrase() {
            return null;
        }

        @Override
        public char[] getPassphraseCharacters() {
            return null;
        }
    }
}
