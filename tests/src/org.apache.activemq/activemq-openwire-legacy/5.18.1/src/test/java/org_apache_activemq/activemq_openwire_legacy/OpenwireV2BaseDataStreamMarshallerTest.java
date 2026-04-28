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

public class OpenwireV2BaseDataStreamMarshallerTest {

    @Test
    void unmarshalsExceptionResponseWithTightEncoding() throws Exception {
        ExceptionResponse decoded = roundTrip(createException("v2 tight root", "v2 tight cause"), true);

        assertDecodedException(decoded, "v2 tight root", "v2 tight cause");
    }

    @Test
    void unmarshalsExceptionResponseWithLooseEncoding() throws Exception {
        ExceptionResponse decoded = roundTrip(createException("v2 loose root", "v2 loose cause"), false);

        assertDecodedException(decoded, "v2 loose root", "v2 loose cause");
    }

    private ExceptionResponse roundTrip(IllegalStateException exception, boolean tightEncodingEnabled) throws Exception {
        OpenWireFormat wireFormat = new OpenWireFormat(2);
        wireFormat.setStackTraceEnabled(true);
        wireFormat.setTightEncodingEnabled(tightEncodingEnabled);

        return (ExceptionResponse) wireFormat.unmarshal(wireFormat.marshal(new ExceptionResponse(exception)));
    }

    private IllegalStateException createException(String message, String causeMessage) {
        IllegalArgumentException cause = new IllegalArgumentException(causeMessage);
        cause.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("test.support.OpenwireV2Cause", "cause", "OpenwireV2BaseDataStreamMarshallerTest.java", 41)
        });

        IllegalStateException exception = new IllegalStateException(message, cause);
        exception.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("test.support.OpenwireV2Root", "root", "OpenwireV2BaseDataStreamMarshallerTest.java", 33)
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
        assertThat(rootStackTrace[0].getClassName()).isEqualTo("test.support.OpenwireV2Root");
        assertThat(rootStackTrace[0].getMethodName()).isEqualTo("root");
        assertThat(rootStackTrace[0].getFileName()).isEqualTo("OpenwireV2BaseDataStreamMarshallerTest.java");
        assertThat(rootStackTrace[0].getLineNumber()).isEqualTo(33);

        StackTraceElement[] causeStackTrace = cause.getStackTrace();
        assertThat(causeStackTrace).hasSize(1);
        assertThat(causeStackTrace[0].getClassName()).isEqualTo("test.support.OpenwireV2Cause");
        assertThat(causeStackTrace[0].getMethodName()).isEqualTo("cause");
        assertThat(causeStackTrace[0].getFileName()).isEqualTo("OpenwireV2BaseDataStreamMarshallerTest.java");
        assertThat(causeStackTrace[0].getLineNumber()).isEqualTo(41);
    }
}
