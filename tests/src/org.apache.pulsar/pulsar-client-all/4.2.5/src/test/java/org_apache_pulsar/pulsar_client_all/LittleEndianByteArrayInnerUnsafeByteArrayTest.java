/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.hash.HashCode;
import org.apache.pulsar.shade.com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

public class LittleEndianByteArrayInnerUnsafeByteArrayTest {
    @Test
    void hashesByteArraysWithTheFarmHashImplementation() {
        HashCode first = Hashing.farmHashFingerprint64().hashBytes("pulsar".getBytes(UTF_8));
        HashCode second = Hashing.farmHashFingerprint64().hashBytes("bookkeeper".getBytes(UTF_8));

        assertThat(first.bits()).isEqualTo(64);
        assertThat(first).isNotEqualTo(second);
    }
}
