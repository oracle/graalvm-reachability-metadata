/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.Enumeration;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.jcajce.provider.asymmetric.util.PKCS12BagAttributeCarrierImpl;
import org.bouncycastle.jce.provider.JCEECPrivateKey;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.junit.jupiter.api.Test;

public class PKCS12BagAttributeCarrierImplTest {
    private static final ASN1ObjectIdentifier FRIENDLY_NAME_OID =
        new ASN1ObjectIdentifier("1.2.840.113549.1.9.20");

    @Test
    void objectStreamWritesEmptyBagAttributeState() throws Exception {
        PKCS12BagAttributeCarrierImpl original = new PKCS12BagAttributeCarrierImpl();

        byte[] serialized = writeCarrier(original);

        assertTrue(serialized.length > 0);
    }

    @Test
    void objectStreamRoundTripPreservesEncodedBagAttributes() throws Exception {
        DERUTF8String friendlyName = new DERUTF8String("client-key");
        PKCS12BagAttributeCarrierImpl original = new PKCS12BagAttributeCarrierImpl();
        original.setBagAttribute(FRIENDLY_NAME_OID, friendlyName);

        PKCS12BagAttributeCarrierImpl restored = readCarrier(writeCarrier(original));

        assertEquals(friendlyName, restored.getBagAttribute(FRIENDLY_NAME_OID));
        assertSingleKey(FRIENDLY_NAME_OID, restored.getBagAttributeKeys());
    }

    @Test
    void legacyEcPrivateKeySerializationPreservesBagAttributes() throws Exception {
        DERUTF8String friendlyName = new DERUTF8String("legacy-ec-key");
        JCEECPrivateKey original = new JCEECPrivateKey(
            "EC",
            new ECPrivateKeySpec(BigInteger.valueOf(7L), null));
        original.setBagAttribute(FRIENDLY_NAME_OID, friendlyName);

        JCEECPrivateKey restored = readEcPrivateKey(writeEcPrivateKey(original));

        assertEquals(original.getS(), restored.getS());
        assertEquals(original.getAlgorithm(), restored.getAlgorithm());
        assertEquals(friendlyName, restored.getBagAttribute(FRIENDLY_NAME_OID));
        assertSingleKey(FRIENDLY_NAME_OID, restored.getBagAttributeKeys());
    }

    private static byte[] writeCarrier(PKCS12BagAttributeCarrierImpl carrier) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            carrier.writeObject(objectOutputStream);
        }
        return byteOutputStream.toByteArray();
    }

    private static PKCS12BagAttributeCarrierImpl readCarrier(byte[] serialized) throws Exception {
        PKCS12BagAttributeCarrierImpl carrier = new PKCS12BagAttributeCarrierImpl();
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            carrier.readObject(objectInputStream);
        }
        return carrier;
    }

    private static byte[] writeEcPrivateKey(JCEECPrivateKey privateKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(privateKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static JCEECPrivateKey readEcPrivateKey(byte[] serialized) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            return (JCEECPrivateKey)objectInputStream.readObject();
        }
    }

    private static void assertSingleKey(
            ASN1ObjectIdentifier expected,
            Enumeration<?> keys) {
        assertTrue(keys.hasMoreElements());
        assertEquals(expected, keys.nextElement());
        assertFalse(keys.hasMoreElements());
    }
}
