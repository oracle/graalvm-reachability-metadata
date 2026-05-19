/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_amqp.spring_rabbit;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingConnectionFactoryInnerCachedChannelInvocationHandlerTest {

    @Test
    void delegatesChannelMethodToCachedTarget() throws Exception {
        CachingConnectionFactoryTest.TestRabbitConnectionFactory rabbitConnectionFactory =
                new CachingConnectionFactoryTest.TestRabbitConnectionFactory();
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitConnectionFactory);

        try {
            Connection connection = connectionFactory.createConnection();
            Channel cachedChannel = connection.createChannel(false);

            assertThat(cachedChannel.getChannelNumber()).isEqualTo(1);

            cachedChannel.close();
        } finally {
            connectionFactory.destroy();
        }
    }

}
