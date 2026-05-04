/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ongres_scram.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongres.scram.common.ScramAttributeValue;
import com.ongres.scram.common.ScramAttributes;
import com.ongres.scram.common.ScramFunctions;
import com.ongres.scram.common.ScramMechanism;
import com.ongres.scram.common.ScramMechanisms;
import com.ongres.scram.common.ScramStringFormatting;
import com.ongres.scram.common.exception.ScramParseException;
import com.ongres.scram.common.gssapi.Gs2AttributeValue;
import com.ongres.scram.common.gssapi.Gs2Attributes;
import com.ongres.scram.common.gssapi.Gs2CbindFlag;
import com.ongres.scram.common.gssapi.Gs2Header;
import com.ongres.scram.common.message.ClientFinalMessage;
import com.ongres.scram.common.message.ClientFirstMessage;
import com.ongres.scram.common.message.ServerFinalMessage;
import com.ongres.scram.common.message.ServerFirstMessage;
import com.ongres.scram.common.stringprep.StringPreparation;
import com.ongres.scram.common.stringprep.StringPreparations;
import com.ongres.scram.common.util.CryptoUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

public class CommonTest {
    private static final byte[] RFC5802_SALT = Base64.getDecoder().decode("QSXCR+Q6sek8bf92");
    private static final String RFC5802_CLIENT_NONCE = "fyko+d2lbbFgONRv9qkxdawL";
    private static final String RFC5802_SERVER_NONCE = "3rfcNHYJY1ZVvWVs7j";
    private static final String RFC5802_COMBINED_NONCE = RFC5802_CLIENT_NONCE + RFC5802_SERVER_NONCE;
    private static final String RFC5802_CLIENT_FIRST_BARE = "n=user,r=" + RFC5802_CLIENT_NONCE;
    private static final String RFC5802_SERVER_FIRST = "r=" + RFC5802_COMBINED_NONCE + ",s=QSXCR+Q6sek8bf92,i=4096";
    private static final String RFC5802_CLIENT_FINAL_WITHOUT_PROOF = "c=biws,r=" + RFC5802_COMBINED_NONCE;
    private static final String RFC5802_AUTH_MESSAGE = RFC5802_CLIENT_FIRST_BARE + "," + RFC5802_SERVER_FIRST + ","
            + RFC5802_CLIENT_FINAL_WITHOUT_PROOF;

    @Test
    void selectsMechanismsByNameChannelBindingAndPriority() {
        assertThat(ScramMechanisms.SCRAM_SHA_1.getName()).isEqualTo("SCRAM-SHA-1");
        assertThat(ScramMechanisms.SCRAM_SHA_1_PLUS.getName()).isEqualTo("SCRAM-SHA-1-PLUS");
        assertThat(ScramMechanisms.SCRAM_SHA_256.getName()).isEqualTo("SCRAM-SHA-256");
        assertThat(ScramMechanisms.SCRAM_SHA_256_PLUS.getName()).isEqualTo("SCRAM-SHA-256-PLUS");

        assertThat(ScramMechanisms.SCRAM_SHA_1.algorithmKeyLength()).isEqualTo(160);
        assertThat(ScramMechanisms.SCRAM_SHA_256.algorithmKeyLength()).isEqualTo(256);
        assertThat(ScramMechanisms.SCRAM_SHA_1.supportsChannelBinding()).isFalse();
        assertThat(ScramMechanisms.SCRAM_SHA_256_PLUS.supportsChannelBinding()).isTrue();

        assertThat(ScramMechanisms.byName("SCRAM-SHA-256")).isSameAs(ScramMechanisms.SCRAM_SHA_256);
        assertThat(ScramMechanisms.byName("unknown-mechanism")).isNull();

        ScramMechanism selectedWithoutChannelBinding = ScramMechanisms.selectMatchingMechanism(false,
                "SCRAM-SHA-1", "SCRAM-SHA-256", "unknown");
        ScramMechanism selectedWithChannelBinding = ScramMechanisms.selectMatchingMechanism(true,
                "SCRAM-SHA-1-PLUS", "SCRAM-SHA-256-PLUS");

        assertThat(selectedWithoutChannelBinding).isSameAs(ScramMechanisms.SCRAM_SHA_256);
        assertThat(selectedWithChannelBinding).isSameAs(ScramMechanisms.SCRAM_SHA_256_PLUS);
        assertThat(ScramMechanisms.selectMatchingMechanism(true, "SCRAM-SHA-256")).isNull();
    }

