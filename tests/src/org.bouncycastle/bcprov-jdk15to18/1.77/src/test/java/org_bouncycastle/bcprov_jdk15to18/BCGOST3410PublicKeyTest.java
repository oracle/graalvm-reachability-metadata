/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.PublicKey;

import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.asn1.cryptopro.GOST3410NamedParameters;
import org.bouncycastle.asn1.cryptopro.GOST3410ParamSetParameters;
import org.bouncycastle.asn1.cryptopro.GOST3410PublicKeyAlgParameters;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.gost.BCGOST3410PublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.gost.KeyFactorySpi;
import org.bouncycastle.jce.interfaces.GOST3410Params;
import org.bouncycastle.jce.interfaces.GOST3410PublicKey;
import org.junit.jupiter.api.Test;

public class BCGOST3410PublicKeyTest {
    private static final String BCGOST3410_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.gost.BCGOST3410PublicKey";
    private static final BigInteger PUBLIC_VALUE = BigInteger.valueOf(23L);
    private static final String PUBLIC_KEY_PARAM_SET =
        CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A.getId();
    private static final String DIGEST_PARAM_SET =
        CryptoProObjectIdentifiers.gostR3411_94_CryptoProParamSet.getId();
    private static final GOST3410ParamSetParameters PARAM_SET =
        GOST3410NamedParameters.getByOID(CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A);

    @Test
    void javaSerializationRoundTripsGost3410PublicKey() throws Exception {
        BCGOST3410PublicKey publicKey = generatePublicKey();

        byte[] serialized = serialize(publicKey);
        GOST3410PublicKey restored = deserialize(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaSerializationWritesAndReadsGost3410PublicKeyParameters() throws Exception {
        BCGOST3410PublicKey publicKey = generatePublicKey();

        BCGOST3410PublicKeyObjectOutputStream objectOutputStream =
            new BCGOST3410PublicKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(publicKey);
        BCGOST3410PublicKeyObjectInputStream objectInputStream =
            new BCGOST3410PublicKeyObjectInputStream(serialized);
        GOST3410PublicKey restored = objectInputStream.deserialize();

        assertTrue(objectOutputStream.sawGostParameterObjects());
        assertTrue(objectInputStream.sawGostParameterObjects());
        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static BCGOST3410PublicKey generatePublicKey() throws Exception {
        return (BCGOST3410PublicKey)new KeyFactorySpi().generatePublic(createPublicKeyInfo());
    }

    private static SubjectPublicKeyInfo createPublicKeyInfo() throws IOException {
        GOST3410PublicKeyAlgParameters parameters = new GOST3410PublicKeyAlgParameters(
            CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A,
            CryptoProObjectIdentifiers.gostR3411_94_CryptoProParamSet);
        return new SubjectPublicKeyInfo(
            new AlgorithmIdentifier(CryptoProObjectIdentifiers.gostR3410_94, parameters),
            new DEROctetString(toLittleEndianUnsigned(PUBLIC_VALUE)));
    }

    private static byte[] toLittleEndianUnsigned(BigInteger value) {
        byte[] bigEndian = value.toByteArray();
        int start = bigEndian[0] == 0 ? 1 : 0;
        byte[] littleEndian = new byte[bigEndian.length - start];
        for (int i = 0; i < littleEndian.length; i++) {
            littleEndian[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return littleEndian;
    }

    private static byte[] serialize(PublicKey publicKey) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static GOST3410PublicKey deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (GOST3410PublicKey)objectInputStream.readObject();
        }
    }

    private static void assertPublicKeyRoundTrip(
            GOST3410PublicKey publicKey,
            GOST3410PublicKey restored) {
        assertEquals(BCGOST3410_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCGOST3410_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(publicKey.getFormat(), restored.getFormat());
        assertEquals(publicKey.getY(), restored.getY());
        assertParameters(publicKey.getParameters(), restored.getParameters());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
        assertNotNull(restored.toString());
    }

    private static void assertParameters(
            GOST3410Params expected,
            GOST3410Params actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(PUBLIC_KEY_PARAM_SET, actual.getPublicKeyParamSetOID());
        assertEquals(DIGEST_PARAM_SET, actual.getDigestParamSetOID());
        assertEquals(expected.getPublicKeyParamSetOID(), actual.getPublicKeyParamSetOID());
        assertEquals(expected.getDigestParamSetOID(), actual.getDigestParamSetOID());
        assertEquals(expected.getEncryptionParamSetOID(), actual.getEncryptionParamSetOID());
        assertEquals(expected.getPublicKeyParameters(), actual.getPublicKeyParameters());
        assertEquals(PARAM_SET.getP(), actual.getPublicKeyParameters().getP());
        assertEquals(PARAM_SET.getQ(), actual.getPublicKeyParameters().getQ());
        assertEquals(PARAM_SET.getA(), actual.getPublicKeyParameters().getA());
    }

    private static final class BCGOST3410PublicKeyObjectOutputStream
            extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean publicKeyParamSetSeen;
        private boolean digestParamSetSeen;

        private BCGOST3410PublicKeyObjectOutputStream() throws IOException {
            this(new ByteArrayOutputStream());
        }

        private BCGOST3410PublicKeyObjectOutputStream(
                ByteArrayOutputStream byteOutputStream) throws IOException {
            super(byteOutputStream);
            this.byteOutputStream = byteOutputStream;
            enableReplaceObject(true);
        }

        private byte[] serialize(PublicKey publicKey) throws IOException {
            try {
                writeObject(publicKey);
            }
            finally {
                close();
            }
            return byteOutputStream.toByteArray();
        }

        private boolean sawGostParameterObjects() {
            return publicKeyParamSetSeen && digestParamSetSeen;
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            recordGostParameterObject(object);
            return object;
        }

        private void recordGostParameterObject(Object object) {
            publicKeyParamSetSeen |= PUBLIC_KEY_PARAM_SET.equals(object);
            digestParamSetSeen |= DIGEST_PARAM_SET.equals(object);
        }
    }

    private static final class BCGOST3410PublicKeyObjectInputStream
            extends ObjectInputStream {
        private boolean publicKeyParamSetSeen;
        private boolean digestParamSetSeen;

        private BCGOST3410PublicKeyObjectInputStream(byte[] serialized) throws IOException {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private GOST3410PublicKey deserialize() throws IOException, ClassNotFoundException {
            try {
                return (GOST3410PublicKey)readObject();
            }
            finally {
                close();
            }
        }

        private boolean sawGostParameterObjects() {
            return publicKeyParamSetSeen && digestParamSetSeen;
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            recordGostParameterObject(object);
            return object;
        }

        private void recordGostParameterObject(Object object) {
            publicKeyParamSetSeen |= PUBLIC_KEY_PARAM_SET.equals(object);
            digestParamSetSeen |= DIGEST_PARAM_SET.equals(object);
        }
    }
}
