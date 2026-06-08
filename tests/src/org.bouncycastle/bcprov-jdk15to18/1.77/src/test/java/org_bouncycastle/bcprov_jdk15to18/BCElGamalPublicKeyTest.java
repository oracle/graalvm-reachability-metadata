/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.spec.DHPublicKeySpec;

import org.bouncycastle.jce.interfaces.ElGamalPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class BCElGamalPublicKeyTest {
    private static final String BC_ELGAMAL_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jcajce.provider.asymmetric.elgamal.BCElGamalPublicKey";
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger Y = BigInteger.valueOf(8L);

    @Test
    void javaSerializationPreservesElGamalPublicKeyParameters() throws Exception {
        ElGamalPublicKey publicKey = generatePublicKey();

        byte[] serialized = serialize(publicKey);
        ElGamalPublicKey restored = (ElGamalPublicKey)deserialize(serialized);

        assertEquals(BC_ELGAMAL_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals(BC_ELGAMAL_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertEquals("ElGamal", publicKey.getAlgorithm());
        assertEquals("X.509", publicKey.getFormat());
        assertEquals(publicKey.getY(), restored.getY());
        assertEquals(publicKey.getParameters().getP(), restored.getParameters().getP());
        assertEquals(publicKey.getParameters().getG(), restored.getParameters().getG());
        assertEquals(publicKey.getParams().getP(), restored.getParams().getP());
        assertEquals(publicKey.getParams().getG(), restored.getParams().getG());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
    }

    @Test
    void javaSerializationPreservesElGamalPublicKeyAsNestedObject() throws Exception {
        ElGamalPublicKey publicKey = generatePublicKey();
        KeyEnvelope keyEnvelope = new KeyEnvelope(publicKey);

        byte[] serialized = serialize(keyEnvelope);
        KeyEnvelope restored = (KeyEnvelope)deserialize(serialized);

        assertEquals(BC_ELGAMAL_PUBLIC_KEY_CLASS, restored.publicKey.getClass().getName());
        assertEquals(publicKey.getY(), restored.publicKey.getY());
        assertEquals(publicKey.getParameters().getP(), restored.publicKey.getParameters().getP());
        assertEquals(publicKey.getParameters().getG(), restored.publicKey.getParameters().getG());
        assertArrayEquals(publicKey.getEncoded(), restored.publicKey.getEncoded());
    }

    @Test
    void javaSerializationProcessesElGamalPublicKeyParameterObjects() throws Exception {
        ElGamalPublicKey publicKey = generatePublicKey();

        TrackingObjectOutputStream outputStream = new TrackingObjectOutputStream();
        outputStream.writeObject(publicKey);
        outputStream.close();

        TrackingObjectInputStream inputStream = new TrackingObjectInputStream(
            outputStream.toByteArray());
        ElGamalPublicKey restored = (ElGamalPublicKey)inputStream.readObject();
        inputStream.close();

        assertEquals(BC_ELGAMAL_PUBLIC_KEY_CLASS, restored.getClass().getName());
        assertTrue(outputStream.bigIntegers.contains(Y));
        assertTrue(outputStream.bigIntegers.contains(P));
        assertTrue(outputStream.bigIntegers.contains(G));
        assertTrue(inputStream.bigIntegers.contains(Y));
        assertTrue(inputStream.bigIntegers.contains(P));
        assertTrue(inputStream.bigIntegers.contains(G));
    }

    private static ElGamalPublicKey generatePublicKey() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("ElGamal", new BouncyCastleProvider());
        PublicKey publicKey = keyFactory.generatePublic(new DHPublicKeySpec(Y, P, G));
        return (ElGamalPublicKey)publicKey;
    }

    private static byte[] serialize(Object publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }

    private static final class KeyEnvelope implements Serializable {
        private static final long serialVersionUID = 1L;

        private final ElGamalPublicKey publicKey;

        private KeyEnvelope(ElGamalPublicKey publicKey) {
            this.publicKey = publicKey;
        }
    }

    private static final class TrackingObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private final List<BigInteger> bigIntegers = new ArrayList<>();

        private TrackingObjectOutputStream() throws Exception {
            this(new ByteArrayOutputStream());
        }

        private TrackingObjectOutputStream(
            ByteArrayOutputStream byteOutputStream) throws Exception {
            super(byteOutputStream);
            this.byteOutputStream = byteOutputStream;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) {
            if (object instanceof BigInteger) {
                bigIntegers.add((BigInteger)object);
            }
            return object;
        }

        private byte[] toByteArray() {
            return byteOutputStream.toByteArray();
        }
    }

    private static final class TrackingObjectInputStream extends ObjectInputStream {
        private final List<BigInteger> bigIntegers = new ArrayList<>();

        private TrackingObjectInputStream(byte[] serialized) throws Exception {
            super(new ByteArrayInputStream(serialized));
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) {
            if (object instanceof BigInteger) {
                bigIntegers.add((BigInteger)object);
            }
            return object;
        }
    }
}
