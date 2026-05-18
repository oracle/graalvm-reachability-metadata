/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.util.ByteSequence;
import org.junit.jupiter.api.Test;

public class ActiveMQObjectMessageTest {
    @Test
    void setObjectStoresSerializedContentAndGetObjectDeserializesIt() throws Exception {
        ActiveMQObjectMessage message = new ActiveMQObjectMessage();
        String payload = "native-image object message payload";

        message.setObject(payload);

        ByteSequence storedContent = message.getContent();
        assertThat(storedContent).isNotNull();
        assertThat(storedContent.length).isGreaterThan(0);

        message.clearUnMarshalledState();

        assertThat(message.getObject()).isEqualTo(payload);
    }
}
