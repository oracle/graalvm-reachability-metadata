/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_i2p_crypto.eddsa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.Utils;
import net.i2p.crypto.eddsa.spec.EdDSAGenParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;
import org.junit.jupiter.api.Test;

class EddsaTest {
    private static final byte[] RFC_8032_SEED =
            Utils.hexToBytes("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
    private static final byte[] RFC_8032_PUBLIC_KEY =
            Utils.hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
    private static final byte[] RFC_8032_SIGNATURE =
            Utils.hexToBytes(
                    "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                            + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");

    @Test
    void signsAndVerifiesTheRfc8032ReferenceVector() throws Exception {
        EdDSANamedCurveSpec curveSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        EdDSAPrivateKey privateKey = new EdDSAPrivateKey(new EdDSAPrivateKeySpec(RFC_8032_SEED, curveSpec));
        EdDSAPublicKey publicKey = new EdDSAPublicKey(new EdDSAPublicKeySpec(RFC_8032_PUBLIC_KEY, curveSpec));
        EdDSAEngine engine = new EdDSAEngine(MessageDigest.getInstance(curveSpec.getHashAlgorithm()));
        byte[] message = new byte[0];

        engine.initSign(privateKey);
        byte[] signature = engine.signOneShot(message);

        assertThat(signature).isEqualTo(RFC_8032_SIGNATURE);
        assertThat(privateKey.getSeed()).isEqualTo(RFC_8032_SEED);
        assertThat(privateKey.getAbyte()).isEqualTo(RFC_8032_PUBLIC_KEY);
        assertThat(publicKey.getAbyte()).isEqualTo(RFC_8032_PUBLIC_KEY);
        assertThat(privateKey.getParams()).isEqualTo(curveSpec);
        assertThat(publicKey.getParams()).isEqualTo(curveSpec);
        assertThat(privateKey.getAlgorithm()).isEqualTo("EdDSA");
        assertThat(publicKey.getAlgorithm()).isEqualTo("EdDSA");
        assertThat(privateKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(privateKey.getEncoded()).isNotEmpty();
        assertThat(publicKey.getEncoded()).isNotEmpty();

        engine.initVerify(publicKey);
        assertThat(engine.verifyOneShot(message, signature)).isTrue();

        byte[] tamperedSignature = signature.clone();
        tamperedSignature[tamperedSignature.length - 1] ^= 0x01;
        engine.initVerify(publicKey);
        assertThat(engine.verifyOneShot(message, tamperedSignature)).isFalse();
    }

    @Test
    void generatesAndRoundTripsKeysThroughThePublicApi() throws Exception {
        Provider provider = new EdDSASecurityProvider();
        net.i2p.crypto.eddsa.KeyPairGenerator keyPairGenerator = new net.i2p.crypto.eddsa.KeyPairGenerator();
        keyPairGenerator.initialize(new EdDSAGenParameterSpec(EdDSANamedCurveTable.ED_25519), new SecureRandom());

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        EdDSAPrivateKey privateKey = (EdDSAPrivateKey) keyPair.getPrivate();
        EdDSAPublicKey publicKey = (EdDSAPublicKey) keyPair.getPublic();
        EdDSAPrivateKey roundTrippedPrivateKey = new EdDSAPrivateKey(new PKCS8EncodedKeySpec(privateKey.getEncoded()));
        EdDSAPublicKey roundTrippedPublicKey = new EdDSAPublicKey(new X509EncodedKeySpec(publicKey.getEncoded()));
        byte[] message = "metadata forge exercises EdDSA signing".getBytes(StandardCharsets.UTF_8);

        assertThat(provider.getName()).isEqualTo(EdDSASecurityProvider.PROVIDER_NAME);
        assertThat(provider.getService("Signature", "NONEwithEdDSA")).isNotNull();
        assertThat(provider.getService("KeyFactory", "EdDSA")).isNotNull();
        assertThat(provider.getService("KeyPairGenerator", "EdDSA")).isNotNull();
        assertThat(roundTrippedPrivateKey).isEqualTo(privateKey);
        assertThat(roundTrippedPublicKey).isEqualTo(publicKey);
        assertThat(privateKey.getSeed()).hasSize(32);
        assertThat(privateKey.getAbyte()).hasSize(32);
        assertThat(publicKey.getAbyte()).hasSize(32);
        assertThat(privateKey.getAbyte()).isEqualTo(publicKey.getAbyte());

        EdDSAEngine signer = new EdDSAEngine();
        signer.initSign(privateKey);
        signer.update(message, 0, 8);
        signer.update(message, 8, message.length - 8);
        byte[] signature = signer.sign();

        EdDSAEngine verifier = new EdDSAEngine();
        verifier.initVerify(roundTrippedPublicKey);
        verifier.update(message);
        assertThat(verifier.verify(signature)).isTrue();

        byte[] tamperedMessage = message.clone();
        tamperedMessage[0] ^= 0x01;
        verifier.initVerify(roundTrippedPublicKey);
        verifier.update(tamperedMessage);
        assertThat(verifier.verify(signature)).isFalse();
    }

    @Test
    void rebuildsKeysFromExpandedPrivateStateAndGroupElements() {
        EdDSANamedCurveSpec curveSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        EdDSAPrivateKey seedBackedPrivateKey = new EdDSAPrivateKey(new EdDSAPrivateKeySpec(RFC_8032_SEED, curveSpec));
        EdDSAPublicKey byteBackedPublicKey = new EdDSAPublicKey(new EdDSAPublicKeySpec(RFC_8032_PUBLIC_KEY, curveSpec));
        byte[] expandedHash = seedBackedPrivateKey.getH().clone();

        EdDSAPrivateKey hashBackedPrivateKey = new EdDSAPrivateKey(new EdDSAPrivateKeySpec(curveSpec, expandedHash));
        EdDSAPublicKey groupElementBackedPublicKey = new EdDSAPublicKey(new EdDSAPublicKeySpec(byteBackedPublicKey.getA(), curveSpec));

        assertThat(hashBackedPrivateKey.getSeed()).isNull();
        assertThat(hashBackedPrivateKey.getH()).isEqualTo(seedBackedPrivateKey.getH());
        assertThat(hashBackedPrivateKey.geta()).isEqualTo(seedBackedPrivateKey.geta());
        assertThat(hashBackedPrivateKey.getAbyte()).isEqualTo(seedBackedPrivateKey.getAbyte());
        assertThat(hashBackedPrivateKey.getParams()).isEqualTo(curveSpec);
        assertThat(groupElementBackedPublicKey).isEqualTo(byteBackedPublicKey);
        assertThat(groupElementBackedPublicKey.getAbyte()).isEqualTo(RFC_8032_PUBLIC_KEY);
        assertThat(groupElementBackedPublicKey.getParams()).isEqualTo(curveSpec);
    }

    @Test
    void supportsOneShotModeThroughTheSignatureApi() throws Exception {
        EdDSANamedCurveSpec curveSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        EdDSAPrivateKey privateKey = new EdDSAPrivateKey(new EdDSAPrivateKeySpec(RFC_8032_SEED, curveSpec));
        EdDSAPublicKey publicKey = new EdDSAPublicKey(new EdDSAPublicKeySpec(RFC_8032_PUBLIC_KEY, curveSpec));
        byte[] message = "one shot mode signs the requested byte range".getBytes(StandardCharsets.UTF_8);
        byte[] paddedMessage = "__one shot mode signs the requested byte range__".getBytes(StandardCharsets.UTF_8);

        EdDSAEngine signer = new EdDSAEngine(MessageDigest.getInstance(curveSpec.getHashAlgorithm()));
        signer.initSign(privateKey);
        signer.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        signer.update(paddedMessage, 2, message.length);
        byte[] signature = signer.sign();

        EdDSAEngine verifier = new EdDSAEngine(MessageDigest.getInstance(curveSpec.getHashAlgorithm()));
        verifier.initVerify(publicKey);
        verifier.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        verifier.update(paddedMessage, 2, message.length);
        assertThat(verifier.verify(signature)).isTrue();

        byte[] tamperedMessage = paddedMessage.clone();
        tamperedMessage[2] ^= 0x01;
        verifier.initVerify(publicKey);
        verifier.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        verifier.update(tamperedMessage, 2, message.length);
        assertThat(verifier.verify(signature)).isFalse();

        EdDSAEngine splitUpdateSigner = new EdDSAEngine();
        splitUpdateSigner.initSign(privateKey);
        splitUpdateSigner.setParameter(EdDSAEngine.ONE_SHOT_MODE);
        splitUpdateSigner.update(message, 0, 8);
        assertThatThrownBy(() -> splitUpdateSigner.update(message, 8, message.length - 8))
                .isInstanceOf(SignatureException.class)
                .hasMessage("update() already called");

        EdDSAEngine lateOneShotModeSigner = new EdDSAEngine();
        lateOneShotModeSigner.initSign(privateKey);
        lateOneShotModeSigner.update(message, 0, 8);
        assertThatThrownBy(() -> lateOneShotModeSigner.setParameter(EdDSAEngine.ONE_SHOT_MODE))
                .isInstanceOf(InvalidAlgorithmParameterException.class)
                .hasMessage("update() already called");
    }
}
