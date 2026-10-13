/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.hash.HashCode;
import com.tngtech.archunit.thirdparty.com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

public class LittleEndianByteArrayInnerUnsafeByteArrayTest {
    @Test
    void farmHashReadsLongBlocksWithLittleEndianSemantics() {
        byte[] input = new byte[64];
        for (int index = 0; index < input.length; index++) {
            input[index] = (byte) (index * 3 + 1);
        }

        HashCode first = Hashing.farmHashFingerprint64().hashBytes(input);
        input[32]++;
        HashCode second = Hashing.farmHashFingerprint64().hashBytes(input);

        assertThat(first.asBytes()).hasSize(8);
        assertThat(Hashing.farmHashFingerprint64().hashBytes(input)).isEqualTo(second);
        assertThat(second).isNotEqualTo(first);
    }
}
