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
import com.oracle.bmc.auth.sasl.OciAuthProviderCallback;
import com.oracle.bmc.auth.sasl.OciMechanism;
import com.oracle.bmc.auth.sasl.OciSaslClient;
import com.oracle.bmc.auth.sasl.OciSaslClientProvider;
import com.oracle.bmc.auth.sasl.UserPrincipalsLoginModule;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;
import java.util.Map;
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
    @Test
    void ociMechanismMapsItsProtocolNameAndSigningAlgorithm() {
        OciMechanism mechanism = OciMechanism.OCI_RSA_SHA256;

        assertThat(mechanism.mechanismName()).isEqualTo("OCI-RSA-SHA256");
        assertThat(OciMechanism.fromMechanismName(mechanism.mechanismName())).isSameAs(mechanism);
        assertThat(OciMechanism.mechanismNames()).containsExactly(mechanism.mechanismName());
        assertThat(OciMechanism.isOci(mechanism.mechanismName())).isTrue();
        assertThat(OciMechanism.isOci("PLAIN")).isFalse();
        assertThat(mechanism.algorithm().getJvmName()).isEqualTo("SHA256withRSA");
    }

    @Test
    void userPrincipalsLoginModuleLoadsConfiguredCredentialsIntoSubject(@TempDir Path tempDirectory)
            throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        BasicAuthenticationDetailsProvider configuredProvider = new TestAuthenticationProvider(keyPair);
        Path privateKeyFile = tempDirectory.resolve("oci_api_key.pem");
        try (InputStream privateKey = configuredProvider.getPrivateKey()) {
            Files.write(privateKeyFile, privateKey.readAllBytes());
        }

        Path configurationFile = tempDirectory.resolve("config");
        Files.writeString(
                configurationFile,
                """
                [SASL]
                user=ocid1.user.oc1..test
                fingerprint=fingerprint
                tenancy=ocid1.tenancy.oc1..test
                region=us-ashburn-1
                key_file=%s
                """
                        .formatted(privateKeyFile));

        Subject subject = new Subject();
        UserPrincipalsLoginModule loginModule = new UserPrincipalsLoginModule();
        loginModule.initialize(
                subject,
                null,
                Map.of(),
                Map.of("intent", "database", "config", configurationFile.toString(), "profile", "SASL"));

        assertThat(loginModule.login()).isTrue();
        assertThat(loginModule.commit()).isTrue();
        assertThat(subject.getPublicCredentials()).contains("database");
        assertThat(subject.getPrivateCredentials(BasicAuthenticationDetailsProvider.class))
                .singleElement()
                .satisfies(
                        provider ->
                                assertThat(provider.getKeyId())
                                        .isEqualTo(
                                                "ocid1.tenancy.oc1..test/ocid1.user.oc1..test/fingerprint"));
    }

    @Test
    void registeredProviderCreatesAndCompletesAnOciSaslExchange() throws Exception {
        OciSaslClientProvider.initialize();
        assertThat(Security.getProvider("SASL/OCI Client Provider")).isNotNull();

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        BasicAuthenticationDetailsProvider provider = new TestAuthenticationProvider(keyPair);
        SaslClient client =
                Sasl.createSaslClient(
                        new String[] {OciMechanism.OCI_RSA_SHA256.mechanismName()},
                        null,
                        "database",
                        "service.example.com",
                        Map.of(),
                        new AuthenticationCallbackHandler(provider));

        assertThat(client).isNotNull();
        assertThat(client.getMechanismName()).isEqualTo(OciMechanism.OCI_RSA_SHA256.mechanismName());
        assertThat(client.hasInitialResponse()).isTrue();
        assertThat(client.isComplete()).isFalse();

        OciSaslMessages.Key key = OciSaslMessages.Key.parseFrom(client.evaluateChallenge(new byte[0]));
        assertThat(key.getKeyId()).isEqualTo("ocid1.user.oc1..test/fingerprint");
        assertThat(key.getIntent()).isEqualTo("database");

        byte[] challenge = new byte[OciSaslClient.MIN_CHALLENGE_SIZE];
        for (int index = 0; index < challenge.length; index++) {
            challenge[index] = (byte) index;
        }
        byte[] response =
                client.evaluateChallenge(
                        OciSaslMessages.Challenge.newBuilder()
                                .setChallenge(ByteString.copyFrom(challenge))
                                .build()
                                .toByteArray());

        OciSaslMessages.Response signedResponse = OciSaslMessages.Response.parseFrom(response);
        assertThat(signedResponse.getSignature()).isNotEmpty();
        assertThat(signedResponse.getTime()).isPositive();
        assertThat(client.isComplete()).isTrue();
        assertThat(client.wrap(new byte[] {1}, 0, 1)).isEmpty();
        assertThat(client.unwrap(new byte[] {1}, 0, 1)).isEmpty();
        assertThat(client.getNegotiatedProperty(Sasl.QOP)).isNull();
        client.dispose();
    }

    private static final class AuthenticationCallbackHandler implements CallbackHandler {
        private final BasicAuthenticationDetailsProvider provider;

        private AuthenticationCallbackHandler(BasicAuthenticationDetailsProvider provider) {
            this.provider = provider;
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    nameCallback.setName("database");
                } else if (callback instanceof OciAuthProviderCallback authProviderCallback) {
                    authProviderCallback.authProvider(provider);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }

    private static final class TestAuthenticationProvider implements BasicAuthenticationDetailsProvider {
        private final String privateKeyPem;

        private TestAuthenticationProvider(KeyPair keyPair) {
            privateKeyPem =
                    "-----BEGIN PRIVATE KEY-----\n"
                            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                                    .encodeToString(keyPair.getPrivate().getEncoded())
                            + "\n-----END PRIVATE KEY-----\n";
        }

        @Override
        public String getKeyId() {
            return "ocid1.user.oc1..test/fingerprint";
        }

        @Override
        public InputStream getPrivateKey() {
            return new ByteArrayInputStream(privateKeyPem.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
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
