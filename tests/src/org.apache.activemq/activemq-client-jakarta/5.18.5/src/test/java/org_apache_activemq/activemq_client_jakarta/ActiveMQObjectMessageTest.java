/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import java.io.Serializable;

import jakarta.jms.JMSException;

import org.apache.activemq.command.ActiveMQObjectMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQObjectMessageTest {

    @Test
    void objectBodyRoundTripsThroughSerializedContent() throws JMSException {
        ActiveMQObjectMessage message = new ActiveMQObjectMessage();
        String expectedBody = "active-mq-object-message-body";

        message.setObject(expectedBody);
        message.clearUnMarshalledState();
        Serializable actualBody = message.getObject();

        assertThat(actualBody).isEqualTo(expectedBody);
    }
}
