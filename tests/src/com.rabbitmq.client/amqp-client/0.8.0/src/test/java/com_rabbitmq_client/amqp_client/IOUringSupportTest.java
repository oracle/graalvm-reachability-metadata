/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.qpid.protonj2.client.TransportOptions;
import com.rabbitmq.qpid.protonj2.client.transport.netty4.IOUringSupport;
import org.junit.jupiter.api.Test;

public class IOUringSupportTest {

    @Test
    void detectsAvailabilityUsingNettyIoUringSupport() {
        boolean available = IOUringSupport.isAvailable();

        assertThat(IOUringSupport.isAvailable(new TransportOptions().allowNativeIO(true)))
                .isEqualTo(available);
        assertThat(IOUringSupport.isAvailable(new TransportOptions().allowNativeIO(false))).isFalse();
    }
}
