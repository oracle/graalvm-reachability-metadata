/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.Pipe;
import java.nio.channels.WritableByteChannel;
import org.apache.pulsar.shade.org.apache.commons.io.channels.CloseShieldChannel;
import org.junit.jupiter.api.Test;

public class CloseShieldChannelTest {

    @Test
    void preservesAnExistingCloseShield() throws Exception {
        try (Pipe.SinkChannel delegate = Pipe.open().sink()) {
            final WritableByteChannel shield = CloseShieldChannel.wrap(delegate);

            assertThat(shield).isNotSameAs(delegate);
            assertThat(CloseShieldChannel.wrap(shield)).isSameAs(shield);
        }
    }
}
