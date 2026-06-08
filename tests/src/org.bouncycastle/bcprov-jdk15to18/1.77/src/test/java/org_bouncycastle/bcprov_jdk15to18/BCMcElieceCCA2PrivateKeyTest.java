/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.SecureRandom;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.crypto.util.JournaledAlgorithm;
import org.bouncycastle.crypto.util.JournalingSecureRandom;
import org.bouncycastle.pqc.jcajce.provider.mceliece.BCMcElieceCCA2PrivateKey;
import org.bouncycastle.pqc.jcajce.provider.mceliece.McElieceCCA2KeyPairGeneratorSpi;
import org.bouncycastle.pqc.jcajce.spec.McElieceCCA2KeyGenParameterSpec;
import org.junit.jupiter.api.Test;

public class BCMcElieceCCA2PrivateKeyTest {
    @Test
    void javaSerializationPreservesMcElieceCca2PrivateKeyEncoding() throws Exception {
        BCMcElieceCCA2PrivateKey privateKey = createPrivateKey();

        byte[] serialized = serialize(privateKey);
        BCMcElieceCCA2PrivateKey restored = deserialize(serialized);

        assertEquals("McEliece-CCA2", privateKey.getAlgorithm());
        assertEquals(privateKey.getAlgorithm(), restored.getAlgorithm());
        assertEquals("PKCS#8", restored.getFormat());
        assertEquals(privateKey.getN(), restored.getN());
        assertEquals(privateKey.getK(), restored.getK());
        assertEquals(privateKey.getT(), restored.getT());
        assertEquals(privateKey.getField(), restored.getField());
        assertEquals(privateKey.getGoppaPoly(), restored.getGoppaPoly());
        assertEquals(privateKey.getP(), restored.getP());
        assertEquals(privateKey.getH(), restored.getH());
        assertArrayEquals(privateKey.getEncoded(), restored.getEncoded());
        assertEquals(privateKey, restored);
        assertEquals(privateKey.hashCode(), restored.hashCode());
    }

    private static BCMcElieceCCA2PrivateKey createPrivateKey() throws Exception {
        McElieceCCA2KeyPairGeneratorSpi generator = new McElieceCCA2KeyPairGeneratorSpi();
        generator.initialize(
            new McElieceCCA2KeyGenParameterSpec(4, 1, McElieceCCA2KeyGenParameterSpec.SHA256),
            new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = generator.generateKeyPair();
        return assertInstanceOf(BCMcElieceCCA2PrivateKey.class, keyPair.getPrivate());
    }

    private static byte[] serialize(BCMcElieceCCA2PrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (EncodedPrivateKeyReplacingObjectOutputStream objectOutputStream =
                new EncodedPrivateKeyReplacingObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
            assertEquals(1, objectOutputStream.replacedEncodedKeys);
        }
        return byteOutputStream.toByteArray();
    }

    private static BCMcElieceCCA2PrivateKey deserialize(byte[] serialized) throws Exception {
        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(serialized);
        try (EncodedPrivateKeyResolvingObjectInputStream objectInputStream =
                new EncodedPrivateKeyResolvingObjectInputStream(byteInputStream)) {
            BCMcElieceCCA2PrivateKey restored =
                (BCMcElieceCCA2PrivateKey)objectInputStream.readObject();
            assertEquals(1, objectInputStream.resolvedEncodedKeys);
            return restored;
        }
    }

    private static JournaledAlgorithm createMarker(byte[] encodedPrivateKey) {
        AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(
            new ASN1ObjectIdentifier("1.2.840.113549.3.7"));
        JournalingSecureRandom journalingSecureRandom = new JournalingSecureRandom(
            encodedPrivateKey, new SecureRandom(new byte[] {9, 8, 7, 6}));
        return new JournaledAlgorithm(algorithmIdentifier, journalingSecureRandom);
    }

    private static final class EncodedPrivateKeyReplacingObjectOutputStream
            extends ObjectOutputStream {
        private int replacedEncodedKeys;

        private EncodedPrivateKeyReplacingObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (replacedEncodedKeys == 0 && object instanceof byte[]) {
                replacedEncodedKeys++;
                return createMarker((byte[])object);
            }
            return super.replaceObject(object);
        }
    }

    private static final class EncodedPrivateKeyResolvingObjectInputStream
            extends ObjectInputStream {
        private int resolvedEncodedKeys;

        private EncodedPrivateKeyResolvingObjectInputStream(ByteArrayInputStream inputStream)
                throws IOException {
            super(inputStream);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) throws IOException {
            if (object instanceof JournaledAlgorithm) {
                resolvedEncodedKeys++;
                return ((JournaledAlgorithm)object).getJournalingSecureRandom().getFullTranscript();
            }
            return super.resolveObject(object);
        }
    }
}
