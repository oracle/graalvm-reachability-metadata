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
import java.io.Serializable;
import java.math.BigInteger;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cryptopro.ECGOST3410NamedCurves;
import org.bouncycastle.asn1.rosstandart.RosstandartObjectIdentifiers;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECGOST3410Parameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.ecgost12.BCECGOST3410_2012PublicKey;
import org.bouncycastle.jce.ECGOST3410NamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Test;

public class BCECGOST3410_2012PublicKeyTest {
    private static final String BCECGOST3410_2012_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.ecgost12.BCECGOST3410_2012PublicKey";
    private static final String CURVE_NAME = "Tc26-Gost-3410-12-256-paramSetA";

    @Test
    void javaSerializationPreservesEcGost3410_2012PublicKeyParameters() throws Exception {
        BCECGOST3410_2012PublicKey publicKey = generatePublicKey();

        byte[] serialized = serialize(publicKey);
        ECPublicKey restored = deserialize(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaSerializationWritesAndReadsEncodedEcGost3410_2012PublicKey() throws Exception {
        BCECGOST3410_2012PublicKey publicKey = generatePublicKey();

        BCECGOST3410_2012PublicKeyObjectOutputStream objectOutputStream =
            new BCECGOST3410_2012PublicKeyObjectOutputStream();
        byte[] serialized = objectOutputStream.serialize(publicKey);
        BCECGOST3410_2012PublicKeyObjectInputStream objectInputStream =
            new BCECGOST3410_2012PublicKeyObjectInputStream(serialized);
        ECPublicKey restored = objectInputStream.deserialize();

        assertTrue(objectOutputStream.sawEncodedKey());
        assertTrue(objectInputStream.sawEncodedKey());
        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static BCECGOST3410_2012PublicKey generatePublicKey() {
        ECNamedCurveParameterSpec curveSpec = ECGOST3410NamedCurveTable.getParameterSpec(
            CURVE_NAME);
        ASN1ObjectIdentifier publicKeyParamSet = ECGOST3410NamedCurves.getOID(CURVE_NAME);
        ECDomainParameters domainParameters = new ECDomainParameters(
            curveSpec.getCurve(),
            curveSpec.getG(),
            curveSpec.getN(),
            curveSpec.getH(),
            curveSpec.getSeed());
        ECGOST3410Parameters gostParameters = new ECGOST3410Parameters(
            domainParameters,
            publicKeyParamSet,
            RosstandartObjectIdentifiers.id_tc26_gost_3411_12_256);
        ECPoint publicPoint = curveSpec.getG().multiply(BigInteger.valueOf(2)).normalize();
        ECPublicKeyParameters publicKeyParameters = new ECPublicKeyParameters(
            publicPoint,
            gostParameters);
        return new BCECGOST3410_2012PublicKey(
            "ECGOST3410-2012",
            publicKeyParameters,
            curveSpec);
    }

    private static void assertPublicKeyRoundTrip(
            ECPublicKey publicKey,
            ECPublicKey restored) {
        assertEquals(BCECGOST3410_2012_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCECGOST3410_2012_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(publicKey.getFormat(), restored.getFormat());
        assertEquals(publicKey.getW(), restored.getW());
        assertParameters(publicKey.getParams(), restored.getParams());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
        assertNotNull(restored.toString());
    }

    private static void assertParameters(
            ECParameterSpec expected,
            ECParameterSpec actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getOrder(), actual.getOrder());
        assertEquals(expected.getCofactor(), actual.getCofactor());
        assertEquals(expected.getCurve(), actual.getCurve());
        assertEquals(expected.getGenerator(), actual.getGenerator());
    }

    private static byte[] serialize(ECPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static ECPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (ECPublicKey)objectInputStream.readObject();
        }
    }

    private static final class BCECGOST3410_2012PublicKeyObjectOutputStream
            extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private boolean encodedKeySeen;

        private BCECGOST3410_2012PublicKeyObjectOutputStream() throws IOException {
            this(new ByteArrayOutputStream());
        }

        private BCECGOST3410_2012PublicKeyObjectOutputStream(
                ByteArrayOutputStream byteOutputStream) throws IOException {
            super(byteOutputStream);
            this.byteOutputStream = byteOutputStream;
            enableReplaceObject(true);
        }

        private byte[] serialize(ECPublicKey publicKey) throws IOException {
            try {
                writeObject(publicKey);
            }
            finally {
                close();
            }
            return byteOutputStream.toByteArray();
        }

        private boolean sawEncodedKey() {
            return encodedKeySeen;
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!encodedKeySeen && object instanceof byte[]) {
                encodedKeySeen = true;
                return new SerializedEncodedKey((byte[])object);
            }
            return object;
        }
    }

    private static final class BCECGOST3410_2012PublicKeyObjectInputStream
            extends ObjectInputStream {
        private boolean encodedKeySeen;

        private BCECGOST3410_2012PublicKeyObjectInputStream(byte[] serialized) throws IOException {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        private ECPublicKey deserialize() throws IOException, ClassNotFoundException {
            try {
                return (ECPublicKey)readObject();
            }
            finally {
                close();
            }
        }

        private boolean sawEncodedKey() {
            return encodedKeySeen;
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof SerializedEncodedKey) {
                encodedKeySeen = true;
                return ((SerializedEncodedKey)object).getEncoded();
            }
            return object;
        }
    }

    private static final class SerializedEncodedKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final byte[] encoded;

        private SerializedEncodedKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        private byte[] getEncoded() {
            return encoded.clone();
        }
    }
}
