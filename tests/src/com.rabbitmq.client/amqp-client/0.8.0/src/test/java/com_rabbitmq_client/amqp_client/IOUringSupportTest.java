/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import com.rabbitmq.qpid.protonj2.client.transport.netty4.IOUringSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IOUringSupportTest {
    @Test
    void availabilityCheckInitializesNettyIoUringSupport() {
        boolean available = IOUringSupport.isAvailable();

        if (available) {
            assertThat(IOUringSupport.getChannelClass()).isNotNull();
        } else {
            assertThatThrownBy(IOUringSupport::ensureAvailability)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("io_ring support is not enabled");
        }
    }
}
