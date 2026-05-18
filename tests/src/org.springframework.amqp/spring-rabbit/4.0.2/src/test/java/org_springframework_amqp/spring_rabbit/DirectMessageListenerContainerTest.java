/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_amqp.spring_rabbit;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectMessageListenerContainerTest {

    @Test
    void createsLocalRabbitAdminWhenMissingQueuesAreFatalAndNoAdminIsConfigured() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");
        TestDirectMessageListenerContainer container = new TestDirectMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setMissingQueuesFatal(true);

        try {
            container.start();

            AmqpAdmin amqpAdmin = container.getConfiguredAmqpAdmin();
            assertThat(amqpAdmin).isInstanceOf(RabbitAdmin.class);
        } finally {
            container.stop();
            connectionFactory.destroy();
        }
    }

    private static final class TestDirectMessageListenerContainer extends DirectMessageListenerContainer {

        AmqpAdmin getConfiguredAmqpAdmin() {
            return getAmqpAdmin();
        }

    }

}
