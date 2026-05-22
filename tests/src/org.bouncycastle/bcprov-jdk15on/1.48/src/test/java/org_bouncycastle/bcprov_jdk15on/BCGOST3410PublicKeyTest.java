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
import java.io.ObjectStreamClass;
import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.asn1.cryptopro.GOST3410PublicKeyAlgParameters;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.gost.BCGOST3410PublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.gost.KeyFactorySpi;
import org.bouncycastle.jce.interfaces.GOST3410PublicKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BCGOST3410PublicKeyTest {
    private static final BigInteger Y = BigInteger.valueOf(64L);
    private static final String PUBLIC_KEY_PARAM_SET_OID =
            CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A.getId();
    private static final String DIGEST_PARAM_SET_OID =
            CryptoProObjectIdentifiers.gostR3411_94_CryptoProParamSet.getId();
    private static final String ENCRYPTION_PARAM_SET_OID =
            CryptoProObjectIdentifiers.id_Gost28147_89_CryptoPro_A_ParamSet.getId();

    @Test
    void subjectPublicKeyInfoCreatedPublicKeySerializationRoundTripPreservesGostParameters() throws Exception {
        GOST3410PublicKey publicKey = createPublicKey();
        byte[] encodedPublicKey = publicKey.getEncoded();

        byte[] serializedPublicKey = serialize(publicKey);
        GOST3410PublicKey restoredKey = (GOST3410PublicKey) deserialize(serializedPublicKey);

        assertThat(publicKey).isInstanceOf(BCGOST3410PublicKey.class);
        assertThat(restoredKey).isInstanceOf(BCGOST3410PublicKey.class);
        assertThat(restoredKey).isNotSameAs(publicKey);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("GOST3410");
        assertThat(restoredKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredKey.getY()).isEqualTo(Y);
        assertThat(restoredKey.getParameters().getPublicKeyParamSetOID())
                .isEqualTo(PUBLIC_KEY_PARAM_SET_OID);
        assertThat(restoredKey.getParameters().getDigestParamSetOID())
                .isEqualTo(DIGEST_PARAM_SET_OID);
        assertThat(restoredKey.getParameters().getEncryptionParamSetOID())
                .isEqualTo(ENCRYPTION_PARAM_SET_OID);
        assertThat(restoredKey.getEncoded()).isEqualTo(encodedPublicKey);
    }

    private static GOST3410PublicKey createPublicKey() throws Exception {
        GOST3410PublicKeyAlgParameters parameters = new GOST3410PublicKeyAlgParameters(
                new ASN1ObjectIdentifier(PUBLIC_KEY_PARAM_SET_OID),
                new ASN1ObjectIdentifier(DIGEST_PARAM_SET_OID),
                new ASN1ObjectIdentifier(ENCRYPTION_PARAM_SET_OID));
        AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(
                CryptoProObjectIdentifiers.gostR3410_94,
                parameters.toASN1Primitive());
        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo(
                algorithmIdentifier,
                new DEROctetString(toLittleEndianUnsigned(Y)));

        return (GOST3410PublicKey) new KeyFactorySpi().generatePublic(publicKeyInfo);
    }

    private static byte[] toLittleEndianUnsigned(BigInteger value) {
        byte[] bigEndianValue = value.toByteArray();
        int start = bigEndianValue[0] == 0 ? 1 : 0;
        byte[] littleEndianValue = new byte[bigEndianValue.length - start];
        for (int i = 0; i < littleEndianValue.length; i++) {
            littleEndianValue[i] = bigEndianValue[bigEndianValue.length - 1 - i];
        }
        return littleEndianValue;
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
        }
        return byteStream.toByteArray();
    }

    private static Object deserialize(byte[] serializedValue) throws Exception {
        try (ObjectInputStream objectStream = new BouncyCastleObjectInputStream(
                new ByteArrayInputStream(serializedValue))) {
            return objectStream.readObject();
        }
    }

    private static final class BouncyCastleObjectInputStream extends ObjectInputStream {
        private BouncyCastleObjectInputStream(ByteArrayInputStream inputStream) throws IOException {
            super(inputStream);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass classDescription) throws IOException, ClassNotFoundException {
            if (BCGOST3410PublicKey.class.getName().equals(classDescription.getName())) {
                return BCGOST3410PublicKey.class;
            }
            return super.resolveClass(classDescription);
        }
    }
}
