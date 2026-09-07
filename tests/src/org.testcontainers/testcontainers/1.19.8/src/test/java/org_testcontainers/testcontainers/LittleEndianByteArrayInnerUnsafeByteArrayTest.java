/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.hash.HashCode;
import org.testcontainers.shaded.com.google.common.hash.Hashing;

import static org.assertj.core.api.Assertions.assertThat;

public class LittleEndianByteArrayInnerUnsafeByteArrayTest {
    @Test
    void fingerprintsBytesUsingLittleEndianWordAccess() {
        byte[] input = "testcontainers-fingerprint".getBytes(StandardCharsets.UTF_8);

        HashCode fingerprint = Hashing.farmHashFingerprint64().hashBytes(input);

        assertThat(fingerprint.bits()).isEqualTo(Long.SIZE);
        assertThat(fingerprint.asLong()).isNotZero();
    }
}
