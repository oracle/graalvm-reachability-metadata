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
    void murmurHashReadsLongBlocksWithLittleEndianSemantics() {
        byte[] input = new byte[32];
        for (int index = 0; index < input.length; index++) {
            input[index] = (byte) (index * 3 + 1);
        }

        HashCode first = Hashing.murmur3_128().hashBytes(input);
        input[16]++;
        HashCode second = Hashing.murmur3_128().hashBytes(input);

        assertThat(first.asBytes()).hasSize(16);
        assertThat(second.asBytes()).hasSize(16);
        assertThat(second).isNotEqualTo(first);
    }
}
