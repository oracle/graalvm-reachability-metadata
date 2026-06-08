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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusParameters;
import org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusPublicKeyParameters;
import org.bouncycastle.pqc.jcajce.provider.sphincsplus.BCSPHINCSPlusPublicKey;
import org.junit.jupiter.api.Test;

public class BCSPHINCSPlusPublicKeyTest {
    private static final int SPHINCS_PLUS_PUBLIC_KEY_SIZE = 32;

    @Test
    void javaSerializationWritesSphincsPlusPublicKeyEncoding() throws Exception {
        BCSPHINCSPlusPublicKey publicKey = createPublicKey();

        byte[] serialized = serialize(publicKey);

        assertSerializedPublicKey(publicKey, serialized);
    }

    @Test
    void javaSerializationWritesDecodedSphincsPlusPublicKeyEncoding() throws Exception {
        BCSPHINCSPlusPublicKey publicKey = createPublicKey();
        SubjectPublicKeyInfo keyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        BCSPHINCSPlusPublicKey decodedPublicKey = new BCSPHINCSPlusPublicKey(keyInfo);

        byte[] serialized = serialize(decodedPublicKey);

        assertSerializedPublicKey(decodedPublicKey, serialized);
    }

    @Test
    void publicKeyApiExposesStableSphincsPlusEncoding() throws Exception {
        BCSPHINCSPlusPublicKey publicKey = createPublicKey();
        SubjectPublicKeyInfo keyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());

        assertEquals("SPHINCS+-SHA2-128F-ROBUST", publicKey.getAlgorithm());
        assertEquals("X.509", publicKey.getFormat());
        assertEquals("sha2-128f-robust", publicKey.getParameterSpec().getName());
        assertArrayEquals(publicKey.getEncoded(), keyInfo.getEncoded());
    }

    private static void assertSerializedPublicKey(
            BCSPHINCSPlusPublicKey publicKey,
            byte[] serialized) throws Exception {
        assertEquals("SPHINCS+-SHA2-128F-ROBUST", publicKey.getAlgorithm());
        assertEquals("X.509", publicKey.getFormat());
        assertEquals("sha2-128f-robust", publicKey.getParameterSpec().getName());
        assertTrue(serialized.length > publicKey.getEncoded().length);
        assertTrue(containsSubsequence(serialized, publicKey.getEncoded()));
    }

    private static BCSPHINCSPlusPublicKey createPublicKey() {
        return new BCSPHINCSPlusPublicKey(new SPHINCSPlusPublicKeyParameters(
                SPHINCSPlusParameters.sha2_128f_robust,
                sequence(SPHINCS_PLUS_PUBLIC_KEY_SIZE, 17)));
    }

    private static byte[] sequence(int length, int firstValue) {
        byte[] values = new byte[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (byte)(firstValue + i);
        }
        return values;
    }

    private static byte[] serialize(BCSPHINCSPlusPublicKey publicKey) throws Exception {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream)) {
            objectOutputStream.writeObject(publicKey);
        }
        return byteOutputStream.toByteArray();
    }

    private static boolean containsSubsequence(byte[] values, byte[] subsequence) {
        for (int offset = 0; offset <= values.length - subsequence.length; offset++) {
            if (matchesAt(values, subsequence, offset)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAt(byte[] values, byte[] subsequence, int offset) {
        for (int i = 0; i < subsequence.length; i++) {
            if (values[offset + i] != subsequence[i]) {
                return false;
            }
        }
        return true;
    }
}
