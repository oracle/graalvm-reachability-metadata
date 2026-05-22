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
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Base64;

import org.bouncycastle.jce.interfaces.ElGamalPublicKey;
import org.bouncycastle.jce.provider.JCEElGamalPublicKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCEElGamalPublicKeyTest {
    private static final BigInteger P = BigInteger.valueOf(23L);
    private static final BigInteger G = BigInteger.valueOf(5L);
    private static final BigInteger Y = BigInteger.valueOf(8L);

    @Test
    void serializedJcePublicKeyRoundTripPreservesElGamalParameters() throws Exception {
        ElGamalPublicKey publicKey = (ElGamalPublicKey) deserialize(serializedJcePublicKey());

        ElGamalPublicKey restoredKey = (ElGamalPublicKey) deserialize(serialize(publicKey));

        assertThat(publicKey).isInstanceOf(JCEElGamalPublicKey.class);
        assertThat(restoredKey).isInstanceOf(JCEElGamalPublicKey.class);
        assertJcePublicKey(restoredKey);
        assertThat(restoredKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    @Test
    void serializedHolderRoundTripPreservesNestedJcePublicKey() throws Exception {
        ElGamalPublicKey publicKey = (ElGamalPublicKey) deserialize(serializedJcePublicKey());
        KeyHolder holder = new KeyHolder(publicKey);

        KeyHolder restoredHolder = (KeyHolder) deserialize(serialize(holder));

        assertThat(restoredHolder.publicKey).isInstanceOf(JCEElGamalPublicKey.class);
        assertJcePublicKey(restoredHolder.publicKey);
    }

    private static void assertJcePublicKey(ElGamalPublicKey publicKey) {
        assertThat(publicKey.getAlgorithm()).isEqualTo("ElGamal");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(publicKey.getY()).isEqualTo(Y);
        assertThat(publicKey.getParameters().getP()).isEqualTo(P);
        assertThat(publicKey.getParameters().getG()).isEqualTo(G);
    }

    private static byte[] serializedJcePublicKey() {
        return Base64.getMimeDecoder().decode("""
                rO0ABXNyADFvcmcuYm91bmN5Y2FzdGxlLmpjZS5wcm92aWRlci5KQ0VFbEdhbWFsUHVibGljS2V5
                eOnUVVUsZjQDAAJMAAZlbFNwZWN0ADBMb3JnL2JvdW5jeWNhc3RsZS9qY2Uvc3BlYy9FbEdhbWFs
                UGFyYW1ldGVyU3BlYztMAAF5dAAWTGphdmEvbWF0aC9CaWdJbnRlZ2VyO3hwc3IAFGphdmEubWF0
                aC5CaWdJbnRlZ2VyjPyfH6k7+x0DAAZJAAhiaXRDb3VudEkACWJpdExlbmd0aEkAE2ZpcnN0Tm9u
                emVyb0J5dGVOdW1JAAxsb3dlc3RTZXRCaXRJAAZzaWdudW1bAAltYWduaXR1ZGV0AAJbQnhyABBq
                YXZhLmxhbmcuTnVtYmVyhqyVHQuU4IsCAAB4cP///////////////v////4AAAABdXIAAltCrPMX
                +AYIVOACAAB4cAAAAAEIeHNxAH4ABP///////////////v////4AAAABdXEAfgAIAAAAARd4c3EA
                fgAE///////////////+/////gAAAAF1cQB+AAgAAAABBXh4
                """);
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

    private static final class KeyHolder implements Serializable {
        private static final long serialVersionUID = 1L;

        private final ElGamalPublicKey publicKey;

        private KeyHolder(ElGamalPublicKey publicKey) {
            this.publicKey = publicKey;
        }
    }
}