    @Test
    void hashesAndHmacsMatchJdkImplementations() throws Exception {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] key = "secret".getBytes(StandardCharsets.UTF_8);

        assertThat(ScramMechanisms.SCRAM_SHA_1.digest(data))
                .isEqualTo(MessageDigest.getInstance("SHA-1").digest(data));
        assertThat(ScramMechanisms.SCRAM_SHA_256.digest(data))
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(data));
        assertThat(ScramFunctions.hash(ScramMechanisms.SCRAM_SHA_256, data))
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(data));

        assertThat(ScramMechanisms.SCRAM_SHA_1.hmac(key, data))
                .isEqualTo(hmac("HmacSHA1", key, data));
        assertThat(ScramMechanisms.SCRAM_SHA_256.hmac(key, data))
                .isEqualTo(hmac("HmacSHA256", key, data));
        assertThat(ScramFunctions.hmac(ScramMechanisms.SCRAM_SHA_256, data, key))
                .isEqualTo(hmac("HmacSHA256", key, data));
    }

    @Test
    void derivesRfc5802ScramSha1ProofAndServerSignature() {
        byte[] saltedPassword = ScramFunctions.saltedPassword(ScramMechanisms.SCRAM_SHA_1,
                StringPreparations.NO_PREPARATION, "pencil", RFC5802_SALT, 4096);
        byte[] clientKey = ScramFunctions.clientKey(ScramMechanisms.SCRAM_SHA_1, saltedPassword);
        byte[] storedKey = ScramFunctions.storedKey(ScramMechanisms.SCRAM_SHA_1, clientKey);
        byte[] clientSignature = ScramFunctions.clientSignature(ScramMechanisms.SCRAM_SHA_1, storedKey,
                RFC5802_AUTH_MESSAGE);
        byte[] clientProof = ScramFunctions.clientProof(clientKey, clientSignature);
        byte[] serverKey = ScramFunctions.serverKey(ScramMechanisms.SCRAM_SHA_1, saltedPassword);
        byte[] serverSignature = ScramFunctions.serverSignature(ScramMechanisms.SCRAM_SHA_1, serverKey,
                RFC5802_AUTH_MESSAGE);

        assertThat(ScramStringFormatting.base64Encode(clientProof)).isEqualTo("v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=");
        assertThat(ScramStringFormatting.base64Encode(serverSignature)).isEqualTo("rmF9pqV8S7suAoZWja4dJRkFsKQ=");
        assertThat(ScramFunctions.verifyClientProof(ScramMechanisms.SCRAM_SHA_1, clientProof, storedKey,
                RFC5802_AUTH_MESSAGE)).isTrue();
        assertThat(ScramFunctions.verifyServerSignature(ScramMechanisms.SCRAM_SHA_1, serverKey,
                RFC5802_AUTH_MESSAGE, serverSignature)).isTrue();

        byte[] tamperedProof = clientProof.clone();
        tamperedProof[0] ^= 1;
        assertThat(ScramFunctions.verifyClientProof(ScramMechanisms.SCRAM_SHA_1, tamperedProof, storedKey,
                RFC5802_AUTH_MESSAGE)).isFalse();
    }

    @Test
    void derivesSaltedPasswordsForSha1AndSha256() {
        byte[] salt = "NaCl".getBytes(StandardCharsets.UTF_8);

        assertThat(ScramMechanisms.SCRAM_SHA_1.saltedPassword(StringPreparations.NO_PREPARATION,
                "password", salt, 4096))
                .isEqualTo(Base64.getDecoder().decode("UUdr21KOejviPohS5PBxfRSzuKA="));
        assertThat(ScramMechanisms.SCRAM_SHA_256.saltedPassword(StringPreparations.NO_PREPARATION,
                "password", salt, 4096))
                .isEqualTo(Base64.getDecoder().decode("FGgSaancNV2YcsRMPqKQo2n4BLT9Ky9xx747ItvVuJg="));
    }

    @Test
    void appliesCustomStringPreparationWhenDerivingKeys() {
        byte[] salt = "pepper".getBytes(StandardCharsets.UTF_8);
        StringPreparation customPreparation = value -> "prepared-" + value;
        byte[] preparedSaltedPassword = ScramFunctions.saltedPassword(ScramMechanisms.SCRAM_SHA_256,
                StringPreparations.NO_PREPARATION, "prepared-secret", salt, 4096);

        assertThat(ScramFunctions.saltedPassword(ScramMechanisms.SCRAM_SHA_256, customPreparation,
                "secret", salt, 4096)).isEqualTo(preparedSaltedPassword);
        assertThat(ScramFunctions.clientKey(ScramMechanisms.SCRAM_SHA_256, customPreparation,
                "secret", salt, 4096))
                .isEqualTo(ScramFunctions.clientKey(ScramMechanisms.SCRAM_SHA_256, preparedSaltedPassword));
        assertThat(ScramFunctions.serverKey(ScramMechanisms.SCRAM_SHA_256, customPreparation,
                "secret", salt, 4096))
                .isEqualTo(ScramFunctions.serverKey(ScramMechanisms.SCRAM_SHA_256, preparedSaltedPassword));
    }

    @Test
    void formatsAndParsesClientFirstMessagesWithGs2Header() throws Exception {
        ClientFirstMessage simple = new ClientFirstMessage("user", RFC5802_CLIENT_NONCE);

        assertThat(simple.toString()).isEqualTo("n,," + RFC5802_CLIENT_FIRST_BARE);
        assertThat(simple.writeToWithoutGs2Header(new java.lang.StringBuffer()).toString()).isEqualTo(RFC5802_CLIENT_FIRST_BARE);

        ClientFirstMessage parsed = ClientFirstMessage.parseFrom(simple.toString());
        assertThat(parsed.getChannelBindingFlag()).isSameAs(Gs2CbindFlag.CLIENT_NOT);
        assertThat(parsed.isChannelBinding()).isFalse();
        assertThat(parsed.getAuthzid()).isNull();
        assertThat(parsed.getUser()).isEqualTo("user");
        assertThat(parsed.getNonce()).isEqualTo(RFC5802_CLIENT_NONCE);

        ClientFirstMessage withChannelBinding = new ClientFirstMessage(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED,
                "authzid", "tls-server-end-point", "user,name", "nonce");
        assertThat(withChannelBinding.toString())
                .isEqualTo("p=tls-server-end-point,a=authzid,n=user=2Cname,r=nonce");
        assertThat(withChannelBinding.isChannelBinding()).isTrue();
        assertThat(withChannelBinding.getChannelBindingName()).isEqualTo("tls-server-end-point");
        assertThat(withChannelBinding.getAuthzid()).isEqualTo("authzid");
    }

    @Test
    void rejectsMalformedClientFirstMessages() {
        assertThatThrownBy(() -> ClientFirstMessage.parseFrom("n,,r=nonce,n=user"))
                .isInstanceOf(ScramParseException.class)
                .hasMessageContaining("user must be the 3rd element");
        assertThatThrownBy(() -> new ClientFirstMessage(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED, null,
                null, "user", "nonce"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Channel binding name is required");
        assertThatThrownBy(() -> new ClientFirstMessage("", "nonce"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user");
    }

    @Test
    void formatsAndParsesServerFirstMessages() throws Exception {
        ServerFirstMessage message = new ServerFirstMessage(RFC5802_CLIENT_NONCE, RFC5802_SERVER_NONCE,
                "QSXCR+Q6sek8bf92", ServerFirstMessage.ITERATION_MIN_VALUE);

        assertThat(message.getClientNonce()).isEqualTo(RFC5802_CLIENT_NONCE);
        assertThat(message.getServerNonce()).isEqualTo(RFC5802_SERVER_NONCE);
        assertThat(message.getNonce()).isEqualTo(RFC5802_COMBINED_NONCE);
        assertThat(message.getSalt()).isEqualTo("QSXCR+Q6sek8bf92");
        assertThat(message.getIteration()).isEqualTo(4096);
        assertThat(message.toString()).isEqualTo(RFC5802_SERVER_FIRST);

        ServerFirstMessage parsed = ServerFirstMessage.parseFrom(RFC5802_SERVER_FIRST, RFC5802_CLIENT_NONCE);
        assertThat(parsed.getServerNonce()).isEqualTo(RFC5802_SERVER_NONCE);
        assertThat(parsed.toString()).isEqualTo(RFC5802_SERVER_FIRST);
    }

    @Test
    void rejectsMalformedServerFirstMessages() {
        assertThatThrownBy(() -> new ServerFirstMessage("client", "server", "salt", 4095))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iteration must be >= 4096");
        assertThatThrownBy(() -> ServerFirstMessage.parseFrom("r=wrong,s=salt,i=4096", "client"))
                .isInstanceOf(ScramParseException.class)
                .hasMessageContaining("does not start");
        assertThatThrownBy(() -> ServerFirstMessage.parseFrom("r=clientserver,s=salt,i=NaN", "client"))
                .isInstanceOf(ScramParseException.class)
                .hasMessageContaining("invalid iteration");
    }

    @Test
    void formatsClientFinalMessagesWithAndWithoutProof() {
        Gs2Header header = new Gs2Header(Gs2CbindFlag.CLIENT_NOT);
        byte[] proof = Base64.getDecoder().decode("v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=");
        ClientFinalMessage message = new ClientFinalMessage(header, null, RFC5802_COMBINED_NONCE, proof);

        assertThat(ClientFinalMessage.writeToWithoutProof(header, null, RFC5802_COMBINED_NONCE).toString())
                .isEqualTo(RFC5802_CLIENT_FINAL_WITHOUT_PROOF);
        assertThat(message.toString()).isEqualTo(RFC5802_CLIENT_FINAL_WITHOUT_PROOF
                + ",p=v0X8v3Bz2T0CJGbJQyF0X+HI4Ts=");

        Gs2Header channelBindingHeader = new Gs2Header(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED,
                "tls-server-end-point");
        ClientFinalMessage withChannelBindingData = new ClientFinalMessage(channelBindingHeader,
                "tls-exported".getBytes(StandardCharsets.UTF_8), "nonce", new byte[] {1, 2, 3});
        assertThat(withChannelBindingData.toString())
                .isEqualTo("c=cD10bHMtc2VydmVyLWVuZC1wb2ludCwsYz1kR3h6TFdWNGNHOXlkR1Zr,r=nonce,p=AQID");
    }

    @Test
    void formatsAndParsesServerFinalMessages() throws Exception {
        byte[] verifier = Base64.getDecoder().decode("rmF9pqV8S7suAoZWja4dJRkFsKQ=");
        ServerFinalMessage verifierMessage = new ServerFinalMessage(verifier);

        assertThat(verifierMessage.isError()).isFalse();
        assertThat(verifierMessage.getVerifier()).isEqualTo(verifier);
        assertThat(verifierMessage.getError()).isNull();
        assertThat(verifierMessage.toString()).isEqualTo("v=rmF9pqV8S7suAoZWja4dJRkFsKQ=");
        assertThat(ServerFinalMessage.parseFrom(verifierMessage.toString()).getVerifier()).isEqualTo(verifier);

        ServerFinalMessage errorMessage = new ServerFinalMessage(ServerFinalMessage.Error.INVALID_PROOF);
        assertThat(errorMessage.isError()).isTrue();
        assertThat(errorMessage.getError()).isSameAs(ServerFinalMessage.Error.INVALID_PROOF);
        assertThat(errorMessage.toString()).isEqualTo("e=invalid-proof");
        assertThat(ServerFinalMessage.parseFrom(errorMessage.toString()).getError())
                .isSameAs(ServerFinalMessage.Error.INVALID_PROOF);
    }

    @Test
    void mapsServerFinalErrorsByProtocolName() {
        for (ServerFinalMessage.Error error : ServerFinalMessage.Error.values()) {
            assertThat(ServerFinalMessage.Error.getByErrorMessage(error.getErrorMessage())).isSameAs(error);
            assertThat(error.getErrorMessage()).matches("[a-z-]+");
        }

        assertThatThrownBy(() -> ServerFinalMessage.Error.getByErrorMessage("not-a-scram-error"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid error message");
    }

    @Test
    void formatsParsesAndValidatesGs2Headers() {
        Gs2Header noChannelBinding = new Gs2Header(Gs2CbindFlag.CLIENT_NOT);
        Gs2Header clientSupportsBinding = new Gs2Header(Gs2CbindFlag.CLIENT_YES_SERVER_NOT);
        Gs2Header requiredBinding = new Gs2Header(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED,
                "tls-server-end-point", "authzid");

        assertThat(noChannelBinding.toString()).isEqualTo("n,");
        assertThat(clientSupportsBinding.toString()).isEqualTo("y,");
        assertThat(requiredBinding.toString()).isEqualTo("p=tls-server-end-point,a=authzid");
        assertThat(Gs2Header.parseFrom("p=tls-server-end-point,a=authzid,n=user,r=nonce")
                .getChannelBindingName()).isEqualTo("tls-server-end-point");
        assertThat(Gs2Header.parseFrom("p=tls-server-end-point,a=authzid,n=user,r=nonce")
                .getAuthzid()).isEqualTo("authzid");

        assertThatThrownBy(() -> new Gs2Header(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Specify channel binding flag and value together");
        assertThatThrownBy(() -> Gs2Header.parseFrom("a=authzid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsScramAndGs2AttributesByCharacter() throws Exception {
        assertThat(ScramAttributes.byChar('n')).isSameAs(ScramAttributes.USERNAME);
        assertThat(ScramAttributes.byChar('r')).isSameAs(ScramAttributes.NONCE);
        assertThat(ScramAttributes.byChar('p')).isSameAs(ScramAttributes.CLIENT_PROOF);
        assertThat(ScramAttributes.byChar('v')).isSameAs(ScramAttributes.SERVER_SIGNATURE);
        assertThat(ScramAttributes.byChar('e')).isSameAs(ScramAttributes.ERROR);
        assertThat(Gs2CbindFlag.byChar('n')).isSameAs(Gs2CbindFlag.CLIENT_NOT);
        assertThat(Gs2CbindFlag.byChar('y')).isSameAs(Gs2CbindFlag.CLIENT_YES_SERVER_NOT);
        assertThat(Gs2CbindFlag.byChar('p')).isSameAs(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED);
        assertThat(Gs2Attributes.byGS2CbindFlag(Gs2CbindFlag.CHANNEL_BINDING_REQUIRED))
                .isSameAs(Gs2Attributes.CHANNEL_BINDING_REQUIRED);

        assertThatThrownBy(() -> ScramAttributes.byChar('x')).isInstanceOf(ScramParseException.class);
        assertThatThrownBy(() -> Gs2CbindFlag.byChar('x')).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesAndWritesAttributeValues() throws Exception {
        ScramAttributeValue nonce = new ScramAttributeValue(ScramAttributes.NONCE, "abc123");
        ScramAttributeValue parsed = ScramAttributeValue.parse("r=abc123");

        assertThat(nonce.getChar()).isEqualTo('r');
        assertThat(nonce.getValue()).isEqualTo("abc123");
        assertThat(nonce.toString()).isEqualTo("r=abc123");
        assertThat(parsed.getChar()).isEqualTo('r');
        assertThat(parsed.getValue()).isEqualTo("abc123");
        assertThat(ScramAttributeValue.writeTo(new java.lang.StringBuffer(), ScramAttributes.USERNAME, "user").toString())
                .isEqualTo("n=user");

        assertThatThrownBy(() -> ScramAttributeValue.parse("abc123"))
                .isInstanceOf(ScramParseException.class);
    }

    @Test
    void parsesAndWritesGs2AttributeValues() {
        Gs2AttributeValue noChannelBinding = new Gs2AttributeValue(Gs2Attributes.CLIENT_NOT, null);
        Gs2AttributeValue channelBinding = Gs2AttributeValue.parse("p=tls-server-end-point");

        assertThat(noChannelBinding.getChar()).isEqualTo('n');
        assertThat(noChannelBinding.getValue()).isNull();
        assertThat(noChannelBinding.toString()).isEqualTo("n");
        assertThat(Gs2AttributeValue.parse("n").getValue()).isNull();
        assertThat(channelBinding.getChar()).isEqualTo('p');
        assertThat(channelBinding.getValue()).isEqualTo("tls-server-end-point");
        assertThat(channelBinding.toString()).isEqualTo("p=tls-server-end-point");
        assertThat(Gs2AttributeValue.writeTo(new java.lang.StringBuffer(), Gs2Attributes.AUTHZID, "authzid").toString())
                .isEqualTo("a=authzid");
        assertThat(Gs2AttributeValue.parse(null)).isNull();

        assertThatThrownBy(() -> Gs2AttributeValue.parse("n="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Gs2AttributeValue");
        assertThatThrownBy(() -> Gs2AttributeValue.parse("x=value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid GS2Attribute character");
        assertThatThrownBy(() -> new Gs2AttributeValue(Gs2Attributes.AUTHZID, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value should be either null or non-empty");
    }

    @Test
    void formatsSaslNamesAndBase64Values() {
        assertThat(ScramStringFormatting.toSaslName("user,name=one")).isEqualTo("user=2Cname=3Done");
        assertThat(ScramStringFormatting.fromSaslName("user=2Cname=3Done")).isEqualTo("user,name=one");
        assertThat(ScramStringFormatting.toSaslName("plain-user")).isEqualTo("plain-user");
        assertThat(ScramStringFormatting.toSaslName(null)).isNull();
        assertThat(ScramStringFormatting.fromSaslName("")).isEmpty();

        assertThat(ScramStringFormatting.base64Encode(new byte[] {1, 2, 3, 4})).isEqualTo("AQIDBA==");
        assertThat(ScramStringFormatting.base64Encode("n,,")).isEqualTo("biws");
        assertThat(ScramStringFormatting.base64Decode("AQIDBA==")).containsExactly((byte) 1, (byte) 2, (byte) 3,
                (byte) 4);

        assertThatThrownBy(() -> ScramStringFormatting.fromSaslName("bad,name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ','");
        assertThatThrownBy(() -> ScramStringFormatting.fromSaslName("bad=XX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid char");
        assertThatThrownBy(() -> ScramStringFormatting.base64Decode("!not-base64!"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void appliesStringPreparationStrategies() {
        assertThat(StringPreparations.NO_PREPARATION.normalize(" I\tX\n")).isEqualTo("IX");
        assertThatThrownBy(() -> StringPreparations.NO_PREPARATION.normalize("I\u00ADX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non US-ASCII");
        assertThat(StringPreparations.SASL_PREPARATION.normalize("I\u00ADX")).isEqualTo("IX");
        assertThat(StringPreparations.SASL_PREPARATION.normalize("\u00AA")).isEqualTo("a");

        assertThatThrownBy(() -> StringPreparations.SASL_PREPARATION.normalize("bad\u0007"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesPrintableNoncesAndXorsEqualLengthArrays() {
        String nonce = CryptoUtil.nonce(128, new CyclingSecureRandom());

        assertThat(nonce).hasSize(128);
        for (int i = 0; i < nonce.length(); i++) {
            int codePoint = nonce.charAt(i);
            assertThat(codePoint).isBetween(33, 126);
            assertThat(codePoint).isNotEqualTo((int) ',');
        }
        assertThat(CryptoUtil.xor(new byte[] {0x0F, 0x33}, new byte[] {(byte) 0xF0, 0x55}))
                .containsExactly((byte) 0xFF, (byte) 0x66);

        assertThatThrownBy(() -> CryptoUtil.nonce(0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Size must be positive");
        assertThatThrownBy(() -> CryptoUtil.xor(new byte[] {1}, new byte[] {1, 2}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same length");
    }

    private static byte[] hmac(String algorithm, byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        return mac.doFinal(data);
    }

    private static final class CyclingSecureRandom extends SecureRandom {
        private static final long serialVersionUID = 1L;
        private static final int[] VALUES = {11, 0, 93};

        private int index;

        @Override
        public int nextInt(int bound) {
            int value = VALUES[index % VALUES.length];
            index++;
            return value % bound;
        }
    }
}

