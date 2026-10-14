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
    void fingerprintsBytesWithTheLittleEndianImplementation() {
        byte[] input =
                "AutoValue generates immutable value types from concise declarations"
                        .getBytes(StandardCharsets.UTF_8);

        HashCode fingerprint = Hashing.farmHashFingerprint64().hashBytes(input);

        assertThat(fingerprint.bits()).isEqualTo(64);
        assertThat(fingerprint)
                .isEqualTo(Hashing.farmHashFingerprint64().hashBytes(input))
                .isNotEqualTo(
                        Hashing.farmHashFingerprint64()
                                .hashBytes("a different declaration".getBytes(StandardCharsets.UTF_8)));
    }
}
