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
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import org.bouncycastle.asn1.bc.BCObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.pqc.crypto.cmce.CMCEParameters;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.cmce.BCCMCEPublicKey;
import org.junit.jupiter.api.Test;

public class BCCMCEPublicKeyTest {
    @Test
    void javaSerializationPreservesCmcePublicKeyEncoding() throws Exception {
        BCCMCEPublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(publicKey);
        BCCMCEPublicKey restored = deserialize(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    @Test
    void javaUnsharedSerializationPreservesCmcePublicKeyEncoding() throws Exception {
        BCCMCEPublicKey publicKey = createPublicKey();

        byte[] serialized = serializeUnshared(publicKey);
        BCCMCEPublicKey restored = deserializeUnshared(serialized);

        assertPublicKeyRoundTrip(publicKey, restored);
    }

    private static void assertPublicKeyRoundTrip(BCCMCEPublicKey publicKey,
            BCCMCEPublicKey restored) {
        assertEquals("MCELIECE348864", publicKey.getAlgorithm());
        assertEquals(publicKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("X.509", restored.getFormat());
        assertEquals(publicKey.getParameterSpec().getName(),
                restored.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
        assertEquals(publicKey, restored);
        assertEquals(publicKey.hashCode(), restored.hashCode());
    }

    private static BCCMCEPublicKey createPublicKey() throws Exception {
        CMCEParameters parameters = CMCEParameters.mceliece348864r3;
        int publicKeySize = parameters.getT() * parameters.getM()
                * (parameters.getN() - parameters.getT() * parameters.getM()) / 8;
        byte[] publicKey = sequence(publicKeySize, 53);
        SubjectPublicKeyInfo keyInfo = new SubjectPublicKeyInfo(
                new AlgorithmIdentifier(BCObjectIdentifiers.mceliece348864_r3), publicKey);
        KeyFactory keyFactory = KeyFactory.getInstance("CMCE", new BouncyCastlePQCProvider());
        return (BCCMCEPublicKey)keyFactory.generatePublic(
                new X509EncodedKeySpec(keyInfo.getEncoded()));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCCMCEPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        EncodedPublicKeyReplacingStream objectOutputStream = new EncodedPublicKeyReplacingStream(
                byteOutputStream, publicKey.getEncoded());
        try (objectOutputStream) {
            objectOutputStream.writeObject(publicKey);
        }
        assertTrue(objectOutputStream.replacedEncodedKey);
        return byteOutputStream.toByteArray();
    }

    private static BCCMCEPublicKey deserialize(byte[] serialized) throws Exception {
        EncodedPublicKeyResolvingStream objectInputStream = new EncodedPublicKeyResolvingStream(
                new ByteArrayInputStream(serialized));
        try (objectInputStream) {
            BCCMCEPublicKey publicKey = (BCCMCEPublicKey)objectInputStream.readObject();
            assertTrue(objectInputStream.resolvedEncodedKey);
            return publicKey;
        }
    }

    private static byte[] serializeUnshared(BCCMCEPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeUnshared(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCCMCEPublicKey deserializeUnshared(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (BCCMCEPublicKey)objectInputStream.readUnshared();
        }
    }

    private static final class EncodedPublicKeyReplacingStream extends ObjectOutputStream {
        private final byte[] expectedEncoding;
        private boolean replacedEncodedKey;

        private EncodedPublicKeyReplacingStream(ByteArrayOutputStream outputStream,
                byte[] expectedEncoding) throws IOException {
            super(outputStream);
            this.expectedEncoding = expectedEncoding;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey && object instanceof byte[]
                    && Arrays.equals(expectedEncoding, (byte[])object)) {
                replacedEncodedKey = true;
                return new EncodedPublicKey((byte[])object);
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPublicKeyResolvingStream extends ObjectInputStream {
        private boolean resolvedEncodedKey;

        private EncodedPublicKeyResolvingStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedEncodedKey && object instanceof EncodedPublicKey) {
                resolvedEncodedKey = true;
                return ((EncodedPublicKey)object).toByteArray();
            }
            return super.resolveObject(object);
        }
    }

    private static final class EncodedPublicKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int[] encodedKey;

        private EncodedPublicKey(byte[] encodedKey) {
            this.encodedKey = new int[encodedKey.length];
            for (int i = 0; i < encodedKey.length; i++) {
                this.encodedKey[i] = encodedKey[i] & 0xff;
            }
        }

        private byte[] toByteArray() {
            byte[] bytes = new byte[encodedKey.length];
            for (int i = 0; i < encodedKey.length; i++) {
                bytes[i] = (byte)encodedKey[i];
            }
            return bytes;
        }
    }
}
