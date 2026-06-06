/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_addons_sasl;

import com.google.protobuf.ByteString;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.sasl.OciAuthProviderCallback;
import com.oracle.bmc.auth.sasl.OciLoginModule;
import com.oracle.bmc.auth.sasl.OciMechanism;
import com.oracle.bmc.auth.sasl.OciSaslClient;
import com.oracle.bmc.auth.sasl.OciSaslClientProvider;
import com.oracle.bmc.auth.sasl.UserPrincipalsLoginModule;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Challenge;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Key;
import com.oracle.bmc.identity.auth.sasl.messages.OciSaslMessages.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Oci_java_sdk_addons_saslTest {
    private static final String INTENT = "stream-pool-example";
    private static final String KEY_ID = "ocid1.tenancy.example/ocid1.user.example/00:11:22:33";
    private static final TestAuthenticationDetailsProvider AUTH_PROVIDER =
            new TestAuthenticationDetailsProvider();

    private static final String PRIVATE_KEY =
            """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC5ZbEolH32qzvv
            4hC44EioasT7G8tnUzGJivZsss2JvBIQNp68OAUq42Yj5FagO28Qsxj7xnVLX9zE
            z2orNGaA5fjyhSN2W6JuAb+Gw3dsQEGvU9bPG2yF/VtUcrlHIoIaF21jNThyKM6t
            ZFxLAa/Vkhowgp+Ya81dTwQQWKAj983Vh199TbOK4H2MY7efY2L4//7FtxBXvZN5
            9R2z3NJlZYn6AatSIUA3NLOh4Wr23A7rFLI/diUfJtJzCXyK2sxQ/HbL8eBF0m/o
            QaqPsUz3+45fPQIU+G8vQrx5FXh6b5VLdH4+u+bh+D3ngdEhWWH+k2lNIJNoSX29
            Kp649KmFAgMBAAECggEABsUVaXRg4nk4dfcZUFUloEmkBmvOEkwKvJexVMuuObUb
            ufi59HprpeLUv8M3bpH9BdCkZ8tvMCfdFecolYn2v83CcaUZnNl5iSe09uvtpnKD
            Hi6/jYcrf/u23O87g22u72j4erR7BvV6gwEKEGP+RGNSgCqIeAu169U5tBjhRXYC
            SWrjoL7EpDbITrYIIHPg+RKf1cDbYSKo/GbK5swJ8qAkXuRfiGLc2RTT5eEnDEYB
            RY7AisrYpce4eHx/qIoOr7Yo/kvPoNx59rnq+LT6VqsGaOD90FhkLDvdgIpBU4qs
            ssfeqBAHVqBNQQCNBSy57RmqvcFDiC7wChkL2qgOGQKBgQD8EkCMUds6ZtZHoz8H
            RPiYWPmmyaIHZFIOOdQu/TXPhZNjs3SbWeVgPtiyoAe+VL4Vu3ceHb2hrTthu5hl
            KzR5MUSKyy/rpVukAJwV6vYrP3pHmiubKQBtv14Ttw62U+YdkRgNR4Jx8khWnWWO
            pXF3JEUHc+6hdPPhEvrjDi8LFwKBgQC8SWouR3SnjrHjvN4RYs7cit0AlRl0SC++
            sFUFIHStfFl4v6kptGr0NMBDqM6dxBHTuH3nW8QUOGxsfJ8TFCgbc3KfYn48xie2
            zzK5I0xWdgL6zhH+WUAM7GH9RXaw49tJ7h+GxZYvEmKWEF2PDxrox4W3avzkIHmB
            7fBu/KDhwwKBgFlZJuQWB701wnJ2HNs4yV1G/IbwJay1FJjSSrG1MyOx10KZ05VV
            UgbBgBIgw13lr9MINfrI0/TA8LF9y1Dg9aKXohIIX54JaOhDBxCFzmgaAf/rV9WR
            vQMpt/EJC//40TJdwcbDOCunk5iQBfWAEy4F/ynbIBS+3ctD7QYaevZVAoGBAKp+
            j/d4HNndDJf73dpAbrClzZlJadMxyooRgesyiHTPtPTyGGv7Qx3+5lpd8TpK4Rmm
            Zdup7NOihAt4jVLpXaf90j0hIs5FYJDY7kVXATkJg4k4MHLwvl71bYW58NAcg+Ve
            Co4vPN9C9qKMDywLsYI3qY19A6JPBbfxjVXyzHuhAoGBAJ628OVKJdTQtVnjSTI1
            /+KrmEBiQNtaxiZK5h3X/UGA5hBusbX0/QXfIo9YWww7KVthwAS5sh1eQM52j/My
            KaOegno38CA9fipXRgBnxm2hl48K7ScRBhS9UtxsRQoQrBd76bgwYbH3hAhYO5qW
            UXl0aU0VLL9FHbIC2kr3s5kQ
            -----END PRIVATE KEY-----
            """;

    @Test
    void exposesSupportedOciMechanism() {
        String mechanismName = OciMechanism.OCI_RSA_SHA256.mechanismName();

        assertThat(mechanismName).isEqualTo("OCI-RSA-SHA256");
        assertThat(OciMechanism.fromMechanismName(mechanismName))
                .isSameAs(OciMechanism.OCI_RSA_SHA256);
        assertThat(OciMechanism.isOci(mechanismName)).isTrue();
        assertThat(OciMechanism.isOci("PLAIN")).isFalse();
        assertThat(OciMechanism.mechanismNames()).containsExactly(mechanismName);
    }

    @Test
    void storesAuthenticationProviderInCallback() {
        OciAuthProviderCallback callback = new OciAuthProviderCallback();

        assertThat(callback.authProvider()).isNull();
        callback.authProvider(AUTH_PROVIDER);

        assertThat(callback.authProvider()).isSameAs(AUTH_PROVIDER);
    }

    @Test
    void registersProviderAndCompletesChallengeResponseHandshake() throws Exception {
        OciSaslClientProvider.initialize();

        SaslClient client = createClient(authProviderCallbackHandler(AUTH_PROVIDER));

        assertThat(client).isNotNull();
        assertThat(client.getMechanismName()).isEqualTo(OciMechanism.OCI_RSA_SHA256.mechanismName());
        assertThat(client.hasInitialResponse()).isTrue();
        assertThat(client.isComplete()).isFalse();
        assertThat(client.wrap(new byte[] {1, 2, 3}, 0, 3)).isEmpty();
        assertThat(client.unwrap(new byte[] {1, 2, 3}, 0, 3)).isEmpty();
        assertThat(client.getNegotiatedProperty("qop")).isNull();

        Key key = Key.parseFrom(client.evaluateChallenge(new byte[0]));
        assertThat(key.getKeyId()).isEqualTo(KEY_ID);
        assertThat(key.getIntent()).isEqualTo(INTENT);

        long before = Instant.now().minusSeconds(1).getEpochSecond();
        Response response = Response.parseFrom(client.evaluateChallenge(serializedChallenge()));
        long after = Instant.now().plusSeconds(1).getEpochSecond();

        assertThat(response.getTime()).isBetween(before, after);
        assertThat(response.getSignature().isEmpty()).isFalse();
        assertThat(client.isComplete()).isTrue();
        assertThat(client.evaluateChallenge(serializedChallenge())).isEmpty();

        client.dispose();
        boolean providerRegistered =
                Arrays.stream(Security.getProviders())
                        .anyMatch(provider -> provider.getName().contains("OCI"));
        assertThat(providerRegistered).isTrue();
    }

    @Test
    void loginModulePopulatesSubjectCredentialsUsableByPasswordCallback() throws Exception {
        TestLoginModule loginModule = new TestLoginModule();
        Subject subject = new Subject();

        loginModule.initialize(subject, null, Map.of(), Map.of("intent", INTENT));

        assertThat(subject.getPublicCredentials(String.class)).contains(INTENT);
        assertThat(subject.getPrivateCredentials(BasicAuthenticationDetailsProvider.class))
                .contains(AUTH_PROVIDER);
        assertThat(subject.getPrivateCredentials(String.class)).hasSize(1);

        SaslClient client = createClient(subjectPasswordCallbackHandler(subject));
        Key key = Key.parseFrom(client.evaluateChallenge(new byte[0]));

        assertThat(key.getKeyId()).isEqualTo(KEY_ID);
        assertThat(key.getIntent()).isEqualTo(INTENT);
    }

    @Test
    void userPrincipalsLoginModuleLoadsConfiguredProfile(@TempDir Path tempDir) throws Exception {
        Path privateKeyFile = tempDir.resolve("oci_api_key.pem");
        Path configFile = tempDir.resolve("config");
        Files.writeString(privateKeyFile, PRIVATE_KEY, StandardCharsets.UTF_8);
        Files.writeString(
                configFile,
                """
                [TEST]
                user=ocid1.user.example
                fingerprint=00:11:22:33
                tenancy=ocid1.tenancy.example
                region=us-phoenix-1
                key_file=%s
                """
                        .formatted(privateKeyFile),
                StandardCharsets.UTF_8);
        UserPrincipalsLoginModule loginModule = new UserPrincipalsLoginModule();
        Subject subject = new Subject();

        loginModule.initialize(
                subject,
                null,
                Map.of(),
                Map.of("intent", INTENT, "config", configFile.toString(), "profile", "TEST"));

        assertThat(subject.getPublicCredentials(String.class)).contains(INTENT);
        assertThat(subject.getPrivateCredentials(BasicAuthenticationDetailsProvider.class))
                .hasSize(1);

        SaslClient client = createClient(subjectPasswordCallbackHandler(subject));
        Key key = Key.parseFrom(client.evaluateChallenge(new byte[0]));

        assertThat(key.getKeyId()).isEqualTo(KEY_ID);
        assertThat(key.getIntent()).isEqualTo(INTENT);
    }

    @Test
    void rejectsMissingIntentUnsupportedMechanismsAndInvalidChallenges() throws Exception {
        TestLoginModule loginModule = new TestLoginModule();

        assertThatThrownBy(() -> loginModule.initialize(new Subject(), null, Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Intent is required.");

        OciSaslClient.OciSaslClientFactory factory = new OciSaslClient.OciSaslClientFactory();
        assertThat(factory.getMechanismNames(Map.of()))
                .containsExactly(OciMechanism.OCI_RSA_SHA256.mechanismName());
        assertThatThrownBy(
                        () ->
                                factory.createSaslClient(
                                        new String[] {"PLAIN"},
                                        null,
                                        "oci",
                                        "localhost",
                                        Map.of(),
                                        authProviderCallbackHandler(AUTH_PROVIDER)))
                .isInstanceOf(SaslException.class)
                .hasMessageContaining("not supported");

        SaslClient client = createClient(authProviderCallbackHandler(AUTH_PROVIDER));
        client.evaluateChallenge(new byte[0]);

        assertThatThrownBy(() -> client.evaluateChallenge(Challenge.newBuilder().build().toByteArray()))
                .isInstanceOf(SaslException.class)
                .hasMessageContaining("right size");
    }

    private static SaslClient createClient(CallbackHandler callbackHandler) throws SaslException {
        OciSaslClientProvider.initialize();
        return Sasl.createSaslClient(
                new String[] {OciMechanism.OCI_RSA_SHA256.mechanismName()},
                null,
                "oci",
                "localhost",
                Map.of(),
                callbackHandler);
    }

    private static CallbackHandler authProviderCallbackHandler(
            BasicAuthenticationDetailsProvider authProvider) {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(INTENT);
                } else if (callback instanceof OciAuthProviderCallback) {
                    ((OciAuthProviderCallback) callback).authProvider(authProvider);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };
    }

    private static CallbackHandler subjectPasswordCallbackHandler(Subject subject) {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    Set<String> publicCredentials = subject.getPublicCredentials(String.class);
                    ((NameCallback) callback).setName(publicCredentials.iterator().next());
                } else if (callback instanceof PasswordCallback) {
                    Set<String> privateCredentials = subject.getPrivateCredentials(String.class);
                    ((PasswordCallback) callback)
                            .setPassword(privateCredentials.iterator().next().toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };
    }

    private static byte[] serializedChallenge() {
        byte[] challenge = new byte[OciSaslClient.MIN_CHALLENGE_SIZE];
        Arrays.fill(challenge, (byte) 7);
        return Challenge.newBuilder()
                .setChallenge(ByteString.copyFrom(challenge))
                .build()
                .toByteArray();
    }

    private static final class TestAuthenticationDetailsProvider
            implements BasicAuthenticationDetailsProvider {
        @Override
        public String getKeyId() {
            return KEY_ID;
        }

        @Override
        public InputStream getPrivateKey() {
            return new ByteArrayInputStream(PRIVATE_KEY.getBytes(StandardCharsets.UTF_8));
        }

        @SuppressWarnings("deprecation")
        @Override
        public String getPassPhrase() {
            return null;
        }

        @Override
        public char[] getPassphraseCharacters() {
            return null;
        }
    }

    private static final class TestLoginModule extends OciLoginModule {
        @Override
        protected BasicAuthenticationDetailsProvider loadAuthenticationProvider(
                Map<String, ?> options) {
            return AUTH_PROVIDER;
        }
    }
}
