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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamConstants;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.JCEECPublicKey;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Test;

public class JCEECPublicKeyTest {
    private static final String JCEEC_PUBLIC_KEY_CLASS =
        "org.bouncycastle.jce.provider.JCEECPublicKey";
    private static final BigInteger PUBLIC_POINT_MULTIPLIER = BigInteger.valueOf(3L);

    @Test
    void javaSerializationWritesAndReadsJceEcPublicKeyParameters() throws Exception {
        JCEECPublicKey publicKey = generatePublicKey();

        ECPublicKey restored = deserialize(serialize(publicKey));

        assertJceEcPublicKey(publicKey, restored);
    }

    @Test
    void javaUnsharedSerializationWritesAndReadsJceEcPublicKeyParameters() throws Exception {
        JCEECPublicKey publicKey = generatePublicKey();

        ECPublicKey restored = deserializeUnshared(serializeUnshared(publicKey));

        assertJceEcPublicKey(publicKey, restored);
    }

    @Test
    void javaDeserializationReadsJceEcPublicKeyDataFromCompatibleStream() throws Exception {
        JCEECPublicKey publicKey = generatePublicKey();

        ECPublicKey restored = deserialize(compatibleSerializedKey(publicKey));

        assertJceEcPublicKey(publicKey, restored);
    }

    @Test
    void javaSerializationUsesJceEcPublicKeyFormForSerializableSubclass() throws Exception {
        SerializableJceEcPublicKey publicKey = new SerializableJceEcPublicKey();

        ECPublicKey restored = deserialize(serialize(publicKey));

        assertEquals(SerializableJceEcPublicKey.class, restored.getClass());
        assertKeyState(publicKey, restored);
    }

    private static JCEECPublicKey generatePublicKey() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
        ECPoint publicPoint = curveSpec.getG().multiply(PUBLIC_POINT_MULTIPLIER).normalize();
        return new JCEECPublicKey("EC", new ECPublicKeySpec(publicPoint, curveSpec));
    }

    private static byte[] serialize(JCEECPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] serializeUnshared(JCEECPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeUnshared(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] compatibleSerializedKey(JCEECPublicKey publicKey) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new JCEECPublicKeyObjectOutputStream(
                byteOutputStream)) {
            objectOutputStream.writeObject(new SerializedJceEcPublicKey(publicKey));
        }
        return byteOutputStream.toByteArray();
    }

    private static ECPublicKey deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (ECPublicKey)objectInputStream.readObject();
        }
    }

    private static ECPublicKey deserializeUnshared(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (ECPublicKey)objectInputStream.readUnshared();
        }
    }

    private static void assertJceEcPublicKey(
            JCEECPublicKey expected,
            ECPublicKey actual) {
        assertEquals(JCEEC_PUBLIC_KEY_CLASS, expected.getClass().getName());
        assertEquals(JCEEC_PUBLIC_KEY_CLASS, actual.getClass().getName());
        assertKeyState(expected, actual);
    }

    private static void assertKeyState(
            JCEECPublicKey expected,
            ECPublicKey actual) {
        assertEquals(expected.getAlgorithm(), actual.getAlgorithm());
        assertEquals(expected.getFormat(), actual.getFormat());
        assertEquals(expected.getW(), actual.getW());
        assertParameters(expected.getParams(), actual.getParams());
        assertArrayEquals(expected.getEncoded(), actual.getEncoded());
    }

    private static void assertParameters(
            ECParameterSpec expected,
            ECParameterSpec actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getOrder(), actual.getOrder());
        assertEquals(expected.getCofactor(), actual.getCofactor());
        assertEquals(expected.getCurve(), actual.getCurve());
        assertEquals(expected.getGenerator(), actual.getGenerator());
    }

    private static final class SerializableJceEcPublicKey extends JCEECPublicKey {
        private static final long serialVersionUID = 1L;

        private SerializableJceEcPublicKey() {
            super("EC", new ECPublicKeySpec(
                createPublicPoint(),
                ECNamedCurveTable.getParameterSpec("secp256r1")));
        }
    }

    private static final class SerializedJceEcPublicKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final byte[] encoded;
        private final String algorithm;

        private SerializedJceEcPublicKey(JCEECPublicKey publicKey) {
            this.encoded = publicKey.getEncoded();
            this.algorithm = publicKey.getAlgorithm();
        }

        private void writeObject(ObjectOutputStream outputStream) throws IOException {
            outputStream.writeObject(encoded);
            outputStream.writeObject(algorithm);
            outputStream.writeBoolean(false);
        }
    }

    private static final class JCEECPublicKeyObjectOutputStream extends ObjectOutputStream {
        private static final byte SERIALIZABLE_CLASS_WITH_WRITE_OBJECT =
            ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_WRITE_METHOD;

        private JCEECPublicKeyObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJceEcPublicKey.class.getName().equals(descriptor.getName())) {
                writeUTF(JCEEC_PUBLIC_KEY_CLASS);
                long serialVersionUid = ObjectStreamClass.lookupAny(
                    JCEECPublicKey.class).getSerialVersionUID();
                writeLong(serialVersionUid);
                writeByte(SERIALIZABLE_CLASS_WITH_WRITE_OBJECT);
                writeShort(0);
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }

    private static ECPoint createPublicPoint() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
        return curveSpec.getG().multiply(PUBLIC_POINT_MULTIPLIER).normalize();
    }
}
