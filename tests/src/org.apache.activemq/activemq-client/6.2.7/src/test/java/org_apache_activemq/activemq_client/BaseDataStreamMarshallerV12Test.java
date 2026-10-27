/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.command.ExceptionResponse;
import org.apache.activemq.openwire.OpenWireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseDataStreamMarshallerV12Test {

    @Test
    void roundTripsExceptionsWithBothEncodings() throws Exception {
        OpenWireFormat wireFormat = new OpenWireFormat();
        wireFormat.setVersion(12);
        wireFormat.setStackTraceEnabled(true);

        for (boolean tightEncoding : new boolean[] {true, false}) {
            wireFormat.setTightEncodingEnabled(tightEncoding);
            ExceptionResponse decoded = (ExceptionResponse) wireFormat.unmarshal(
                    wireFormat.marshal(new ExceptionResponse(new IllegalStateException("v12 failure"))));

            assertThat(decoded.getException())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("v12 failure");
        }
    }
}
