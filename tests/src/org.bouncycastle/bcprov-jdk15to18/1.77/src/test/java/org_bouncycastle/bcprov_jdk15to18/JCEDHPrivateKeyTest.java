/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.math.BigInteger;

import javax.crypto.spec.DHParameterSpec;

import org.bouncycastle.jce.provider.JCEDHPrivateKey;
import org.junit.jupiter.api.Test;

public class JCEDHPrivateKeyTest {
    private static final String JCE_DH_PRIVATE_KEY_CLASS = "org.bouncycastle.jce.provider.JCEDHPrivateKey";
    private static final long JCE_DH_PRIVATE_KEY_SERIAL_VERSION_UID = 311058815616901812L;
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger X = BigInteger.valueOf(7L);
    private static final int L = 4;

    @Test
    void javaDeserializationReadsDiffieHellmanPrivateKeyParameters() throws Exception {
        JCEDHPrivateKey privateKey = deserialize(initialSerializedKey());

        assertDiffieHellmanPrivateKey(privateKey);
    }

    @Test
    void javaSerializationWritesDiffieHellmanPrivateKeyParameters() throws Exception {
        JCEDHPrivateKey privateKey = deserialize(initialSerializedKey());

        byte[] serialized = serialize(privateKey);
        JCEDHPrivateKey restored = deserialize(serialized);

        assertDiffieHellmanPrivateKey(restored);
    }

    private static void assertDiffieHellmanPrivateKey(JCEDHPrivateKey privateKey) {
        assertEquals(JCE_DH_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals("DH", privateKey.getAlgorithm());
        assertEquals("PKCS#8", privateKey.getFormat());
        assertEquals(X, privateKey.getX());
        assertEquals(P, privateKey.getParams().getP());
        assertEquals(G, privateKey.getParams().getG());
        assertEquals(L, privateKey.getParams().getL());
    }

    private static byte[] initialSerializedKey() throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new JCEDHPrivateKeyObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(new SerializedJceDhPrivateKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] serialize(JCEDHPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static JCEDHPrivateKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (JCEDHPrivateKey)objectInputStream.readObject();
        }
    }

    private static final class SerializedJceDhPrivateKey implements Serializable {
        private static final long serialVersionUID = JCE_DH_PRIVATE_KEY_SERIAL_VERSION_UID;

        private void writeObject(ObjectOutputStream outputStream) throws IOException {
            outputStream.writeObject(X);
            outputStream.writeObject(P);
            outputStream.writeObject(G);
            outputStream.writeInt(L);
        }
    }

    private static final class JCEDHPrivateKeyObjectOutputStream extends ObjectOutputStream {
        private static final byte SERIALIZABLE_CLASS_WITH_WRITE_OBJECT = 0x03;

        private JCEDHPrivateKeyObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJceDhPrivateKey.class.getName().equals(descriptor.getName())) {
                writeUTF(JCE_DH_PRIVATE_KEY_CLASS);
                writeLong(JCE_DH_PRIVATE_KEY_SERIAL_VERSION_UID);
                writeByte(SERIALIZABLE_CLASS_WITH_WRITE_OBJECT);
                writeShort(0);
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }
}
