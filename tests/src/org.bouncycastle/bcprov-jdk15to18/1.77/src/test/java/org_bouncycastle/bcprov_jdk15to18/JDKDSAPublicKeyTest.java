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
import java.io.ObjectStreamClass;
import java.io.ObjectStreamConstants;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.interfaces.DSAPublicKey;

import org.bouncycastle.jce.provider.JDKDSAPublicKey;
import org.junit.jupiter.api.Test;

public class JDKDSAPublicKeyTest {
    private static final String JDK_DSA_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jce.provider.JDKDSAPublicKey";
    private static final byte SERIALIZABLE_CLASS_WITH_WRITE_OBJECT =
        ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_WRITE_METHOD;
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger Q = BigInteger.valueOf(11L);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger Y = BigInteger.valueOf(8L);

    @Test
    void javaDeserializationReadsDsaPublicKeyParameters() throws Exception {
        JDKDSAPublicKey publicKey = deserializeJdkDsaPublicKey(initialSerializedKey());

        assertDsaPublicKey(publicKey);
        assertTrue(publicKey.toString().contains(Y.toString(16)));
        assertNotNull(publicKey.getEncoded());
    }

    @Test
    void javaSerializationWritesDsaPublicKeyParameters() throws Exception {
        JDKDSAPublicKey publicKey = deserializeJdkDsaPublicKey(initialSerializedKey());

        byte[] serialized = serialize(publicKey);
        DSAPublicKey restored = deserializeJdkDsaPublicKey(serialized);

        assertDsaPublicKey(restored);
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
    }

    private static void assertDsaPublicKey(DSAPublicKey publicKey) {
        assertEquals(JDK_DSA_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals("DSA", publicKey.getAlgorithm());
        assertEquals("X.509", publicKey.getFormat());
        assertEquals(Y, publicKey.getY());
        assertEquals(P, publicKey.getParams().getP());
        assertEquals(Q, publicKey.getParams().getQ());
        assertEquals(G, publicKey.getParams().getG());
    }

    private static byte[] initialSerializedKey() throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new JDKDSAPublicKeyObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(new SerializedJdkDsaPublicKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(value);
        }
        return byteOutputStream.toByteArray();
    }

    private static JDKDSAPublicKey deserializeJdkDsaPublicKey(byte[] serialized)
            throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (JDKDSAPublicKey)objectInputStream.readObject();
        }
    }

    private static final class SerializedJdkDsaPublicKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(Y);
            out.writeObject(P);
            out.writeObject(Q);
            out.writeObject(G);
        }
    }

    private static final class JDKDSAPublicKeyObjectOutputStream extends ObjectOutputStream {
        private JDKDSAPublicKeyObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJdkDsaPublicKey.class.getName().equals(descriptor.getName())) {
                writeUTF(JDK_DSA_PUBLIC_KEY_CLASS);
                writeLong(ObjectStreamClass.lookupAny(JDKDSAPublicKey.class).getSerialVersionUID());
                writeByte(SERIALIZABLE_CLASS_WITH_WRITE_OBJECT);
                writeShort(0);
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }
}
