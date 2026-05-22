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
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.JCEECPublicKey;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCEECPublicKeyTest {
    private static final String CURVE_NAME = "prime256v1";

    @Test
    void jcaPublicKeySerializationRoundTripPreservesJcePublicKeyImplementation() throws Exception {
        PublicKey publicKey = createPublicKey();
        byte[] serializedKey;
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(publicKey);
        }
        serializedKey = byteStream.toByteArray();

        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(serializedKey))) {
            PublicKey restoredKey = (PublicKey)objectStream.readObject();

            assertThat(restoredKey).isInstanceOf(JCEECPublicKey.class);
            assertThat(restoredKey.getAlgorithm()).isEqualTo(publicKey.getAlgorithm());
            assertThat(restoredKey.getFormat()).isEqualTo(publicKey.getFormat());
            assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
        }
    }

    @Test
    void serializationWritesPublicKeyState() throws Exception {
        JCEECPublicKey publicKey = createPublicKey();
        byte[] encodedKey = publicKey.getEncoded();

        byte[] serializedKey = serialize(publicKey);

        assertThat(serializedKey).isNotEmpty();
        assertThat(containsSubsequence(serializedKey, encodedKey)).isTrue();
        assertThat(containsSubsequence(serializedKey, "EC".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    void serializationRoundTripPreservesPublicKeyState() throws Exception {
        JCEECPublicKey publicKey = createPublicKey();
        publicKey.setPointFormat("COMPRESSED");

        byte[] serializedKey = serialize(publicKey);
        JCEECPublicKey restoredKey = (JCEECPublicKey)deserialize(serializedKey);

        assertPublicKeysMatch(restoredKey, publicKey);
    }

    @Test
    void unsharedSerializationRoundTripPreservesPublicKeyState() throws Exception {
        JCEECPublicKey publicKey = createPublicKey();
        publicKey.setPointFormat("UNCOMPRESSED");

        byte[] serializedKey = serializeUnshared(publicKey);
        JCEECPublicKey restoredKey = (JCEECPublicKey)deserializeUnshared(serializedKey);

        assertPublicKeysMatch(restoredKey, publicKey);
    }

    private static JCEECPublicKey createPublicKey() {
        ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(curveSpec.getG(), curveSpec);
        return new JCEECPublicKey("EC", keySpec);
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

    private static byte[] serializeUnshared(Object value) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeUnshared(value);
        }
        return byteStream.toByteArray();
    }

    private static Object deserializeUnshared(byte[] serializedValue) throws Exception {
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(serializedValue))) {
            return objectStream.readUnshared();
        }
    }

    private static void assertPublicKeysMatch(JCEECPublicKey restoredKey, JCEECPublicKey publicKey) {
        assertThat(restoredKey).isInstanceOf(JCEECPublicKey.class);
        assertThat(restoredKey.getAlgorithm()).isEqualTo("EC");
        assertThat(restoredKey.getFormat()).isEqualTo("X.509");
        assertThat(restoredKey.getW()).isEqualTo(publicKey.getW());
        assertThat(restoredKey.getQ()).isEqualTo(publicKey.getQ());
        assertThat(restoredKey.getParams()).isNotNull();
        assertThat(restoredKey.getParams().getOrder()).isEqualTo(publicKey.getParams().getOrder());
        assertThat(restoredKey.getParams().getGenerator()).isEqualTo(publicKey.getParams().getGenerator());
        assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
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
