/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.HttpException;
import org.junit.jupiter.api.Test;

public class HttpExceptionTest {
    @Test
    void storesCauseAndPrintsNestedStackTraceToStreamAndWriter() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid header");
        HttpException exception = new HttpException("top-level failure", cause);

        assertThat(exception.getCause()).isSameAs(cause);

        String streamStackTrace = printToStream(exception);
        String writerStackTrace = printToWriter(exception);

        assertThat(streamStackTrace)
                .contains(HttpException.class.getName() + ": top-level failure")
                .contains(IllegalArgumentException.class.getName() + ": invalid header")
                .contains("Caused by:");
        assertThat(writerStackTrace)
                .contains(HttpException.class.getName() + ": top-level failure")
                .contains(IllegalArgumentException.class.getName() + ": invalid header")
                .contains("Caused by:");
    }

    private static String printToStream(HttpException exception) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            exception.printStackTrace(stream);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String printToWriter(HttpException exception) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8)) {
            exception.printStackTrace(writer);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
