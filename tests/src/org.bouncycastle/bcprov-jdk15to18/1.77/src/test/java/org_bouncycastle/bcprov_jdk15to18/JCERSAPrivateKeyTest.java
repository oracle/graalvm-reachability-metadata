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
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Hashtable;
import java.util.Vector;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.jce.provider.JCERSAPrivateKey;
import org.junit.jupiter.api.Test;

public class JCERSAPrivateKeyTest {
    private static final String JCE_RSA_PRIVATE_KEY_CLASS = "org.bouncycastle.jce.provider.JCERSAPrivateKey";
    private static final long JCE_RSA_PRIVATE_KEY_SERIAL_VERSION_UID = 5110188922551353628L;
    private static final BigInteger MODULUS = new BigInteger("135791357913579135791357913579");
    private static final BigInteger PRIVATE_EXPONENT = new BigInteger("24680246802468024680246802468");
    private static final ASN1ObjectIdentifier FRIENDLY_NAME_OID = new ASN1ObjectIdentifier(
        "1.2.840.113549.1.9.20");
    private static final DERUTF8String FRIENDLY_NAME = new DERUTF8String("serialization-test-key");

    @Test
    void javaDeserializationReachesExactRsaPrivateKeySerialForm() throws Exception {
        JCERSAPrivateKey privateKey = deserializeExactKey(initialSerializedExactKey());

        assertEquals(JCE_RSA_PRIVATE_KEY_CLASS, privateKey.getClass().getName());
        assertRsaPrivateKey(privateKey);

        JCERSAPrivateKey restored = deserializeExactKey(serialize(privateKey));

        assertEquals(JCE_RSA_PRIVATE_KEY_CLASS, restored.getClass().getName());
        assertRsaPrivateKey(restored);
    }

    @Test
    void javaSerializationRoundTripPreservesRsaPrivateKeyParameters() throws Exception {
        SerializableJceRsaPrivateKey privateKey = new SerializableJceRsaPrivateKey(
            MODULUS,
            PRIVATE_EXPONENT);

        byte[] serialized = serialize(privateKey);
        SerializableJceRsaPrivateKey restored = deserialize(serialized);

        assertRsaPrivateKey(restored);
    }

    @Test
    void javaSerializationRoundTripPreservesBagAttributes() throws Exception {
        SerializableJceRsaPrivateKey privateKey = new SerializableJceRsaPrivateKey(
            MODULUS,
            PRIVATE_EXPONENT);
        privateKey.setBagAttribute(FRIENDLY_NAME_OID, FRIENDLY_NAME);

        byte[] serialized = serialize(privateKey);
        SerializableJceRsaPrivateKey restored = deserialize(serialized);

        assertRsaPrivateKey(restored);
        assertEquals(FRIENDLY_NAME, restored.getBagAttribute(FRIENDLY_NAME_OID));
    }

    private static void assertRsaPrivateKey(JCERSAPrivateKey privateKey) {
        assertEquals("RSA", privateKey.getAlgorithm());
        assertEquals("PKCS#8", privateKey.getFormat());
        assertEquals(MODULUS, privateKey.getModulus());
        assertEquals(PRIVATE_EXPONENT, privateKey.getPrivateExponent());
    }

    private static byte[] initialSerializedExactKey() throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new JCERSAPrivateKeyObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(new SerializedJceRsaPrivateKey());
        }
        return byteOutputStream.toByteArray();
    }

    private static byte[] serialize(JCERSAPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static SerializableJceRsaPrivateKey deserialize(byte[] serialized) throws Exception {
        return (SerializableJceRsaPrivateKey)deserializeObject(serialized);
    }

    private static JCERSAPrivateKey deserializeExactKey(byte[] serialized) throws Exception {
        return (JCERSAPrivateKey)deserializeObject(serialized);
    }

    private static Object deserializeObject(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }

    private static final class SerializedJceRsaPrivateKey implements Serializable {
        private static final long serialVersionUID = JCE_RSA_PRIVATE_KEY_SERIAL_VERSION_UID;

        private void writeObject(ObjectOutputStream outputStream) throws IOException {
            outputStream.writeObject(MODULUS);
            outputStream.writeObject(new Hashtable<ASN1ObjectIdentifier, ASN1Encodable>());
            outputStream.writeObject(new Vector<ASN1ObjectIdentifier>());
            outputStream.writeObject(PRIVATE_EXPONENT);
        }
    }

    private static final class JCERSAPrivateKeyObjectOutputStream extends ObjectOutputStream {
        private static final byte SERIALIZABLE_CLASS_WITH_WRITE_OBJECT = 0x03;

        private JCERSAPrivateKeyObjectOutputStream(ByteArrayOutputStream outputStream) throws IOException {
            super(outputStream);
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            if (SerializedJceRsaPrivateKey.class.getName().equals(descriptor.getName())) {
                writeUTF(JCE_RSA_PRIVATE_KEY_CLASS);
                writeLong(JCE_RSA_PRIVATE_KEY_SERIAL_VERSION_UID);
                writeByte(SERIALIZABLE_CLASS_WITH_WRITE_OBJECT);
                writeShort(0);
                return;
            }
            super.writeClassDescriptor(descriptor);
        }
    }

    private static final class SerializableJceRsaPrivateKey extends JCERSAPrivateKey {
        private static final long serialVersionUID = 1L;

        private SerializableJceRsaPrivateKey(BigInteger modulus, BigInteger privateExponent) {
            this.modulus = modulus;
            this.privateExponent = privateExponent;
        }
    }
}
