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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.jce.provider.JCEElGamalPrivateKey;
import org.junit.jupiter.api.Test;

public class JCEElGamalPrivateKeyTest {
    private static final String JCE_ELGAMAL_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jce.provider.JCEElGamalPrivateKey";
    private static final long JCE_ELGAMAL_PRIVATE_KEY_SERIAL_VERSION_UID =
        ObjectStreamClass.lookup(JCEElGamalPrivateKey.class).getSerialVersionUID();
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger X = BigInteger.valueOf(7L);

    @Test
    void javaDeserializationReadsElGamalPrivateKeyParameters() throws Exception {
        JCEElGamalPrivateKey privateKey = deserialize(initialSerializedKey());

        assertElGamalPrivateKey(privateKey);
    }

    @Test
    void javaSerializationWritesElGamalPrivateKeyParameters() throws Exception {
        JCEElGamalPrivateKey privateKey = deserialize(initialSerializedKey());

        byte[] serialized = serialize(privateKey);
        JCEElGamalPrivateKey restored = deserialize(serialized);

        assertElGamalPrivateKey(restored);
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
    }

    @Test
    void javaSerializationProcessesElGamalPrivateKeyParameterObjects() throws Exception {
        JCEElGamalPrivateKey privateKey = deserialize(initialSerializedKey());

        TrackingObjectOutputStream outputStream = new TrackingObjectOutputStream();
        outputStream.writeObject(privateKey);
        outputStream.close();

        TrackingObjectInputStream inputStream = new TrackingObjectInputStream(
            outputStream.toByteArray());
        JCEElGamalPrivateKey restored = (JCEElGamalPrivateKey)inputStream.readObject();
        inputStream.close();

        assertElGamalPrivateKey(restored);
        assertTrue(outputStream.bigIntegers.contains(X));
        assertTrue(outputStream.bigIntegers.contains(P));
        assertTrue(outputStream.bigIntegers.contains(G));
        assertTrue(inputStream.bigIntegers.contains(X));
        assertTrue(inputStream.bigIntegers.contains(P));
        assertTrue(inputStream.bigIntegers.contains(G));
    }

    private static void assertElGamalPrivateKey(JCEElGamalPrivateKey privateKey) {
        assertEquals(JCE_ELGAMAL_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals("ElGamal", privateKey.getAlgorithm());
        assertEquals("PKCS#8", privateKey.getFormat());
        assertEquals(X, privateKey.getX());
        assertEquals(P, privateKey.getParameters().getP());
        assertEquals(G, privateKey.getParameters().getG());
        assertEquals(P, privateKey.getParams().getP());
        assertEquals(G, privateKey.getParams().getG());
    }

    private static byte[] initialSerializedKey() throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new JCEElGamalPrivateKeyObjectOutputStream(
                byteOutputStream)) {
            objectOutputStream.writeObject(new SerializedJceElGamalPrivateKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] serialize(JCEElGamalPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static JCEElGamalPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (JCEElGamalPrivateKey)objectInputStream.readObject();
        }
    }

    private static final class SerializedJceElGamalPrivateKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream outputStream) throws IOException {
            outputStream.writeObject(X);
            outputStream.writeObject(P);
            outputStream.writeObject(G);
        }
    }

    private static final class JCEElGamalPrivateKeyObjectOutputStream extends ObjectOutputStream {
        private static final byte SERIALIZABLE_CLASS_WITH_WRITE_OBJECT = 0x03;

        private JCEElGamalPrivateKeyObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJceElGamalPrivateKey.class.getName().equals(descriptor.getName())) {
                writeUTF(JCE_ELGAMAL_PRIVATE_KEY_CLASS);
                writeLong(JCE_ELGAMAL_PRIVATE_KEY_SERIAL_VERSION_UID);
                writeByte(SERIALIZABLE_CLASS_WITH_WRITE_OBJECT);
                writeShort(0);
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }

    private static final class TrackingObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private final List<BigInteger> bigIntegers = new ArrayList<>();

        private TrackingObjectOutputStream() throws IOException {
            this(new ByteArrayOutputStream());
        }

        private TrackingObjectOutputStream(ByteArrayOutputStream byteOutputStream) throws IOException {
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

        private TrackingObjectInputStream(byte[] serialized) throws IOException {
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
