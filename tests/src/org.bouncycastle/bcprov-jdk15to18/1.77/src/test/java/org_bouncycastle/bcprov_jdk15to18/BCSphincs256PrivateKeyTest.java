/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.PrivateKey;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.pqc.crypto.sphincs.SPHINCSPrivateKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey;
import org.junit.jupiter.api.Test;

public class BCSphincs256PrivateKeyTest {
    private static final int SPHINCS_PRIVATE_KEY_SIZE = 1088;

    @Test
    void javaSerializationPreservesSphincsPrivateKeyEncoding() throws Exception {
        BCSphincs256PrivateKey privateKey = createPrivateKey();

        assertSerializationRoundTrip(privateKey);
    }

    @Test
    void javaSerializationPreservesDecodedSphincsPrivateKeyEncoding() throws Exception {
        BCSphincs256PrivateKey generatedKey = createPrivateKey();
        PrivateKeyInfo keyInfo = PrivateKeyInfo.getInstance(generatedKey.getEncoded());
        BCSphincs256PrivateKey decodedKey = new BCSphincs256PrivateKey(keyInfo);

        assertSerializationRoundTrip(decodedKey);
    }

    @Test
    void standardJavaSerializationPreservesSphincsPrivateKeyThroughPrivateKeyApi()
            throws Exception {
        BCSphincs256PrivateKey privateKey = createPrivateKey();
        PrivateKey restored = (PrivateKey)deserializeObject(serializeObject(privateKey));

        assertEquals("SPHINCS-256", restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static void assertSerializationRoundTrip(BCSphincs256PrivateKey privateKey)
            throws Exception {
        byte[] serialized = serialize(privateKey);
        BCSphincs256PrivateKey restored = deserialize(serialized, privateKey.getEncoded());

        assertEquals("SPHINCS-256", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertArrayEquals(privateKey.getKeyData(), restored.getKeyData());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCSphincs256PrivateKey createPrivateKey() {
        return new BCSphincs256PrivateKey(
                NISTObjectIdentifiers.id_sha3_256,
                new SPHINCSPrivateKeyParameters(sequence(SPHINCS_PRIVATE_KEY_SIZE)));
    }

    private static byte[] sequence(int length) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(i + 1);
        }
        return values;
    }

    private static byte[] serialize(BCSphincs256PrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream =
                new EncodedPrivateKeyReplacingObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCSphincs256PrivateKey deserialize(
            byte[] serialized,
            byte[] expectedEncoded) throws Exception {
        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(serialized);
        try (ObjectInputStream objectInputStream = new EncodedPrivateKeyResolvingObjectInputStream(
                byteInputStream)) {
            BCSphincs256PrivateKey privateKey =
                    (BCSphincs256PrivateKey)objectInputStream.readObject();
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

    private static final class EncodedPrivateKeyReplacingObjectOutputStream
            extends ObjectOutputStream {
        private boolean replacedEncodedKey;

        private EncodedPrivateKeyReplacingObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (!replacedEncodedKey && object instanceof byte[]) {
                replacedEncodedKey = true;
                return new BCSphincs256PrivateKey(PrivateKeyInfo.getInstance((byte[])object));
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPrivateKeyResolvingObjectInputStream
            extends ObjectInputStream {
        private boolean resolvedNestedKey;

        private EncodedPrivateKeyResolvingObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (!resolvedNestedKey && object instanceof BCSphincs256PrivateKey) {
                resolvedNestedKey = true;
                return ((BCSphincs256PrivateKey)object).getEncoded();
            }
            return super.resolveObject(object);
        }
    }
}
