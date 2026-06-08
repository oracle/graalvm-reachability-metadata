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
import java.io.ObjectStreamConstants;
import java.io.Serializable;
import java.math.BigInteger;

import org.bouncycastle.jce.provider.JCEDHPublicKey;
import org.junit.jupiter.api.Test;

public class JCEDHPublicKeyTest {
    private static final String JCE_DH_PUBLIC_KEY_CLASS = "org.bouncycastle.jce.provider.JCEDHPublicKey";
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger Y = BigInteger.valueOf(8L);
    private static final int L = 4;

    @Test
    void javaDeserializationReadsDiffieHellmanPublicKeyParameters() throws Exception {
        JCEDHPublicKey publicKey = deserialize(initialSerializedKey());

        assertDiffieHellmanPublicKey(publicKey);
    }

    @Test
    void javaSerializationWritesDiffieHellmanPublicKeyParameters() throws Exception {
        JCEDHPublicKey publicKey = deserialize(initialSerializedKey());

        byte[] serialized = serialize(publicKey);
        JCEDHPublicKey restored = deserialize(serialized);

        assertDiffieHellmanPublicKey(restored);
    }

    private static void assertDiffieHellmanPublicKey(JCEDHPublicKey publicKey) {
        assertEquals(JCE_DH_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals("DH", publicKey.getAlgorithm());
        assertEquals("X.509", publicKey.getFormat());
        assertEquals(Y, publicKey.getY());
        assertEquals(P, publicKey.getParams().getP());
        assertEquals(G, publicKey.getParams().getG());
        assertEquals(L, publicKey.getParams().getL());
    }

    private static byte[] initialSerializedKey() throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new JCEDHPublicKeyObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(new SerializedJceDhPublicKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] serialize(JCEDHPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static JCEDHPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (JCEDHPublicKey)objectInputStream.readObject();
        }
    }

    private static final class SerializedJceDhPublicKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream outputStream) throws IOException {
            outputStream.writeObject(Y);
            outputStream.writeObject(P);
            outputStream.writeObject(G);
            outputStream.writeInt(L);
        }
    }

    private static final class JCEDHPublicKeyObjectOutputStream extends ObjectOutputStream {
        private static final byte SERIALIZABLE_CLASS_WITH_WRITE_OBJECT =
            ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_WRITE_METHOD;

        private JCEDHPublicKeyObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJceDhPublicKey.class.getName().equals(descriptor.getName())) {
                writeUTF(JCE_DH_PUBLIC_KEY_CLASS);
                writeLong(ObjectStreamClass.lookupAny(JCEDHPublicKey.class).getSerialVersionUID());
                writeByte(SERIALIZABLE_CLASS_WITH_WRITE_OBJECT);
                writeShort(0);
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }
}
