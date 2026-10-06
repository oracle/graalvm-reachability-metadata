/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQMessageTransformation;
import org.apache.activemq.command.ActiveMQMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActiveMQMessageTransformationTest {

    @Test
    void copiesJmsDeliveryTimeAlongWithOtherHeaders() throws Exception {
        ActiveMQMessage source = new ActiveMQMessage();
        source.setJMSDeliveryTime(12345L);
        source.setJMSTimestamp(123L);
        source.setStringProperty("source", "external");
        ActiveMQMessage target = new ActiveMQMessage();

        ActiveMQMessageTransformation.copyProperties(source, target);

        assertThat(target.getJMSDeliveryTime()).isEqualTo(12345L);
        assertThat(target.getStringProperty("source")).isEqualTo("external");
    }
}
