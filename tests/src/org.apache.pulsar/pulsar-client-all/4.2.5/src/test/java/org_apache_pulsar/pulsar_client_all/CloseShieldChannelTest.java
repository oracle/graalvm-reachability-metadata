/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.Pipe;

import org.apache.pulsar.shade.org.apache.commons.io.channels.CloseShieldChannel;
import org.junit.jupiter.api.Test;

public class CloseShieldChannelTest {
    @Test
    void shieldsTheUnderlyingChannelFromClose() throws Exception {
        final Pipe pipe = Pipe.open();
        try (Pipe.SourceChannel source = pipe.source(); Pipe.SinkChannel sink = pipe.sink()) {
            final Pipe.SinkChannel shield = CloseShieldChannel.wrap(sink);
            assertThat(shield.isOpen()).isTrue();

            shield.close();

            assertThat(shield.isOpen()).isFalse();
            assertThat(sink.isOpen()).isTrue();
        }
    }
}
