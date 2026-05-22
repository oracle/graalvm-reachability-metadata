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

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.jce.provider.JCERSAPrivateKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCERSAPrivateKeyTest {
    private static final BigInteger MODULUS = new BigInteger("123456789123456789123456789");
    private static final BigInteger PRIVATE_EXPONENT = new BigInteger("987654321987654321");
    private static final ASN1ObjectIdentifier FRIENDLY_NAME_OID = new ASN1ObjectIdentifier("1.2.840.113549.1.9.20");
    private static final DERUTF8String FRIENDLY_NAME = new DERUTF8String("rsa serialization key");

    @Test
    void subclassSerializationRoundTripPreservesRsaPrivateKeyState() throws Exception {
        TestRsaPrivateKey privateKey = new TestRsaPrivateKey(MODULUS, PRIVATE_EXPONENT);
        privateKey.setBagAttribute(FRIENDLY_NAME_OID, FRIENDLY_NAME);

        TestRsaPrivateKey restoredKey = (TestRsaPrivateKey)deserialize(serialize(privateKey));

        assertThat(restoredKey).isInstanceOf(JCERSAPrivateKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getModulus()).isEqualTo(MODULUS);
        assertThat(restoredKey.getPrivateExponent()).isEqualTo(PRIVATE_EXPONENT);
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(((DERUTF8String)restoredKey.getBagAttribute(FRIENDLY_NAME_OID)).getString())
                .isEqualTo(FRIENDLY_NAME.getString());
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

    private static final class TestRsaPrivateKey extends JCERSAPrivateKey {
        private static final long serialVersionUID = 1L;

        private TestRsaPrivateKey(BigInteger modulus, BigInteger privateExponent) {
            this.modulus = modulus;
            this.privateExponent = privateExponent;
        }
    }
}
