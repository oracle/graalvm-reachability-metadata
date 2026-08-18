/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import org.apache.activemq.util.ByteSequence;
import org.apache.activemq.wireformat.ObjectStreamWireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectStreamWireFormatTest {

    @Test
    void serializablePayloadRoundTripsThroughObjectStream() throws Exception {
        ObjectStreamWireFormat wireFormat = new ObjectStreamWireFormat();
        String expectedPayload = "active-mq-object-stream-payload";

        ByteSequence marshalled = wireFormat.marshal(expectedPayload);
        Object unmarshalled = wireFormat.unmarshal(marshalled);

        assertThat(unmarshalled).isEqualTo(expectedPayload);
    }
}
