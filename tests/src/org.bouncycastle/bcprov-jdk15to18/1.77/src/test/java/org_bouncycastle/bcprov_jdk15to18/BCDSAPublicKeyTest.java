/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.interfaces.DSAPublicKey;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DSAParameter;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.dsa.BCDSAPublicKey;
import org.junit.jupiter.api.Test;

public class BCDSAPublicKeyTest {
    private static final String BCDSA_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.dsa.BCDSAPublicKey";
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger Q = BigInteger.valueOf(11L);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger Y = BigInteger.valueOf(8L);

    @Test
    void javaSerializationPreservesDsaPublicKeyParameters() throws Exception {
        DSAPublicKey publicKey = generatePublicKeyWithParameters();

        byte[] serialized = serialize(publicKey);
        DSAPublicKey restored = deserializePublicKey(serialized);

        assertEquals(BCDSA_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCDSA_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getY(), restored.getY());
        assertEquals(publicKey.getParams().getP(), restored.getParams().getP());
        assertEquals(publicKey.getParams().getQ(), restored.getParams().getQ());
        assertEquals(publicKey.getParams().getG(), restored.getParams().getG());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
    }

    @Test
    void javaSerializationPreservesDsaPublicKeyWithoutParameters() throws Exception {
        DSAPublicKey publicKey = generatePublicKeyWithoutParameters();

        byte[] serialized = serialize(publicKey);
        DSAPublicKey restored = deserializePublicKey(serialized);

        assertEquals(BCDSA_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BCDSA_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals(publicKey.getY(), restored.getY());
        assertNull(publicKey.getParams());
        assertNull(restored.getParams());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
    }

    @Test
    void javaSerializationPreservesDsaPublicKeyAsNestedObject() throws Exception {
        DSAPublicKey publicKey = generatePublicKeyWithParameters();
        KeyEnvelope keyEnvelope = new KeyEnvelope(publicKey);

        byte[] serialized = serialize(keyEnvelope);
        KeyEnvelope restored = (KeyEnvelope)deserialize(serialized);

        assertEquals(BCDSA_PUBLIC_KEY_CLASS, restored.publicKey.getClass().getName());
        assertEquals(publicKey.getY(), restored.publicKey.getY());
        assertEquals(publicKey.getParams().getP(), restored.publicKey.getParams().getP());
        assertEquals(publicKey.getParams().getQ(), restored.publicKey.getParams().getQ());
        assertEquals(publicKey.getParams().getG(), restored.publicKey.getParams().getG());
    }

    private static BCDSAPublicKey generatePublicKeyWithParameters() throws Exception {
        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo(
            new AlgorithmIdentifier(
                X9ObjectIdentifiers.id_dsa,
                new DSAParameter(P, Q, G).toASN1Primitive()),
            new ASN1Integer(Y));
        return new BCDSAPublicKey(publicKeyInfo);
    }

    private static BCDSAPublicKey generatePublicKeyWithoutParameters() throws Exception {
        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo(
            new AlgorithmIdentifier(X9ObjectIdentifiers.id_dsa),
            new ASN1Integer(Y));
        return new BCDSAPublicKey(publicKeyInfo);
    }

    private static byte[] serialize(Object object) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(object);
        }
        return byteOutputStream.toByteArray();
    }

    private static DSAPublicKey deserializePublicKey(byte[] serialized) throws Exception {
        return (DSAPublicKey)deserialize(serialized);
    }

    private static Object deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }

    private static final class KeyEnvelope implements Serializable {
        private static final long serialVersionUID = 1L;

        private final DSAPublicKey publicKey;

        private KeyEnvelope(DSAPublicKey publicKey) {
            this.publicKey = publicKey;
        }
    }
}
