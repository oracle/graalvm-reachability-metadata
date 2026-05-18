/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.jms.JMSException;

import org.apache.activemq.ActiveMQMessageTransformation;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.jupiter.api.Test;

public class ActiveMQMessageTransformationTest {
    @Test
    void copyPropertiesUsesJmsDeliveryTimeWhenAvailable() throws JMSException {
        ActiveMQTextMessage source = new ActiveMQTextMessage();
        source.setJMSMessageID("ID:message");
        source.setJMSCorrelationID("correlation");
        source.setJMSDeliveryTime(1_234L);
        source.setJMSTimestamp(567L);
        source.setStringProperty("customer", "alice");

        ActiveMQMessage target = new ActiveMQMessage();

        ActiveMQMessageTransformation.copyProperties(source, target);

        assertThat(target.getJMSMessageID()).isEqualTo("ID:message");
        assertThat(target.getJMSCorrelationID()).isEqualTo("correlation");
        assertThat(target.getJMSDeliveryTime()).isEqualTo(1_234L);
        assertThat(target.getJMSTimestamp()).isEqualTo(567L);
        assertThat(target.getStringProperty("customer")).isEqualTo("alice");
    }
}
