/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.hash.HashCode;
import autovalue.shaded.com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class LittleEndianByteArrayInnerUnsafeByteArrayTest {
    @Test
    void hashesBytesWithTheLittleEndianImplementation() {
        HashCode hash = Hashing.murmur3_128().hashBytes("auto-value".getBytes(StandardCharsets.UTF_8));

        assertThat(hash.bits()).isEqualTo(128);
        assertThat(hash.asBytes()).hasSize(16);
    }
}
