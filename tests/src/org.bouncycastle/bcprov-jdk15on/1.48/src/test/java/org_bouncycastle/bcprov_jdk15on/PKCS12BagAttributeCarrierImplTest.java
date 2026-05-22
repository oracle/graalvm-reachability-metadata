/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.jcajce.provider.asymmetric.util.PKCS12BagAttributeCarrierImpl;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;
import org.bouncycastle.jce.provider.JCEECPrivateKey;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PKCS12BagAttributeCarrierImplTest {
    private static final ASN1ObjectIdentifier FRIENDLY_NAME_OID = new ASN1ObjectIdentifier("1.2.840.113549.1.9.20");
    private static final ASN1ObjectIdentifier LOCAL_KEY_ID_OID = new ASN1ObjectIdentifier("1.2.840.113549.1.9.21");
    private static final DERUTF8String FRIENDLY_NAME = new DERUTF8String("signing key");
    private static final DERUTF8String LOCAL_KEY_ID = new DERUTF8String("local key id");
    private static final String CURVE_NAME = "prime256v1";
    private static final BigInteger PRIVATE_VALUE = BigInteger.valueOf(42L);

    @Test
    void emptyCarrierSerializationRoundTripPreservesNoBagAttributes() throws Exception {
        PKCS12BagAttributeCarrierImpl restoredCarrier = readCarrier(writeCarrier(new PKCS12BagAttributeCarrierImpl()));

        assertThat(attributeKeys(restoredCarrier)).isEmpty();
        assertThat(restoredCarrier.getBagAttribute(FRIENDLY_NAME_OID)).isNull();
    }

    @Test
    void carrierSerializationRoundTripPreservesBagAttributesInInsertionOrder() throws Exception {
        PKCS12BagAttributeCarrierImpl carrier = new PKCS12BagAttributeCarrierImpl();
        carrier.setBagAttribute(FRIENDLY_NAME_OID, FRIENDLY_NAME);
        carrier.setBagAttribute(LOCAL_KEY_ID_OID, LOCAL_KEY_ID);

        PKCS12BagAttributeCarrierImpl restoredCarrier = readCarrier(writeCarrier(carrier));

        assertThat(attributeKeys(restoredCarrier)).containsExactly(FRIENDLY_NAME_OID, LOCAL_KEY_ID_OID);
        assertThat(((DERUTF8String)restoredCarrier.getBagAttribute(FRIENDLY_NAME_OID)).getString())
                .isEqualTo(FRIENDLY_NAME.getString());
        assertThat(((DERUTF8String)restoredCarrier.getBagAttribute(LOCAL_KEY_ID_OID)).getString())
                .isEqualTo(LOCAL_KEY_ID.getString());
    }

    @Test
    void ecPrivateKeySerializationRoundTripPreservesCarrierBagAttributes() throws Exception {
        JCEECPrivateKey privateKey = createEcPrivateKey();
        privateKey.setBagAttribute(FRIENDLY_NAME_OID, FRIENDLY_NAME);
        privateKey.setBagAttribute(LOCAL_KEY_ID_OID, LOCAL_KEY_ID);

        JCEECPrivateKey restoredKey = (JCEECPrivateKey)deserialize(serialize(privateKey));

        assertThat(restoredKey.getS()).isEqualTo(PRIVATE_VALUE);
        assertThat(restoredKey.getParams()).isNotNull();
        assertThat(attributeKeys(restoredKey)).containsExactly(FRIENDLY_NAME_OID, LOCAL_KEY_ID_OID);
        assertThat(((DERUTF8String)restoredKey.getBagAttribute(FRIENDLY_NAME_OID)).getString())
                .isEqualTo(FRIENDLY_NAME.getString());
        assertThat(((DERUTF8String)restoredKey.getBagAttribute(LOCAL_KEY_ID_OID)).getString())
                .isEqualTo(LOCAL_KEY_ID.getString());
    }

    private static JCEECPrivateKey createEcPrivateKey() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(PRIVATE_VALUE, curveSpec);
        return new JCEECPrivateKey("EC", keySpec);
    }

    private static byte[] writeCarrier(PKCS12BagAttributeCarrierImpl carrier) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            carrier.writeObject(objectStream);
        }
        return byteStream.toByteArray();
    }

    private static PKCS12BagAttributeCarrierImpl readCarrier(byte[] encodedCarrier) throws Exception {
        PKCS12BagAttributeCarrierImpl carrier = new PKCS12BagAttributeCarrierImpl();
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(encodedCarrier))) {
            carrier.readObject(objectStream);
        }
        return carrier;
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
        }
        return byteStream.toByteArray();
    }

    private static Object deserialize(byte[] serializedValue) throws Exception {
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(serializedValue))) {
            return objectStream.readObject();
        }
    }

    private static List<DERObjectIdentifier> attributeKeys(PKCS12BagAttributeCarrier carrier) {
        List<DERObjectIdentifier> keys = new ArrayList<DERObjectIdentifier>();
        Enumeration keyEnumeration = carrier.getBagAttributeKeys();
        while (keyEnumeration.hasMoreElements()) {
            keys.add((DERObjectIdentifier)keyEnumeration.nextElement());
        }
        return keys;
    }
}
