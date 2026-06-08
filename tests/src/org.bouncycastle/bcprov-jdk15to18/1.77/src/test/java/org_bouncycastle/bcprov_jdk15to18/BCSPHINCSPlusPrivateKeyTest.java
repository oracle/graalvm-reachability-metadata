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
import java.security.PrivateKey;
import java.util.Arrays;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusParameters;
import org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.sphincsplus.BCSPHINCSPlusPrivateKey;
import org.junit.jupiter.api.Test;

public class BCSPHINCSPlusPrivateKeyTest {
    private static final int SPHINCS_PLUS_COMPONENT_SIZE = 16;

    @Test
    void javaSerializationPreservesSphincsPlusPrivateKeyEncoding() throws Exception {
        BCSPHINCSPlusPrivateKey privateKey = createPrivateKey();

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void javaSerializationPreservesDecodedSphincsPlusPrivateKeyEncoding() throws Exception {
        BCSPHINCSPlusPrivateKey generatedKey = createPrivateKey();
        PrivateKeyInfo keyInfo = PrivateKeyInfo.getInstance(generatedKey.getEncoded());
        BCSPHINCSPlusPrivateKey decodedKey = new BCSPHINCSPlusPrivateKey(keyInfo);

        assertSerializationRoundTrip(decodedKey);
    }

    @Test
    void standardJavaSerializationPreservesSphincsPlusPrivateKeyThroughPrivateKeyApi()
            throws Exception {
        BCSPHINCSPlusPrivateKey privateKey = createPrivateKey();
        PrivateKey restored = (PrivateKey)deserializeObject(serializeObject(privateKey));

        assertEquals("SPHINCS+", restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static void assertSerializationRoundTrip(BCSPHINCSPlusPrivateKey privateKey)
            throws Exception {
        byte[] serialized = serialize(privateKey);
        BCSPHINCSPlusPrivateKey restored = deserialize(serialized, privateKey.getEncoded());

        assertEquals("SPHINCS+", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(
                privateKey.getParameterSpec().getName(),
                restored.getParameterSpec().getName());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertArrayEquals(
                privateKey.getPublicKey().getEncoded(),
                restored.getPublicKey().getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCSPHINCSPlusPrivateKey createPrivateKey() {
        return new BCSPHINCSPlusPrivateKey(new SPHINCSPlusPrivateKeyParameters(
                SPHINCSPlusParameters.sha2_128f_robust,
                sequence(SPHINCS_PLUS_COMPONENT_SIZE, 1),
                sequence(SPHINCS_PLUS_COMPONENT_SIZE, 33),
                sequence(SPHINCS_PLUS_COMPONENT_SIZE, 65),
                sequence(SPHINCS_PLUS_COMPONENT_SIZE, 97)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCSPHINCSPlusPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedKeyOutputStream objectOutputStream =
                new EncodedKeyOutputStream(byteOutputStream, privateKey.getEncoded())) {
            objectOutputStream.writeObject(privateKey);
            assertTrue(objectOutputStream.replacedEncodedKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCSPHINCSPlusPrivateKey deserialize(
            byte[] serialized,
            byte[] expectedEncoded) throws Exception {
        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(serialized);
        try (EncodedKeyInputStream objectInputStream =
                new EncodedKeyInputStream(byteInputStream, expectedEncoded)) {
            BCSPHINCSPlusPrivateKey privateKey =
                    (BCSPHINCSPlusPrivateKey)objectInputStream.readObject();
            assertTrue(objectInputStream.resolvedNestedKey);
            assertArrayEquals(expectedEncoded, privateKey.getEncoded());
            return privateKey;
        }
    }

    private static byte[] serializeObject(Object object) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(object);
        }
        return byteOutputStream.toByteArray();
    }

    private static Object deserializeObject(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }

    private static final class EncodedKeyOutputStream extends ObjectOutputStream {
        private final byte[] expectedEncoded;
        private boolean replacedEncodedKey;

        private EncodedKeyOutputStream(
                ByteArrayOutputStream outputStream,
                byte[] expectedEncoded) throws IOException {
            super(outputStream);
            this.expectedEncoded = expectedEncoded.clone();
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey
                    && object instanceof byte[]
                    && Arrays.equals(expectedEncoded, (byte[])object)) {
                replacedEncodedKey = true;
                return new BCSPHINCSPlusPrivateKey(PrivateKeyInfo.getInstance((byte[])object));
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedKeyInputStream extends ObjectInputStream {
        private final byte[] expectedEncoded;
        private boolean resolvedNestedKey;

        private EncodedKeyInputStream(
                ByteArrayInputStream inputStream,
                byte[] expectedEncoded) throws IOException {
            super(inputStream);
            this.expectedEncoded = expectedEncoded.clone();
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedNestedKey && object instanceof BCSPHINCSPlusPrivateKey) {
                resolvedNestedKey = true;
                byte[] encoded = ((BCSPHINCSPlusPrivateKey)object).getEncoded();
                assertArrayEquals(expectedEncoded, encoded);
                return encoded;
            }
            return super.resolveObject(object);
        }
    }
}
