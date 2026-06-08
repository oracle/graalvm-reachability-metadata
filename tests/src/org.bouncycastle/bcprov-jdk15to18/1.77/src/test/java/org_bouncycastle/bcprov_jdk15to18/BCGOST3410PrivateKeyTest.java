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
import java.security.PrivateKey;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.asn1.cryptopro.GOST3410PublicKeyAlgParameters;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jcajce.provider.asymmetric.gost.BCGOST3410PrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.gost.KeyFactorySpi;
import org.bouncycastle.jce.interfaces.GOST3410Params;
import org.bouncycastle.jce.interfaces.GOST3410PrivateKey;
import org.junit.jupiter.api.Test;

public class BCGOST3410PrivateKeyTest {
    private static final String BCGOST3410_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.gost.BCGOST3410PrivateKey";
    private static final BigInteger PRIVATE_VALUE = BigInteger.valueOf(23L);
    private static final String PUBLIC_KEY_PARAM_SET =
        CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A.getId();
    private static final String DIGEST_PARAM_SET =
        CryptoProObjectIdentifiers.gostR3411_94_CryptoProParamSet.getId();

    @Test
    void javaSerializationPreservesGost3410PrivateKeyParameters() throws Exception {
        BCGOST3410PrivateKey privateKey = generatePrivateKey();

        BCGOST3410PrivateKeyObjectOutputStream objectOutputStream =
            new BCGOST3410PrivateKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(privateKey);
        BCGOST3410PrivateKeyObjectInputStream objectInputStream =
            new BCGOST3410PrivateKeyObjectInputStream(serialized);
        GOST3410PrivateKey restored = objectInputStream.deserialize();

        assertTrue(objectOutputStream.sawGostParameterObjects());
        assertTrue(objectInputStream.sawGostParameterObjects());
        assertPrivateKeyRoundTrip(privateKey, restored);
    }

    private static BCGOST3410PrivateKey generatePrivateKey() throws Exception {
        GOST3410PublicKeyAlgParameters parameters = new GOST3410PublicKeyAlgParameters(
            CryptoProObjectIdentifiers.gostR3410_94_CryptoPro_A,
            CryptoProObjectIdentifiers.gostR3411_94_CryptoProParamSet);
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(
            new AlgorithmIdentifier(CryptoProObjectIdentifiers.gostR3410_94, parameters),
            new ASN1Integer(PRIVATE_VALUE));
        return (BCGOST3410PrivateKey)new KeyFactorySpi().generatePrivate(privateKeyInfo);
    }

    private static void assertPrivateKeyRoundTrip(
            GOST3410PrivateKey privateKey,
            GOST3410PrivateKey restored) {
        assertEquals(BCGOST3410_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BCGOST3410_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(privateKey.getFormat(), restored.getFormat());
        assertEquals(privateKey.getX(), restored.getX());
        assertParameters(privateKey.getParameters(), restored.getParameters());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
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
    }

    private static final class BCGOST3410PrivateKeyObjectOutputStream
            extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean publicKeyParamSetSeen;
        private boolean digestParamSetSeen;

        private BCGOST3410PrivateKeyObjectOutputStream() throws IOException {
            this(new ByteArrayOutputStream());
        }

        private BCGOST3410PrivateKeyObjectOutputStream(
                ByteArrayOutputStream byteOutputStream) throws IOException {
            super(byteOutputStream);
            this.byteOutputStream = byteOutputStream;
            enableReplaceObject(true);
        }

        private byte[] serialize(PrivateKey privateKey) throws IOException {
            try {
                writeObject(privateKey);
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

    private static final class BCGOST3410PrivateKeyObjectInputStream
            extends ObjectInputStream {
        private boolean publicKeyParamSetSeen;
        private boolean digestParamSetSeen;

        private BCGOST3410PrivateKeyObjectInputStream(byte[] serialized) throws IOException {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private GOST3410PrivateKey deserialize() throws IOException, ClassNotFoundException {
            try {
                return (GOST3410PrivateKey)readObject();
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
