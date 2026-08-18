/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import org.apache.activemq.MaxFrameSizeExceededException;
import org.apache.activemq.command.ExceptionResponse;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.util.ByteSequence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenWireV11BaseDataStreamMarshallerTest {
    private static final int OPENWIRE_VERSION_11 = 11;

    @Test
    void tightUnmarshalRestoresThrowableWithStackTrace() throws Exception {
        OpenWireFormat wireFormat = openWireFormat(true);
        ExceptionResponse original = new ExceptionResponse(frameSizeException());

        ExceptionResponse unmarshalled = (ExceptionResponse) wireFormat.unmarshal(wireFormat.marshal(original));

        assertRestoredException(unmarshalled.getException());
    }

    @Test
    void looseUnmarshalRestoresThrowableWithStackTrace() throws Exception {
        OpenWireFormat wireFormat = openWireFormat(false);
        ExceptionResponse original = new ExceptionResponse(frameSizeException());

        ByteSequence marshalled = wireFormat.marshal(original);
        ExceptionResponse unmarshalled = (ExceptionResponse) wireFormat.unmarshal(marshalled);

        assertRestoredException(unmarshalled.getException());
    }

    private static OpenWireFormat openWireFormat(boolean tightEncodingEnabled) {
        OpenWireFormat wireFormat = new OpenWireFormat(OPENWIRE_VERSION_11);
        wireFormat.setStackTraceEnabled(true);
        wireFormat.setTightEncodingEnabled(tightEncodingEnabled);
        return wireFormat;
    }

    private static MaxFrameSizeExceededException frameSizeException() {
        MaxFrameSizeExceededException exception = new MaxFrameSizeExceededException("frame is too large");
        exception.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("example.ActiveMqClient", "send", "ActiveMqClient.java", 41)
        });
        return exception;
    }

    private static void assertRestoredException(Throwable exception) {
        assertThat(exception)
                .isInstanceOf(MaxFrameSizeExceededException.class)
                .hasMessage("frame is too large");
        assertThat(exception.getStackTrace()).containsExactly(
                new StackTraceElement("example.ActiveMqClient", "send", "ActiveMqClient.java", 41));
        assertThat(exception.getCause()).isNull();
    }
}
