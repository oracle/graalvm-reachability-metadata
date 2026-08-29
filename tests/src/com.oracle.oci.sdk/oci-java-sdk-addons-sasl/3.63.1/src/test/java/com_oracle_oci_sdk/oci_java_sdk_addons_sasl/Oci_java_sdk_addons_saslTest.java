/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_addons_sasl;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.sasl.OciAuthProviderCallback;
import com.oracle.bmc.auth.sasl.OciMechanism;
import com.oracle.bmc.auth.sasl.OciSaslClient;
import com.oracle.bmc.auth.sasl.OciSaslClientProvider;
import com.oracle.bmc.auth.sasl.UserPrincipalsLoginModule;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Challenge;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Key;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
import java.util.function.Supplier;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import org.junit.jupiter.api.Test;
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
