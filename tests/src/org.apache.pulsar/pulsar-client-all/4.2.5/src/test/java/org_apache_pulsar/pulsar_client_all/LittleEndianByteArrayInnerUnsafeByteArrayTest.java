/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.pulsar.shade.com.google.common.hash.HashCode;
import org.apache.pulsar.shade.com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

public class LittleEndianByteArrayInnerUnsafeByteArrayTest {

    @Test
    void hashesAnUnalignedLittleEndianPayloadDeterministically() {
        final byte[] payload = "pulsar-message".getBytes(StandardCharsets.UTF_8);

        final HashCode first = Hashing.farmHashFingerprint64().hashBytes(payload);
        final HashCode second = Hashing.farmHashFingerprint64().hashBytes(payload);

        assertThat(first).isEqualTo(second);
        assertThat(first.bits()).isEqualTo(64);
        assertThat(first.asLong()).isNotZero();
    }
}
