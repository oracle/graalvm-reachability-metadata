/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import org.apache.pulsar.shade.org.apache.commons.io.channels.CloseShieldChannel;
import org.junit.jupiter.api.Test;

public class CloseShieldChannelHandlerTest {

    @Test
    void closesTheShieldWithoutClosingTheDelegate() throws Exception {
        try (Pipe.SinkChannel delegate = Pipe.open().sink()) {
            final WritableByteChannel shield = CloseShieldChannel.wrap(delegate);
            shield.close();

            assertThat(shield.isOpen()).isFalse();
            assertThat(delegate.isOpen()).isTrue();
            assertThat(delegate.write(ByteBuffer.wrap("ok".getBytes(StandardCharsets.UTF_8)))).isEqualTo(2);
        }
    }
}
