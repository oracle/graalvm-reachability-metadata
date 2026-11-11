/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.ByteString;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AndroidTest {
    @Test
    void validatesUtf8UsingPlatformSelectedProcessor() {
        String label = "caf\u00e9-\u6e29\u5ea6-\ud83d\ude80";
        ByteString bytes = ByteString.copyFromUtf8(label);

        assertThat(bytes.isValidUtf8()).isTrue();
        assertThat(bytes.toStringUtf8()).isEqualTo(label);
        assertThat(bytes.size()).isGreaterThan(label.length());
        assertThat(ByteString.copyFrom(new byte[]{(byte) 0xc3, 0x28}).isValidUtf8()).isFalse();
    }
}
