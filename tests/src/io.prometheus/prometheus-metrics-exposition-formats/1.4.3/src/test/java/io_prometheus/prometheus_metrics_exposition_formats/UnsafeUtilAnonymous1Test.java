/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.prometheus_metrics_exposition_formats;

import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.CodedInputStream;
import io.prometheus.metrics.shaded.com_google_protobuf_4_33_0.CodedOutputStream;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeUtilAnonymous1Test {
    @Test
    void encodesScalarFieldsIntoDirectMemory() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocateDirect(128);
        CodedOutputStream output = CodedOutputStream.newInstance(buffer);
        output.writeString(1, "temperature");
        output.writeDouble(2, -12.5);
        output.flush();
        buffer.flip();

        CodedInputStream input = CodedInputStream.newInstance(buffer);
        assertThat(input.readTag()).isEqualTo(10);
        assertThat(input.readStringRequireUtf8()).isEqualTo("temperature");
        assertThat(input.readTag()).isEqualTo(17);
        assertThat(input.readDouble()).isEqualTo(-12.5);
        assertThat(input.readTag()).isZero();
        assertThat(input.isAtEnd()).isTrue();
    }
}
