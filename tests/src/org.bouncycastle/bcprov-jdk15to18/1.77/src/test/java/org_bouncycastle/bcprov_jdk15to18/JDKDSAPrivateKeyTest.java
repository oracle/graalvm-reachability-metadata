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
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.DSAPrivateKey;
import java.util.Hashtable;
import java.util.Vector;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.jce.provider.JDKDSAPrivateKey;
import org.junit.jupiter.api.Test;

public class JDKDSAPrivateKeyTest {
    private static final String JDK_DSA_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jce.provider.JDKDSAPrivateKey";
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger Q = BigInteger.valueOf(11L);
    private static final BigInteger G = BigInteger.valueOf(2L);
    private static final BigInteger X = BigInteger.valueOf(3L);
    private static final ASN1ObjectIdentifier FRIENDLY_NAME_OID =
        new ASN1ObjectIdentifier("1.2.840.113549.1.9.20");
    private static final DERUTF8String FRIENDLY_NAME = new DERUTF8String("test-key");

    @Test
    void javaSerializationPreservesDsaPrivateKeyParameters() throws Exception {
        JDKDSAPrivateKey privateKey = deserializeJdkDsaPrivateKey(createSerializedKey());
        privateKey.setBagAttribute(FRIENDLY_NAME_OID, FRIENDLY_NAME);

        byte[] serialized = serialize(privateKey);
        DSAPrivateKey restored = deserializeJdkDsaPrivateKey(serialized);

        assertEquals(JDK_DSA_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertEquals(JDK_DSA_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertEquals("DSA", restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(X, restored.getX());
        assertEquals(P, restored.getParams().getP());
        assertEquals(Q, restored.getParams().getQ());
        assertEquals(G, restored.getParams().getG());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
        assertNotNull(restored.getEncoded());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(FRIENDLY_NAME, privateKey.getBagAttribute(FRIENDLY_NAME_OID));
        JDKDSAPrivateKey restoredJdkKey = (JDKDSAPrivateKey)restored;
        assertEquals(FRIENDLY_NAME, restoredJdkKey.getBagAttribute(FRIENDLY_NAME_OID));
    }

    private static byte[] createSerializedKey() throws Exception {
        byte[] serialized = serialize(new SerializedJdkDsaPrivateKey());
        return replaceSerializedClassDescriptor(
            serialized,
            SerializedJdkDsaPrivateKey.class.getName(),
            JDK_DSA_PRIVATE_KEY_CLASS,
            ObjectStreamClass.lookup(JDKDSAPrivateKey.class).getSerialVersionUID());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(value);
        }
        return byteOutputStream.toByteArray();
    }

    private static JDKDSAPrivateKey deserializeJdkDsaPrivateKey(byte[] serialized)
            throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (JDKDSAPrivateKey)objectInputStream.readObject();
        }
    }

    private static byte[] replaceSerializedClassDescriptor(
            byte[] serialized,
            String originalClassName,
            String replacementClassName,
            long replacementSerialVersionUid) {
        byte[] originalName = originalClassName.getBytes(StandardCharsets.UTF_8);
        byte[] replacementName = replacementClassName.getBytes(StandardCharsets.UTF_8);
        int nameOffset = indexOf(serialized, originalName);

        assertTrue(nameOffset > 1, "serialized class descriptor was not found");
        int encodedLength = ((serialized[nameOffset - 2] & 0xff) << 8)
            | (serialized[nameOffset - 1] & 0xff);
        assertEquals(originalName.length, encodedLength);

        ByteArrayOutputStream renamed = new ByteArrayOutputStream(
            serialized.length - originalName.length + replacementName.length);
        renamed.write(serialized, 0, nameOffset - 2);
        renamed.write((replacementName.length >>> 8) & 0xff);
        renamed.write(replacementName.length & 0xff);
        renamed.write(replacementName, 0, replacementName.length);
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            renamed.write((byte)(replacementSerialVersionUid >>> shift));
        }
        renamed.write(
            serialized,
            nameOffset + originalName.length + Long.BYTES,
            serialized.length - nameOffset - originalName.length - Long.BYTES);
        return renamed.toByteArray();
    }

    private static int indexOf(byte[] bytes, byte[] pattern) {
        for (int i = 0; i <= bytes.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (bytes[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    private static final class SerializedJdkDsaPrivateKey implements Serializable {
        private static final long serialVersionUID = -4677259546958385734L;

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(X);
            out.writeObject(P);
            out.writeObject(Q);
            out.writeObject(G);
            out.writeObject(new Hashtable<Object, Object>());
            out.writeObject(new Vector<Object>());
        }
    }
}
