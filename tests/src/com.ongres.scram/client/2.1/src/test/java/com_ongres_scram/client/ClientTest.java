/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ongres_scram.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongres.scram.client.NonceSupplier;
import com.ongres.scram.client.ScramClient;
import com.ongres.scram.client.ScramSession;
import com.ongres.scram.common.ScramFunctions;
import com.ongres.scram.common.ScramMechanisms;
import com.ongres.scram.common.exception.ScramInvalidServerSignatureException;
import com.ongres.scram.common.exception.ScramParseException;
import com.ongres.scram.common.exception.ScramServerErrorException;
import com.ongres.scram.common.gssapi.Gs2CbindFlag;
import com.ongres.scram.common.message.ServerFinalMessage;
import com.ongres.scram.common.stringprep.StringPreparation;
import com.ongres.scram.common.stringprep.StringPreparations;
import java.security.Provider;
import java.security.Security;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class ClientTest {
    private static final String RFC5802_CLIENT_NONCE = "fyko+d2lbbFgONRv9qkxdawL";
    private static final String RFC5802_SERVER_NONCE = "3rfcNHYJY1ZVvWVs7j";
    private static final String RFC5802_COMBINED_NONCE = RFC5802_CLIENT_NONCE + RFC5802_SERVER_NONCE;
    private static final String RFC5802_SERVER_FIRST = "r=" + RFC5802_COMBINED_NONCE + ",s=QSXCR+Q6sek8bf92,i=4096";
    private static final String RFC5802_SERVER_FINAL = "v=rmF9pqV8S7suAoZWja4dJRkFsKQ=";
    private static final String RFC7677_CLIENT_NONCE = "rOprNGfwEbeRWgbNEkqO";
    private static final String RFC7677_SERVER_NONCE = "%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0";
    private static final String RFC7677_COMBINED_NONCE = RFC7677_CLIENT_NONCE + RFC7677_SERVER_NONCE;
    private static final String RFC7677_SERVER_FIRST = "r=" + RFC7677_COMBINED_NONCE
            + ",s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096";
    private static final String RFC7677_SERVER_FINAL = "v=6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=";

    @Test
    void advertisesSupportedMechanismsAndMapsChannelBindingModes() {
        assertThat(ScramClient.supportedMechanisms()).containsExactly(
                "SCRAM-SHA-1",
                "SCRAM-SHA-1-PLUS",
                "SCRAM-SHA-256",
                "SCRAM-SHA-256-PLUS");

        assertThat(ScramClient.ChannelBinding.NO.gs2CbindFlag()).isSameAs(Gs2CbindFlag.CLIENT_NOT);
        assertThat(ScramClient.ChannelBinding.YES.gs2CbindFlag())
                .isSameAs(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED);
        assertThat(ScramClient.ChannelBinding.IF_SERVER_SUPPORTS_IT.gs2CbindFlag())
                .isSameAs(Gs2CbindFlag.CLIENT_YES_SERVER_NOT);
    }

    @Test
    void buildsClientWithExplicitMechanismAndCustomNonceSupplier() {
        NonceSupplier nonceSupplier = () -> "fixedNonce";
        ScramClient client = ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_256)
                .nonceSupplier(nonceSupplier)
                .setup();

        assertThat(client.getScramMechanism()).isSameAs(ScramMechanisms.SCRAM_SHA_256);
        assertThat(client.getStringPreparation()).isSameAs(StringPreparations.NO_PREPARATION);
        assertThat(client.scramSession("user,name=one").clientFirstMessage())
                .isEqualTo("n,,n=user=2Cname=3Done,r=fixedNonce");
    }

    @Test
    void generatesConfiguredLengthPrintableNonceWhenNoSupplierIsProvided() {
        ScramClient client = ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_1)
                .nonceLength(12)
                .setup();

        String message = client.scramSession("user").clientFirstMessage();
        assertThat(message).startsWith("n,,n=user,r=");

        String nonce = message.substring("n,,n=user,r=".length());
        assertThat(nonce).hasSize(12);
        for (int i = 0; i < nonce.length(); i++) {
            assertThat((int) nonce.charAt(i)).isBetween(33, 126);
            assertThat(nonce.charAt(i)).isNotEqualTo(',');
        }
    }

    @Test
    void usesConfiguredSecureRandomAlgorithmProviderForGeneratedNonces() {
        Provider selectedProvider = null;
        Provider.Service selectedService = null;
        for (Provider provider : Security.getProviders()) {
            for (Provider.Service service : provider.getServices()) {
                if ("SecureRandom".equals(service.getType())) {
                    selectedProvider = provider;
                    selectedService = service;
                    if ("SHA1PRNG".equals(service.getAlgorithm())) {
                        break;
                    }
                }
            }
            if (selectedService != null && "SHA1PRNG".equals(selectedService.getAlgorithm())) {
                break;
            }
        }
        assertThat(selectedProvider).as("a SecureRandom provider is available").isNotNull();
        assertThat(selectedService).as("a SecureRandom algorithm is available").isNotNull();

        ScramClient client = ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_1)
                .secureRandomAlgorithmProvider(selectedService.getAlgorithm(), selectedProvider.getName())
                .nonceLength(10)
                .setup();

        String message = client.scramSession("user").clientFirstMessage();

        assertThat(message).startsWith("n,,n=user,r=");
        assertThat(message.substring("n,,n=user,r=".length())).hasSize(10);
    }

    @Test
    void selectsBestMechanismFromServerAdvertisements() {
        ScramClient noChannelBindingClient = ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectMechanismBasedOnServerAdvertised("SCRAM-SHA-1", "SCRAM-SHA-256", "UNKNOWN")
                .nonceSupplier(() -> "nonce")
                .setup();
        ScramClient channelBindingClient = ScramClient.channelBinding(ScramClient.ChannelBinding.YES)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectMechanismBasedOnServerAdvertised("SCRAM-SHA-256-PLUS")
                .nonceSupplier(() -> "nonce")
                .setup();
        ScramClient opportunisticPlusClient = ScramClient
                .channelBinding(ScramClient.ChannelBinding.IF_SERVER_SUPPORTS_IT)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectMechanismBasedOnServerAdvertisedCsv("SCRAM-SHA-256-PLUS")
                .nonceSupplier(() -> "nonce")
                .setup();
        ScramClient opportunisticPlainClient = ScramClient
                .channelBinding(ScramClient.ChannelBinding.IF_SERVER_SUPPORTS_IT)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectMechanismBasedOnServerAdvertised("SCRAM-SHA-1", "SCRAM-SHA-256")
                .nonceSupplier(() -> "nonce")
                .setup();

        assertThat(noChannelBindingClient.getScramMechanism()).isSameAs(ScramMechanisms.SCRAM_SHA_256);
        assertThat(channelBindingClient.getScramMechanism()).isSameAs(ScramMechanisms.SCRAM_SHA_256_PLUS);
        assertThat(opportunisticPlusClient.getScramMechanism()).isSameAs(ScramMechanisms.SCRAM_SHA_256_PLUS);
        assertThat(opportunisticPlainClient.getScramMechanism()).isSameAs(ScramMechanisms.SCRAM_SHA_256);
    }

    @Test
    void rejectsInvalidBuilderSelections() {
        assertThatThrownBy(() -> ScramClient.channelBinding(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channelBinding");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.NO).stringPreparation(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stringPreparation");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scramMechanism");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_256_PLUS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incompatible selection");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.YES)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_256))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incompatible selection");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.IF_SERVER_SUPPORTS_IT)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_256))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no direct client selection");
    }

    @Test
    void rejectsInvalidServerAdvertisementsAndNonceConfiguration() {
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectMechanismBasedOnServerAdvertised())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serverMechanisms");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectMechanismBasedOnServerAdvertised("SCRAM-SHA-256-PLUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non channel binding");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.YES)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectMechanismBasedOnServerAdvertised("SCRAM-SHA-256"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel binding");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.IF_SERVER_SUPPORTS_IT)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectMechanismBasedOnServerAdvertised("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no matching mechanisms");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_256)
                .nonceSupplier(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonceSupplier");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_256)
                .nonceLength(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length");
        assertThatThrownBy(() -> ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_256)
                .secureRandomAlgorithmProvider("no-such-algorithm", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid algorithm or provider");
    }

    @Test
    void completesRfc5802ScramSha1ClientExchange() throws Exception {
        ScramSession session = rfc5802Session();

        String clientFirstMessage = session.clientFirstMessage();
        ScramSession.ServerFirstProcessor serverFirstProcessor = session.receiveServerFirstMessage(
                RFC5802_SERVER_FIRST);
        ScramSession.ClientFinalProcessor clientFinalProcessor = serverFirstProcessor.clientFinalProcessor("pencil");

        assertThat(clientFirstMessage).isEqualTo("n,,n=user,r=" + RFC5802_CLIENT_NONCE);
        assertThat(serverFirstProcessor.getSalt()).isEqualTo("QSXCR+Q6sek8bf92");
        assertThat(serverFirstProcessor.getIteration()).isEqualTo(4096);
        assertThat(clientFinalProcessor.clientFinalMessage()).isEqualTo(
                "c=biws,r=" + RFC5802_COMBINED_NONCE + ",p=v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=");
        assertThatCode(() -> clientFinalProcessor.receiveServerFinalMessage(RFC5802_SERVER_FINAL))
                .doesNotThrowAnyException();
    }

    @Test
    void completesRfc7677ScramSha256ClientExchange() throws Exception {
        ScramSession session = ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_256)
                .nonceSupplier(() -> RFC7677_CLIENT_NONCE)
                .setup()
                .scramSession("user");

        String clientFirstMessage = session.clientFirstMessage();
        ScramSession.ClientFinalProcessor clientFinalProcessor = session
                .receiveServerFirstMessage(RFC7677_SERVER_FIRST)
                .clientFinalProcessor("pencil");

        assertThat(clientFirstMessage).isEqualTo("n,,n=user,r=" + RFC7677_CLIENT_NONCE);
        assertThat(clientFinalProcessor.clientFinalMessage()).isEqualTo(
                "c=biws,r=" + RFC7677_COMBINED_NONCE + ",p=dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=");
        assertThatCode(() -> clientFinalProcessor.receiveServerFinalMessage(RFC7677_SERVER_FINAL))
                .doesNotThrowAnyException();
    }

    @Test
    void completesExchangeWithPrecomputedKeys() throws Exception {
        ScramSession session = rfc5802Session();
        session.clientFirstMessage();
        ScramSession.ServerFirstProcessor serverFirstProcessor = session.receiveServerFirstMessage(
                RFC5802_SERVER_FIRST);
        byte[] saltedPassword = ScramFunctions.saltedPassword(ScramMechanisms.SCRAM_SHA_1,
                StringPreparations.NO_PREPARATION, "pencil", Base64.getDecoder().decode("QSXCR+Q6sek8bf92"), 4096);
        byte[] clientKey = ScramFunctions.clientKey(ScramMechanisms.SCRAM_SHA_1, saltedPassword);
        byte[] serverKey = ScramFunctions.serverKey(ScramMechanisms.SCRAM_SHA_1, saltedPassword);

        ScramSession.ClientFinalProcessor clientFinalProcessor = serverFirstProcessor.clientFinalProcessor(
                clientKey, serverKey);

        assertThat(clientFinalProcessor.clientFinalMessage()).isEqualTo(
                "c=biws,r=" + RFC5802_COMBINED_NONCE + ",p=v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=");
        assertThatCode(() -> clientFinalProcessor.receiveServerFinalMessage(RFC5802_SERVER_FINAL))
                .doesNotThrowAnyException();
    }

    @Test
    void formatsChannelBindingClientFirstMessage() throws Exception {
        ScramSession session = new ScramSession(ScramMechanisms.SCRAM_SHA_256_PLUS,
                StringPreparations.NO_PREPARATION, "user,name", "clientNonce");
        String clientFirstMessage = session.clientFirstMessage(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED,
                "tls-server-end-point", "authorization-id");

        assertThat(clientFirstMessage).isEqualTo(
                "p=tls-server-end-point,a=authorization-id,n=user=2Cname,r=clientNonce");

        ScramSession.ClientFinalProcessor clientFinalProcessor = session
                .receiveServerFirstMessage("r=clientNonceserverNonce,s=U29tZVNhbHQ=,i=4096")
                .clientFinalProcessor("secret");
        assertThat(clientFinalProcessor.clientFinalMessage())
                .startsWith("c=cD10bHMtc2VydmVyLWVuZC1wb2ludCxhPWF1dGhvcml6YXRpb24taWQs," +
                        "r=clientNonceserverNonce,p=");
    }

    @Test
    void appliesConfiguredStringPreparationBeforeDerivingProof() throws Exception {
        StringPreparation preparation = value -> value.replace("-", "");
        ScramClient preparedClient = ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(preparation)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_1)
                .nonceSupplier(() -> RFC5802_CLIENT_NONCE)
                .setup();

        ScramSession session = preparedClient.scramSession("user");
        session.clientFirstMessage();
        String clientFinalMessage = session.receiveServerFirstMessage(RFC5802_SERVER_FIRST)
                .clientFinalProcessor("pen-cil")
                .clientFinalMessage();

        assertThat(clientFinalMessage).isEqualTo(
                "c=biws,r=" + RFC5802_COMBINED_NONCE + ",p=v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=");
    }

    @Test
    void rejectsInvalidSessionFlowInputs() {
        ScramClient client = ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_1)
                .nonceSupplier(() -> RFC5802_CLIENT_NONCE)
                .setup();

        assertThatThrownBy(() -> client.scramSession(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user");
        assertThatThrownBy(() -> new ScramSession(ScramMechanisms.SCRAM_SHA_1,
                StringPreparations.NO_PREPARATION, "user", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonce");
        assertThatThrownBy(() -> client.scramSession("user").receiveServerFirstMessage(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serverFirstMessage");
        assertThatThrownBy(() -> {
            ScramSession session = client.scramSession("user");
            session.clientFirstMessage();
            session.receiveServerFirstMessage("r=wrong,s=QSXCR+Q6sek8bf92,i=4096");
        }).isInstanceOf(ScramParseException.class);
        assertThatThrownBy(() -> {
            ScramSession session = client.scramSession("user");
            session.clientFirstMessage();
            session.receiveServerFirstMessage(RFC5802_SERVER_FIRST).clientFinalProcessor("");
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
        assertThatThrownBy(() -> {
            ScramSession session = client.scramSession("user");
            session.clientFirstMessage();
            session.receiveServerFirstMessage(RFC5802_SERVER_FIRST).clientFinalProcessor(null, new byte[] {1});
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientKey");
    }

    @Test
    void rejectsServerFinalErrorInvalidSignatureAndMalformedFinalMessage() throws Exception {
        ScramSession.ClientFinalProcessor serverErrorProcessor = preparedRfc5802ClientFinalProcessor();
        serverErrorProcessor.clientFinalMessage();
        assertThatThrownBy(() -> serverErrorProcessor.receiveServerFinalMessage("e=invalid-proof"))
                .isInstanceOf(ScramServerErrorException.class)
                .satisfies(throwable -> assertThat(((ScramServerErrorException) throwable).getError())
                        .isSameAs(ServerFinalMessage.Error.INVALID_PROOF));

        ScramSession.ClientFinalProcessor invalidSignatureProcessor = preparedRfc5802ClientFinalProcessor();
        invalidSignatureProcessor.clientFinalMessage();
        assertThatThrownBy(() -> invalidSignatureProcessor.receiveServerFinalMessage("v=AAAAAAAAAAAAAAAAAAAAAAAAAAA="))
                .isInstanceOf(ScramInvalidServerSignatureException.class)
                .hasMessageContaining("Invalid server SCRAM signature");

        ScramSession.ClientFinalProcessor malformedProcessor = preparedRfc5802ClientFinalProcessor();
        malformedProcessor.clientFinalMessage();
        assertThatThrownBy(() -> malformedProcessor.receiveServerFinalMessage(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serverFinalMessage");
    }

    private static ScramSession rfc5802Session() {
        return ScramClient.channelBinding(ScramClient.ChannelBinding.NO)
                .stringPreparation(StringPreparations.NO_PREPARATION)
                .selectClientMechanism(ScramMechanisms.SCRAM_SHA_1)
                .nonceSupplier(() -> RFC5802_CLIENT_NONCE)
                .setup()
                .scramSession("user");
    }

    private static ScramSession.ClientFinalProcessor preparedRfc5802ClientFinalProcessor() throws Exception {
        ScramSession session = rfc5802Session();
        session.clientFirstMessage();
        return session.receiveServerFirstMessage(RFC5802_SERVER_FIRST).clientFinalProcessor("pencil");
    }
}
