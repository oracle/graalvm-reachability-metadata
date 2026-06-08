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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.bouncycastle.jce.provider.JCEElGamalPublicKey;
import org.junit.jupiter.api.Test;

public class JCEElGamalPublicKeyTest {
    private static final String JCE_ELGAMAL_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jce.provider.JCEElGamalPublicKey";
    private static final byte[] SERIALIZED_PUBLIC_KEY = Base64.getDecoder().decode(
        "rO0ABXNyADFvcmcuYm91bmN5Y2FzdGxlLmpjZS5wcm92aWRlci5KQ0VFbEdhbWFsUHVibGljS2V5eOnUVVUsZjQD"
            + "AAB4cHNyABRqYXZhLm1hdGguQmlnSW50ZWdlcoz8nx+pO/sdAwAGSQAIYml0Q291bnRJAAliaXRMZW5ndGhJABNm"
            + "aXJzdE5vbnplcm9CeXRlTnVtSQAMbG93ZXN0U2V0Qml0SQAGc2lnbnVtWwAJbWFnbml0dWRldAACW0J4cgAQamF2"
            + "YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHD///////////////7////+AAAAAXVyAAJbQqzzF/gGCFTgAgAAeHAA"
            + "AAABCHhzcQB+AAL///////////////7////+AAAAAXVxAH4ABgAAAAEXeHNxAH4AAv///////////////v////4A"
            + "AAABdXEAfgAGAAAAAQV4eA==");
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger Y = BigInteger.valueOf(8L);

    @Test
    void javaDeserializationReadsElGamalPublicKeyParameters() throws Exception {
        JCEElGamalPublicKey publicKey = deserialize(initialSerializedKey());

        assertElGamalPublicKey(publicKey);
    }

    @Test
    void javaSerializationWritesElGamalPublicKeyParameters() throws Exception {
        JCEElGamalPublicKey publicKey = deserialize(initialSerializedKey());

        byte[] serialized = serialize(publicKey);
        JCEElGamalPublicKey restored = deserialize(serialized);

        assertElGamalPublicKey(restored);
        assertArrayEquals(publicKey.getEncoded(), restored.getEncoded());
    }

    @Test
    void javaSerializationPreservesElGamalPublicKeyAsNestedObject() throws Exception {
        JCEElGamalPublicKey publicKey = deserialize(initialSerializedKey());
        KeyEnvelope keyEnvelope = new KeyEnvelope(publicKey);

        KeyEnvelope restored = (KeyEnvelope)deserializeObject(serialize(keyEnvelope));

        assertElGamalPublicKey(restored.publicKey);
        assertArrayEquals(publicKey.getEncoded(), restored.publicKey.getEncoded());
    }

    @Test
    void javaSerializationProcessesElGamalPublicKeyParameterObjects() throws Exception {
        JCEElGamalPublicKey publicKey = deserialize(initialSerializedKey());

        TrackingObjectOutputStream outputStream = new TrackingObjectOutputStream();
        outputStream.writeObject(publicKey);
        outputStream.close();

        TrackingObjectInputStream inputStream = new TrackingObjectInputStream(
            outputStream.toByteArray());
        JCEElGamalPublicKey restored = (JCEElGamalPublicKey)inputStream.readObject();
        inputStream.close();

        assertElGamalPublicKey(restored);
        assertTrue(outputStream.bigIntegers.contains(Y));
        assertTrue(outputStream.bigIntegers.contains(P));
        assertTrue(outputStream.bigIntegers.contains(G));
        assertTrue(inputStream.bigIntegers.contains(Y));
        assertTrue(inputStream.bigIntegers.contains(P));
        assertTrue(inputStream.bigIntegers.contains(G));
    }

    private static void assertElGamalPublicKey(JCEElGamalPublicKey publicKey) {
        assertEquals(JCE_ELGAMAL_PUBLIC_KEY_CLASS, publicKey.getClass().getName());
        assertEquals("ElGamal", publicKey.getAlgorithm());
        assertEquals("X.509", publicKey.getFormat());
        assertEquals(Y, publicKey.getY());
        assertEquals(P, publicKey.getParameters().getP());
        assertEquals(G, publicKey.getParameters().getG());
        assertEquals(P, publicKey.getParams().getP());
        assertEquals(G, publicKey.getParams().getG());
    }

    private static byte[] initialSerializedKey() {
        return SERIALIZED_PUBLIC_KEY.clone();
    }

    private static byte[] serialize(Object object) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(object);
        }
        return byteOutputStream.toByteArray();
    }

    private static JCEElGamalPublicKey deserialize(byte[] serialized) throws Exception {
        return (JCEElGamalPublicKey)deserializeObject(serialized);
    }

    private static Object deserializeObject(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }

    private static final class KeyEnvelope implements Serializable {
        private static final long serialVersionUID = 1L;

        private final JCEElGamalPublicKey publicKey;

        private KeyEnvelope(JCEElGamalPublicKey publicKey) {
            this.publicKey = publicKey;
        }
    }

    private static final class TrackingObjectOutputStream extends ObjectOutputStream {
        private final ByteArrayOutputStream byteOutputStream;
        private final List<BigInteger> bigIntegers = new ArrayList<>();

        private TrackingObjectOutputStream() throws Exception {
            this(new ByteArrayOutputStream());
        }

        private TrackingObjectOutputStream(ByteArrayOutputStream byteOutputStream) throws Exception {
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
