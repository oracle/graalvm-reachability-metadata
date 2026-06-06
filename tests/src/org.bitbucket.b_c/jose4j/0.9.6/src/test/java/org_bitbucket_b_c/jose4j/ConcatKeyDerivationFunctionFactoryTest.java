/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bitbucket_b_c.jose4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.jose4j.jwe.kdf.ConcatenationKeyDerivationFunctionWithSha256;
import org.jose4j.jwe.kdf.KdfUtil;
import org.junit.jupiter.api.Test;

public class ConcatKeyDerivationFunctionFactoryTest {
    private static final String CUSTOM_KDF_PROPERTY =
            "org.jose4j.jwe.kdf.ConcatenationKeyDerivationFunctionWithSha256";

    @Test
    void usesConfiguredConcatKeyDerivationFunctionImplementation() {
        System.setProperty(CUSTOM_KDF_PROPERTY, CustomConcatKeyDerivationFunction.class.getName());

        byte[] derivedKey = new KdfUtil().kdf(new byte[] {1, 2, 3, 4}, 128, "A128GCM", "", "");

        assertThat(derivedKey).hasSize(16).containsOnly((byte) 0x5a);
        assertThat(CustomConcatKeyDerivationFunction.instancesCreated).isEqualTo(2);
        assertThat(CustomConcatKeyDerivationFunction.invocations).isEqualTo(2);
        assertThat(CustomConcatKeyDerivationFunction.lastKeyDataLength).isEqualTo(128);
    }

    public static class CustomConcatKeyDerivationFunction implements ConcatenationKeyDerivationFunctionWithSha256 {
        static int instancesCreated;
        static int invocations;
        static int lastKeyDataLength;

        public CustomConcatKeyDerivationFunction() {
            instancesCreated++;
        }

        @Override
        public byte[] kdf(byte[] sharedSecret, int keydatalen, byte[] otherInfo) {
            invocations++;
            lastKeyDataLength = keydatalen;
            byte[] keyMaterial = new byte[keydatalen / Byte.SIZE];
            Arrays.fill(keyMaterial, (byte) 0x5a);
            return keyMaterial;
        }
    }
}
