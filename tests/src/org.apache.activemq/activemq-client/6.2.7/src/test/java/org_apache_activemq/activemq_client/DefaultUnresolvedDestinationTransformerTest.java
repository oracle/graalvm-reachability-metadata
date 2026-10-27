/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.DefaultUnresolvedDestinationTransformer;
import org.junit.jupiter.api.Test;

import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.Topic;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultUnresolvedDestinationTransformerTest {

    @Test
    void transformsAProviderNeutralQueue() throws Exception {
        ActiveMQDestination destination = new DefaultUnresolvedDestinationTransformer()
                .transform(new ForeignDestination("orders", true));

        assertThat(destination.isQueue()).isTrue();
        assertThat(destination.getPhysicalName()).isEqualTo("orders");
    }

    public static final class ForeignDestination implements Queue, Topic {
        private final String name;
        private final boolean queue;

        public ForeignDestination(String name, boolean queue) {
            this.name = name;
            this.queue = queue;
        }

        @Override
        public String getQueueName() throws JMSException {
            return name;
        }

        @Override
        public String getTopicName() throws JMSException {
            return name;
        }

        public boolean isQueue() {
            return queue;
        }

        public boolean isTopic() {
            return !queue;
        }
    }
}
