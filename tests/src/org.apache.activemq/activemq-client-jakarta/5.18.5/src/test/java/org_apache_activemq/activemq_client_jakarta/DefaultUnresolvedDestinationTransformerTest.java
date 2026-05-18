/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.Topic;

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.DefaultUnresolvedDestinationTransformer;
import org.junit.jupiter.api.Test;

public class DefaultUnresolvedDestinationTransformerTest {
    @Test
    void transformUsesReflectedQueueIndicatorForDualDestination() throws JMSException {
        DefaultUnresolvedDestinationTransformer transformer = new DefaultUnresolvedDestinationTransformer();
        DualDestination destination = new DualDestination("orders", "events", true, false);

        ActiveMQDestination transformed = transformer.transform(destination);

        assertThat(transformed).isInstanceOf(ActiveMQQueue.class);
        assertThat(transformed.getPhysicalName()).isEqualTo("orders");
    }

    @Test
    void transformUsesReflectedTopicIndicatorForDualDestination() throws JMSException {
        DefaultUnresolvedDestinationTransformer transformer = new DefaultUnresolvedDestinationTransformer();
        DualDestination destination = new DualDestination("orders", "events", false, true);

        ActiveMQDestination transformed = transformer.transform(destination);

        assertThat(transformed).isInstanceOf(ActiveMQTopic.class);
        assertThat(transformed.getPhysicalName()).isEqualTo("events");
    }

    public static class DualDestination implements Queue, Topic {
        private final String queueName;
        private final String topicName;
        private final boolean queue;
        private final boolean topic;

        DualDestination(String queueName, String topicName, boolean queue, boolean topic) {
            this.queueName = queueName;
            this.topicName = topicName;
            this.queue = queue;
            this.topic = topic;
        }

        @Override
        public String getQueueName() {
            return queueName;
        }

        @Override
        public String getTopicName() {
            return topicName;
        }

        public boolean isQueue() {
            return queue;
        }

        public boolean isTopic() {
            return topic;
        }
    }
}
