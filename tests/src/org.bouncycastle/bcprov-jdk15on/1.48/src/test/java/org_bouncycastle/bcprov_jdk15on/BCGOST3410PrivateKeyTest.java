/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;

import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.gost.BCGOST3410PrivateKey;
import org.bouncycastle.jce.interfaces.GOST3410PrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.GOST3410ParameterSpec;
import org.bouncycastle.jce.spec.GOST3410PrivateKeySpec;
import org.bouncycastle.jce.spec.GOST3410PublicKeyParameterSetSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCGOST3410PrivateKeyTest {
    private static final BigInteger X = BigInteger.valueOf(7L);
    private static final String PUBLIC_KEY_PARAM_SET_OID =
            CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A.getId();
    private static final byte[] RANDOM_SEED = {0x21, 0x43, 0x65, 0x07};

    @Test
    void generatedPrivateKeySerializationRoundTripPreservesNamedGostParameters() throws Exception {
        GOST3410PrivateKey privateKey = createNamedPrivateKey();
        byte[] encodedPrivateKey = privateKey.getEncoded();

        byte[] serializedPrivateKey = serializeAndTrackWrittenParameter(privateKey, PUBLIC_KEY_PARAM_SET_OID);
        GOST3410PrivateKey restoredKey = (GOST3410PrivateKey) deserializeAndTrackReadParameter(
                serializedPrivateKey,
                PUBLIC_KEY_PARAM_SET_OID);

        assertThat(privateKey).isInstanceOf(BCGOST3410PrivateKey.class);
        assertThat(restoredKey).isInstanceOf(BCGOST3410PrivateKey.class);
        assertThat(restoredKey).isNotSameAs(privateKey);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("GOST3410");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getX()).isEqualTo(privateKey.getX());
        assertThat(restoredKey.getParameters().getPublicKeyParamSetOID())
                .isEqualTo(PUBLIC_KEY_PARAM_SET_OID);
        assertThat(restoredKey.getParameters().getDigestParamSetOID())
                .isEqualTo(privateKey.getParameters().getDigestParamSetOID());
        assertThat(restoredKey.getParameters().getEncryptionParamSetOID())
                .isEqualTo(privateKey.getParameters().getEncryptionParamSetOID());
        assertThat(restoredKey.getEncoded()).isEqualTo(encodedPrivateKey);
    }

    @Test
    void keyFactoryCreatedPrivateKeySerializationRoundTripPreservesGostParameters() throws Exception {
        GOST3410PrivateKey privateKey = createPrivateKey();
        GOST3410PublicKeyParameterSetSpec publicKeyParameters =
                privateKey.getParameters().getPublicKeyParameters();

        byte[] serializedPrivateKey = serializeAndTrackWrittenParameter(privateKey, publicKeyParameters.getP());
        GOST3410PrivateKey restoredKey = (GOST3410PrivateKey) deserializeAndTrackReadParameter(
                serializedPrivateKey,
                publicKeyParameters.getP());

        assertThat(privateKey).isInstanceOf(BCGOST3410PrivateKey.class);
        assertThat(restoredKey).isInstanceOf(BCGOST3410PrivateKey.class);
        assertThat(restoredKey).isNotSameAs(privateKey);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("GOST3410");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getX()).isEqualTo(X);
        assertThat(restoredKey.getParameters().getPublicKeyParameters()).isEqualTo(publicKeyParameters);
        assertThat(restoredKey.getParameters().getDigestParamSetOID())
                .isEqualTo(privateKey.getParameters().getDigestParamSetOID());
        assertThat(restoredKey.getParameters().getEncryptionParamSetOID())
                .isEqualTo(privateKey.getParameters().getEncryptionParamSetOID());
    }

    private static GOST3410PrivateKey createNamedPrivateKey() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("GOST3410", provider);
        generator.initialize(
                new GOST3410ParameterSpec(PUBLIC_KEY_PARAM_SET_OID),
                new SecureRandom(RANDOM_SEED));
        KeyPair keyPair = generator.generateKeyPair();

        return (GOST3410PrivateKey) keyPair.getPrivate();
    }

    private static GOST3410PrivateKey createPrivateKey() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyFactory keyFactory = KeyFactory.getInstance("GOST3410", provider);
        GOST3410ParameterSpec parameterSpec = new GOST3410ParameterSpec(PUBLIC_KEY_PARAM_SET_OID);
        GOST3410PublicKeyParameterSetSpec publicKeyParameters = parameterSpec.getPublicKeyParameters();
        GOST3410PrivateKeySpec keySpec = new GOST3410PrivateKeySpec(
                X,
                publicKeyParameters.getP(),
                publicKeyParameters.getQ(),
                publicKeyParameters.getA());

        return (GOST3410PrivateKey) keyFactory.generatePrivate(keySpec);
    }

    private static byte[] serializeAndTrackWrittenParameter(Object value, Object expectedParameter)
            throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (TrackingObjectOutputStream objectStream = new TrackingObjectOutputStream(
                byteStream,
                expectedParameter)) {
            objectStream.writeObject(value);
            assertThat(objectStream.wroteExpectedParameter()).isTrue();
        }
        return byteStream.toByteArray();
    }

    private static Object deserializeAndTrackReadParameter(byte[] serializedValue, Object expectedParameter)
            throws Exception {
        try (TrackingObjectInputStream objectStream = new TrackingObjectInputStream(
                new ByteArrayInputStream(serializedValue),
                expectedParameter)) {
            Object restored = objectStream.readObject();
            assertThat(objectStream.readExpectedParameter()).isTrue();
            return restored;
        }
    }

    private static final class TrackingObjectOutputStream extends ObjectOutputStream {
        private final Object expectedParameter;
        private boolean wroteExpectedParameter;

        private TrackingObjectOutputStream(ByteArrayOutputStream outputStream, Object expectedParameter)
                throws IOException {
            super(outputStream);
            this.expectedParameter = expectedParameter;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (expectedParameter.equals(object)) {
                wroteExpectedParameter = true;
            }
            return super.replaceObject(object);
        }

        private boolean wroteExpectedParameter() {
            return wroteExpectedParameter;
        }
    }

    private static final class TrackingObjectInputStream extends ObjectInputStream {
        private final Object expectedParameter;
        private boolean readExpectedParameter;

        private TrackingObjectInputStream(ByteArrayInputStream inputStream, Object expectedParameter)
                throws IOException {
            super(inputStream);
            this.expectedParameter = expectedParameter;
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (expectedParameter.equals(object)) {
                readExpectedParameter = true;
            }
            return super.resolveObject(object);
        }

        private boolean readExpectedParameter() {
            return readExpectedParameter;
        }
    }
}
