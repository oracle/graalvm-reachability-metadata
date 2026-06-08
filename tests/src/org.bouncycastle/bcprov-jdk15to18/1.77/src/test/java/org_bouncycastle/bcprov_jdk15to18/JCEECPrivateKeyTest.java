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
import java.io.Serializable;
import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.ASN1UTF8String;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.JCEECPrivateKey;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.junit.jupiter.api.Test;

public class JCEECPrivateKeyTest {
    private static final String JCEEC_PRIVATE_KEY_CLASS =
        "org.bouncycastle.jce.provider.JCEECPrivateKey";
    private static final BigInteger PRIVATE_VALUE = BigInteger.valueOf(29L);
    private static final ASN1ObjectIdentifier BAG_ATTRIBUTE_OID =
        new ASN1ObjectIdentifier("1.2.840.113549.1.9.20");
    private static final String BAG_ATTRIBUTE_VALUE = "jce-ec-private-key";

    @Test
    void javaSerializationWritesAndReadsJceEcPrivateKeyParameters() throws Exception {
        JCEECPrivateKey privateKey = generatePrivateKey();

        ECPrivateKey restored = deserialize(serialize(privateKey));

        assertJceEcPrivateKey(privateKey, restored);
    }

    @Test
    void javaDeserializationReadsJceEcPrivateKeyDataFromCompatibleStream() throws Exception {
        JCEECPrivateKey privateKey = generatePrivateKey();

        ECPrivateKey restored = deserialize(compatibleSerializedKey(privateKey));

        assertJceEcPrivateKey(privateKey, restored);
    }

    @Test
    void javaSerializationUsesJceEcPrivateKeyFormForSerializableSubclass() throws Exception {
        SerializableJceEcPrivateKey privateKey = new SerializableJceEcPrivateKey();

        ECPrivateKey restored = deserialize(serialize(privateKey));

        assertEquals(SerializableJceEcPrivateKey.class, restored.getClass());
        assertKeyState(privateKey, restored);
        assertBagAttribute(restored);
    }

    private static JCEECPrivateKey generatePrivateKey() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
        JCEECPrivateKey privateKey = new JCEECPrivateKey(
            "EC",
            new ECPrivateKeySpec(PRIVATE_VALUE, curveSpec));
        privateKey.setBagAttribute(BAG_ATTRIBUTE_OID, new DERUTF8String(BAG_ATTRIBUTE_VALUE));
        return privateKey;
    }

    private static byte[] serialize(ECPrivateKey privateKey) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] compatibleSerializedKey(JCEECPrivateKey privateKey) throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new JCEECPrivateKeyObjectOutputStream(
                byteOutputStream)) {
            objectOutputStream.writeObject(new SerializedJceEcPrivateKey(privateKey));
        }
        return byteOutputStream.toByteArray();
    }

    private static ECPrivateKey deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (ECPrivateKey)objectInputStream.readObject();
        }
    }

    private static byte[] encodedBagAttributes() throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        ASN1OutputStream asn1OutputStream = ASN1OutputStream.create(byteOutputStream);
        asn1OutputStream.writeObject(BAG_ATTRIBUTE_OID);
        asn1OutputStream.writeObject(new DERUTF8String(BAG_ATTRIBUTE_VALUE));
        asn1OutputStream.close();
        return byteOutputStream.toByteArray();
    }

    private static void assertJceEcPrivateKey(
            JCEECPrivateKey expected,
            ECPrivateKey actual) {
        assertEquals(JCEEC_PRIVATE_KEY_CLASS, expected.getClass().getName());
        assertEquals(JCEEC_PRIVATE_KEY_CLASS, actual.getClass().getName());
        assertKeyState(expected, actual);
        assertBagAttribute(actual);
    }

    private static void assertKeyState(
            JCEECPrivateKey expected,
            ECPrivateKey actual) {
        assertEquals(expected.getAlgorithm(), actual.getAlgorithm());
        assertEquals(expected.getFormat(), actual.getFormat());
        assertEquals(expected.getS(), actual.getS());
        assertParameters(expected.getParams(), actual.getParams());
        assertArrayEquals(expected.getEncoded(), actual.getEncoded());
    }

    private static void assertBagAttribute(ECPrivateKey privateKey) {
        ASN1Encodable attribute = ((JCEECPrivateKey)privateKey).getBagAttribute(BAG_ATTRIBUTE_OID);

        assertEquals(BAG_ATTRIBUTE_VALUE, ASN1UTF8String.getInstance(attribute).getString());
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

    private static final class SerializableJceEcPrivateKey extends JCEECPrivateKey {
        private static final long serialVersionUID = 1L;

        private SerializableJceEcPrivateKey() {
            super("EC", new ECPrivateKeySpec(
                PRIVATE_VALUE,
                ECNamedCurveTable.getParameterSpec("secp256r1")));
            setBagAttribute(BAG_ATTRIBUTE_OID, new DERUTF8String(BAG_ATTRIBUTE_VALUE));
        }
    }

    private static final class SerializedJceEcPrivateKey implements Serializable {
        private static final long serialVersionUID = 1L;

        private final byte[] encoded;
        private final String algorithm;

        private SerializedJceEcPrivateKey(JCEECPrivateKey privateKey) {
            this.encoded = privateKey.getEncoded();
            this.algorithm = privateKey.getAlgorithm();
        }

        private void writeObject(ObjectOutputStream outputStream) throws IOException {
            outputStream.writeObject(encoded);
            outputStream.writeObject(algorithm);
            outputStream.writeBoolean(false);
            outputStream.writeObject(encodedBagAttributes());
        }
    }

    private static final class JCEECPrivateKeyObjectOutputStream extends ObjectOutputStream {
        private static final byte SERIALIZABLE_CLASS_WITH_WRITE_OBJECT = 0x03;

        private JCEECPrivateKeyObjectOutputStream(ByteArrayOutputStream outputStream)
                throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJceEcPrivateKey.class.getName().equals(descriptor.getName())) {
                writeUTF(JCEEC_PRIVATE_KEY_CLASS);
                writeLong(ObjectStreamClass.lookup(JCEECPrivateKey.class).getSerialVersionUID());
                writeByte(SERIALIZABLE_CLASS_WITH_WRITE_OBJECT);
                writeShort(0);
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }
}
