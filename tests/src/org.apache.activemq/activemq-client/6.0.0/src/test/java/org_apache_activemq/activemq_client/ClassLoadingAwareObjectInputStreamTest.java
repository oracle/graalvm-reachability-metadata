/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.wireformat.ObjectStreamWireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoadingAwareObjectInputStreamTest {

    @Test
    void resolvesLibraryTypesWhileUnmarshalling() throws Exception {
        ObjectStreamWireFormat wireFormat = new ObjectStreamWireFormat();

        ActiveMQQueue decoded = (ActiveMQQueue) wireFormat.unmarshal(
                wireFormat.marshal(new ActiveMQQueue("serialized.queue")));

        assertThat(decoded.getPhysicalName()).isEqualTo("serialized.queue");
    }
}
