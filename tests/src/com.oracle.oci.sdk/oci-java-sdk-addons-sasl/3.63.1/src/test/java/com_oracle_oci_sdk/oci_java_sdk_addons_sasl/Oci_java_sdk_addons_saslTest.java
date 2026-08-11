/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_addons_sasl;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.sasl.OciAuthProviderCallback;
import com.oracle.bmc.auth.sasl.OciMechanism;
import com.oracle.bmc.auth.sasl.OciSaslClientProvider;
import com.oracle.bmc.auth.sasl.UserPrincipalsLoginModule;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
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
    void createsOciSaslClientAndProducesInitialKeyExchangePayload() throws Exception {
        String mechanismName = OciMechanism.OCI_RSA_SHA256.mechanismName();
        String providerName = "SASL/OCI Client Provider";
        boolean providerWasRegistered = Security.getProvider(providerName) != null;

        OciSaslClientProvider.initialize();
        try {
            Provider provider = Security.getProvider(providerName);
            assertThat(provider).isNotNull();
            assertThat(provider.get("SaslClientFactory." + mechanismName)).isNotNull();

            SaslClient client =
                    Sasl.createSaslClient(
                            new String[] {"unsupported", mechanismName},
                            null,
                            "oci",
                            "example.test",
                            null,
                            callbackHandler("streaming", new TestAuthenticationProvider()));

            assertThat(client).isNotNull();
            assertThat(client.getMechanismName()).isEqualTo(mechanismName);
            assertThat(client.hasInitialResponse()).isTrue();
            assertThat(client.isComplete()).isFalse();
            assertThat(client.evaluateChallenge(new byte[0])).isNotEmpty();
            assertThat(client.unwrap(new byte[] {1, 2}, 0, 2)).isEmpty();
            assertThat(client.wrap(new byte[] {1, 2}, 0, 2)).isEmpty();
            assertThat(client.getNegotiatedProperty("qop")).isNull();
            client.dispose();
        } finally {
            if (!providerWasRegistered) {
                Security.removeProvider(providerName);
            }
        }
    }

    @Test
    void initializesUserPrincipalsLoginModuleFromConfiguredProfile(@TempDir Path temporaryDirectory)
            throws Exception {
        Path privateKey = temporaryDirectory.resolve("oci_api_key.pem");
        Path configFile = temporaryDirectory.resolve("oci_config");
        Files.writeString(privateKey, "key material is not read during login module initialization");
        Files.writeString(
                configFile,
                """
                [TEST]
                user=test-user
                fingerprint=test-fingerprint
                tenancy=test-tenancy
                region=us-ashburn-1
                key_file=%s
                """
                        .formatted(privateKey));

        String providerName = "SASL/OCI Client Provider";
        boolean providerWasRegistered = Security.getProvider(providerName) != null;
        try {
            Subject subject = new Subject();
            UserPrincipalsLoginModule loginModule = new UserPrincipalsLoginModule();
            loginModule.initialize(
                    subject,
                    null,
                    Map.of(),
                    Map.of("intent", "streaming", "config", configFile.toString(), "profile", "TEST"));

            assertThat(subject.getPublicCredentials()).contains("streaming");
            assertThat(subject.getPrivateCredentials(BasicAuthenticationDetailsProvider.class))
                    .singleElement()
                    .satisfies(
                            authenticationProvider ->
                                    assertThat(authenticationProvider.getKeyId())
                                            .contains("test-tenancy", "test-user", "test-fingerprint"));
            assertThat(loginModule.login()).isTrue();
            assertThat(loginModule.commit()).isTrue();
            assertThat(loginModule.abort()).isFalse();
            assertThat(loginModule.logout()).isTrue();
        } finally {
            if (!providerWasRegistered) {
                Security.removeProvider(providerName);
            }
        }
    }

    @Test
    void recognizesAndListsItsSupportedMechanism() {
        String mechanismName = OciMechanism.OCI_RSA_SHA256.mechanismName();

        assertThat(OciMechanism.fromMechanismName(mechanismName))
                .isEqualTo(OciMechanism.OCI_RSA_SHA256);
        assertThat(OciMechanism.isOci(mechanismName)).isTrue();
        assertThat(OciMechanism.isOci("unsupported")).isFalse();
        assertThat(OciMechanism.mechanismNames()).containsExactly(mechanismName);
    }

    private static CallbackHandler callbackHandler(
            String payload, BasicAuthenticationDetailsProvider authenticationProvider) {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(payload);
                } else if (callback instanceof OciAuthProviderCallback) {
                    ((OciAuthProviderCallback) callback).authProvider(authenticationProvider);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };
    }

    private static final class TestAuthenticationProvider
            implements BasicAuthenticationDetailsProvider {
        @Override
        public String getKeyId() {
            return "test-key-id";
        }

        @Override
        public ByteArrayInputStream getPrivateKey() {
            return new ByteArrayInputStream("not-used-before-challenge".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        @SuppressWarnings("deprecation")
        public String getPassPhrase() {
            return null;
        }

        @Override
        public char[] getPassphraseCharacters() {
            return null;
        }
    }
}
