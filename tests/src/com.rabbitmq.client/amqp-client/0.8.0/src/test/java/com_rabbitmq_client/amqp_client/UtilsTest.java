/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.amqp.ConnectionBuilder;
import com.rabbitmq.client.amqp.Environment;
import com.rabbitmq.client.amqp.impl.AmqpEnvironmentBuilder;
import org.junit.jupiter.api.Test;

public class UtilsTest {

    @Test
    void buildEnvironmentWithDefaultExecutors() {
        try (Environment environment = new AmqpEnvironmentBuilder().build()) {
            ConnectionBuilder connectionBuilder = environment.connectionBuilder();

            assertThat(environment).isNotNull();
            assertThat(connectionBuilder).isNotNull();
        }
    }
}
