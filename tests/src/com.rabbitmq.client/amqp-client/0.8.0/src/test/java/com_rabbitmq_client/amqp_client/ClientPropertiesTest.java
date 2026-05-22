/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.client.amqp.Environment;
import com.rabbitmq.client.amqp.impl.AmqpEnvironmentBuilder;
import org.junit.jupiter.api.Test;

public class ClientPropertiesTest {

    @Test
    void connectionBuilderInitializesDefaultClientProperties() {
        RuntimeException stopBeforeNetworkConnection =
                new RuntimeException("stop before network connection");
        try (Environment environment = new AmqpEnvironmentBuilder().build()) {
            assertThatThrownBy(
                            () ->
                                    environment
                                            .connectionBuilder()
                                            .addressSelector(
                                                    addresses -> {
                                                        throw stopBeforeNetworkConnection;
                                                    })
                                            .build())
                    .isSameAs(stopBeforeNetworkConnection);
        }
    }
}
