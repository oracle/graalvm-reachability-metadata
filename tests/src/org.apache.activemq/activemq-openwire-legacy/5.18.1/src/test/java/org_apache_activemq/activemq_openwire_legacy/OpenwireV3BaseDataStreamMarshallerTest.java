/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_openwire_legacy;

import org.apache.activemq.command.ExceptionResponse;
import org.apache.activemq.openwire.OpenWireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenwireV3BaseDataStreamMarshallerTest {

    @Test
    void unmarshalsExceptionResponseWithTightEncoding() throws Exception {
        ExceptionResponse decoded = roundTrip(createException("v3 tight root", "v3 tight cause"), true);

        assertDecodedException(decoded, "v3 tight root", "v3 tight cause");
    }

    @Test
    void unmarshalsExceptionResponseWithLooseEncoding() throws Exception {
        ExceptionResponse decoded = roundTrip(createException("v3 loose root", "v3 loose cause"), false);

        assertDecodedException(decoded, "v3 loose root", "v3 loose cause");
    }

    private ExceptionResponse roundTrip(IllegalStateException exception, boolean tightEncodingEnabled) throws Exception {
        OpenWireFormat wireFormat = new OpenWireFormat(3);
        wireFormat.setStackTraceEnabled(true);
        wireFormat.setTightEncodingEnabled(tightEncodingEnabled);

        return (ExceptionResponse) wireFormat.unmarshal(wireFormat.marshal(new ExceptionResponse(exception)));
    }

    private IllegalStateException createException(String message, String causeMessage) {
        IllegalArgumentException cause = new IllegalArgumentException(causeMessage);
        cause.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("test.support.OpenwireV3Cause", "cause", "OpenwireV3BaseDataStreamMarshallerTest.java", 41)
        });

        IllegalStateException exception = new IllegalStateException(message, cause);
        exception.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("test.support.OpenwireV3Root", "root", "OpenwireV3BaseDataStreamMarshallerTest.java", 33)
        });
        return exception;
    }

    private void assertDecodedException(ExceptionResponse decoded, String expectedMessage, String expectedCauseMessage) {
        Throwable exception = decoded.getException();
        assertThat(exception).isInstanceOf(IllegalStateException.class);
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);

        Throwable cause = exception.getCause();
        assertThat(cause).isInstanceOf(IllegalArgumentException.class);
        assertThat(cause.getMessage()).isEqualTo(expectedCauseMessage);

        StackTraceElement[] rootStackTrace = exception.getStackTrace();
        assertThat(rootStackTrace).hasSize(1);
        assertThat(rootStackTrace[0].getClassName()).isEqualTo("test.support.OpenwireV3Root");
        assertThat(rootStackTrace[0].getMethodName()).isEqualTo("root");
        assertThat(rootStackTrace[0].getFileName()).isEqualTo("OpenwireV3BaseDataStreamMarshallerTest.java");
        assertThat(rootStackTrace[0].getLineNumber()).isEqualTo(33);

        StackTraceElement[] causeStackTrace = cause.getStackTrace();
        assertThat(causeStackTrace).hasSize(1);
        assertThat(causeStackTrace[0].getClassName()).isEqualTo("test.support.OpenwireV3Cause");
        assertThat(causeStackTrace[0].getMethodName()).isEqualTo("cause");
        assertThat(causeStackTrace[0].getFileName()).isEqualTo("OpenwireV3BaseDataStreamMarshallerTest.java");
        assertThat(causeStackTrace[0].getLineNumber()).isEqualTo(41);
    }
}
