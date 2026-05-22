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
import java.nio.charset.StandardCharsets;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.JCEECPrivateKey;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCEECPrivateKeyTest {
    private static final String CURVE_NAME = "prime256v1";
    private static final BigInteger PRIVATE_VALUE = BigInteger.valueOf(42L);
    private static final ASN1ObjectIdentifier FRIENDLY_NAME_OID = new ASN1ObjectIdentifier("1.2.840.113549.1.9.20");
    private static final DERUTF8String FRIENDLY_NAME = new DERUTF8String("serialization key");

    @Test
    void serializationWritesPrivateKeyState() throws Exception {
        JCEECPrivateKey privateKey = createPrivateKey();
        byte[] encodedKey = privateKey.getEncoded();

        byte[] serializedKey = serialize(privateKey);

        assertThat(serializedKey).hasSizeGreaterThan(encodedKey.length);
        assertThat(containsSubsequence(serializedKey, encodedKey)).isTrue();
        assertThat(containsSubsequence(serializedKey, "EC".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    void serializationRoundTripPreservesPrivateKeyState() throws Exception {
        JCEECPrivateKey privateKey = createPrivateKey();
        privateKey.setPointFormat("COMPRESSED");
        privateKey.setBagAttribute(FRIENDLY_NAME_OID, FRIENDLY_NAME);

        byte[] serializedKey = serialize(privateKey);
        JCEECPrivateKey restoredKey = (JCEECPrivateKey)deserialize(serializedKey);

        assertThat(restoredKey).isInstanceOf(JCEECPrivateKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("EC");
        assertThat(restoredKey.getFormat()).isEqualTo("PKCS#8");
        assertThat(restoredKey.getS()).isEqualTo(PRIVATE_VALUE);
        assertThat(restoredKey.getD()).isEqualTo(PRIVATE_VALUE);
        assertThat(restoredKey.getParams()).isNotNull();
        assertThat(restoredKey.getParams().getOrder()).isEqualTo(privateKey.getParams().getOrder());
        assertThat(restoredKey.getParams().getGenerator()).isEqualTo(privateKey.getParams().getGenerator());
        assertThat(restoredKey.getEncoded()).isEqualTo(privateKey.getEncoded());
        assertThat(((DERUTF8String)restoredKey.getBagAttribute(FRIENDLY_NAME_OID)).getString())
                .isEqualTo(FRIENDLY_NAME.getString());
    }

    private static JCEECPrivateKey createPrivateKey() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(PRIVATE_VALUE, curveSpec);
        return new JCEECPrivateKey("EC", keySpec);
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

    private static boolean containsSubsequence(byte[] source, byte[] candidate) {
        for (int offset = 0; offset <= source.length - candidate.length; offset++) {
            boolean matches = true;
            for (int index = 0; index < candidate.length; index++) {
                if (source[offset + index] != candidate[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }
}
