/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.robolectric;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.protobuf.ByteString;
import org.junit.jupiter.api.Test;

public class AndroidTest {
    @Test
    void byteStringInitializationHandlesRobolectricMarkerClass() {
        String value = "android detection";

        ByteString bytes = ByteString.copyFromUtf8(value);

        assertThat(bytes.toStringUtf8()).isEqualTo(value);
        assertThat(bytes.size()).isEqualTo(value.length());
    }
}

final class Robolectric {
    private Robolectric() {
    }
}
