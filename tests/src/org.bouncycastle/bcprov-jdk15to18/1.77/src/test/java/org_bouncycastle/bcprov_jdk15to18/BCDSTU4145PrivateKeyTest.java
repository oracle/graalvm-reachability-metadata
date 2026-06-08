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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ua.DSTU4145NamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.junit.jupiter.api.Test;

public class BCDSTU4145PrivateKeyTest {
    private static final String BCDSTU4145_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PrivateKey";
    private static final BigInteger PRIVATE_VALUE = BigInteger.valueOf(7L);

    @Test
    void javaSerializationPreservesDstu4145PrivateKeyParameters() throws Exception {
        BCDSTU4145PrivateKey privateKey = generatePrivateKey();

        byte[] serialized = serialize(privateKey);
        ECPrivateKey restored = deserialize(serialized);

        assertEquals(BCDSTU4145_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(BCDSTU4145_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals(privateKey.getFormat(), restored.getFormat());
        assertEquals(privateKey.getS(), restored.getS());
        assertParameters(privateKey.getParams(), restored.getParams());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
    }

    private static BCDSTU4145PrivateKey generatePrivateKey() throws Exception {
        ASN1ObjectIdentifier curveOid = DSTU4145NamedCurves.getOIDs()[0];
        ECDomainParameters domainParameters = DSTU4145NamedCurves.getByOID(curveOid);
        ECNamedCurveParameterSpec curveSpec = new ECNamedCurveParameterSpec(
            curveOid.getId(),
            domainParameters.getCurve(),
            domainParameters.getG(),
            domainParameters.getN(),
            domainParameters.getH(),
            domainParameters.getSeed());
        KeyFactory keyFactory = KeyFactory.getInstance("DSTU4145", new BouncyCastleProvider());
        PrivateKey privateKey = keyFactory.generatePrivate(
            new ECPrivateKeySpec(PRIVATE_VALUE, curveSpec));
        return (BCDSTU4145PrivateKey)privateKey;
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

    private static byte[] serialize(ECPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream =
                new BCDSTU4145PrivateKeyObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static ECPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (ECPrivateKey)objectInputStream.readObject();
        }
    }

    private static final class BCDSTU4145PrivateKeyObjectOutputStream
            extends ObjectOutputStream {
        private boolean replacementWritten;

        private BCDSTU4145PrivateKeyObjectOutputStream(OutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacementWritten && object instanceof byte[]) {
                replacementWritten = true;
                return new BCDSTU4145PrivateKeyEncodedKey((byte[])object);
            }
            return object;
        }
    }

    private static final class BCDSTU4145PrivateKeyEncodedKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final byte[] encoded;

        private BCDSTU4145PrivateKeyEncodedKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        private Object readResolve() throws ObjectStreamException {
            return encoded.clone();
        }
    }
}
