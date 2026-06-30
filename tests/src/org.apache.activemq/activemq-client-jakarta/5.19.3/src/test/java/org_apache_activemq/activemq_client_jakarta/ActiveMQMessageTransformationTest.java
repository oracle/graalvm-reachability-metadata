/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;

import org.apache.activemq.ActiveMQMessageTransformation;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQMessageTransformationTest {

    @Test
    void copyPropertiesPreservesJmsDeliveryTimeFromJakartaMessage() throws JMSException {
        ActiveMQMessage fromMessage = new ActiveMQMessage();
        fromMessage.setJMSMessageID("ID:source-message");
        fromMessage.setJMSCorrelationID("correlation-id");
        fromMessage.setJMSReplyTo(new ActiveMQQueue("reply.queue"));
        fromMessage.setJMSDestination(new ActiveMQTopic("orders.topic"));
        fromMessage.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
        fromMessage.setJMSDeliveryTime(123_456_789L);
        fromMessage.setJMSRedelivered(true);
        fromMessage.setJMSType("order-created");
        fromMessage.setJMSExpiration(123_456_999L);
        fromMessage.setJMSPriority(7);
        fromMessage.setJMSTimestamp(987_654_321L);
        fromMessage.setObjectProperty("tenant", "integration-test");

        ActiveMQMessage toMessage = new ActiveMQMessage();

        ActiveMQMessageTransformation.copyProperties(fromMessage, toMessage);

        assertThat(toMessage.getJMSMessageID()).isEqualTo("ID:source-message");
        assertThat(toMessage.getJMSCorrelationID()).isEqualTo("correlation-id");
        assertThat(toMessage.getJMSReplyTo()).isEqualTo(new ActiveMQQueue("reply.queue"));
        assertThat(toMessage.getJMSDestination()).isEqualTo(new ActiveMQTopic("orders.topic"));
        assertThat(toMessage.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
        assertThat(toMessage.getJMSDeliveryTime()).isEqualTo(123_456_789L);
        assertThat(toMessage.getJMSRedelivered()).isTrue();
        assertThat(toMessage.getJMSType()).isEqualTo("order-created");
        assertThat(toMessage.getJMSExpiration()).isEqualTo(123_456_999L);
        assertThat(toMessage.getJMSPriority()).isEqualTo(7);
        assertThat(toMessage.getJMSTimestamp()).isEqualTo(987_654_321L);
        assertThat(toMessage.getStringProperty("tenant")).isEqualTo("integration-test");
    }
}
