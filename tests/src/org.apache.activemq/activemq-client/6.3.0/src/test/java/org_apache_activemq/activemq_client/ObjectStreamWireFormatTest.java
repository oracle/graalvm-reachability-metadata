/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.wireformat.ObjectStreamWireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectStreamWireFormatTest {

    @Test
    void marshalsAndUnmarshalsCommands() throws Exception {
        ObjectStreamWireFormat wireFormat = new ObjectStreamWireFormat();

        ActiveMQTopic decoded = (ActiveMQTopic) wireFormat.unmarshal(
                wireFormat.marshal(new ActiveMQTopic("events.topic")));

        assertThat(decoded.getQualifiedName()).isEqualTo("topic://events.topic");
    }
}
