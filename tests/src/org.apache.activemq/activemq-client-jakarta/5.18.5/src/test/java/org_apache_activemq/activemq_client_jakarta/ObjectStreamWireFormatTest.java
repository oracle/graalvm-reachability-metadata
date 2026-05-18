/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.util.ByteSequence;
import org.apache.activemq.wireformat.ObjectStreamWireFormat;
import org.junit.jupiter.api.Test;

public class ObjectStreamWireFormatTest {
    @Test
    void marshalAndUnmarshalRoundTripsSerializableCommand() throws Exception {
        ObjectStreamWireFormat wireFormat = new ObjectStreamWireFormat();
        String payload = "native-image object stream wire format payload";

        ByteSequence marshalled = wireFormat.marshal(payload);

        assertThat(marshalled).isNotNull();
        assertThat(marshalled.length).isGreaterThan(0);
        assertThat(wireFormat.unmarshal(marshalled)).isEqualTo(payload);
    }
}
