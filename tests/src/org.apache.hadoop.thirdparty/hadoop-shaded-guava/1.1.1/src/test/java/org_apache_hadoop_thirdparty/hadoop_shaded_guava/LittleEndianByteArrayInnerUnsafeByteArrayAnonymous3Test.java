/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.com.google.common.hash.HashCode;
import org.apache.hadoop.thirdparty.com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

public class LittleEndianByteArrayInnerUnsafeByteArrayAnonymous3Test {
    @Test
    void farmHashFingerprintReadsByteArraysThroughLittleEndianAccess() {
        byte[] bytes = bytesForLittleEndianRead();

        HashCode firstHash = Hashing.farmHashFingerprint64().hashBytes(bytes);
        HashCode repeatedHash = Hashing.farmHashFingerprint64().hashBytes(bytesForLittleEndianRead());

        assertThat(firstHash.asBytes()).hasSize(8);
        assertThat(firstHash).isEqualTo(repeatedHash);
    }

    private static byte[] bytesForLittleEndianRead() {
        byte[] bytes = new byte[32];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index * 3 + 1);
        }
        return bytes;
    }
}
