/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.hash.HashCode;
import org.apache.curator.shaded.com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LittleEndianByteArrayInnerUnsafeByteArrayAnonymous3Test {
    @Test
    void farmHashReadsInputWithLittleEndianOperations() {
        byte[] input = new byte[32];
        for (int index = 0; index < input.length; index++) {
            input[index] = (byte) (index * 3 + 1);
        }

        HashCode first = Hashing.farmHashFingerprint64().hashBytes(input);
        HashCode second = Hashing.farmHashFingerprint64().hashBytes(input);

        assertThat(first).isEqualTo(second);
        assertThat(first.asBytes()).hasSize(8);
    }
}
