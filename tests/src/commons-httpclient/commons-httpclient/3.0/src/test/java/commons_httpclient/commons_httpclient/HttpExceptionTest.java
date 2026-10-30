/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.apache.commons.httpclient.HttpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpExceptionTest {
    @Test
    void preservesCauseInBothStackTraceOutputs() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid header");
        HttpException exception = new HttpException("request rejected", cause);
        assertSame(cause, exception.getCause());
        assertEquals("request rejected", exception.getMessage());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            exception.printStackTrace(stream);
        }
        StringWriter text = new StringWriter();
        try (PrintWriter writer = new PrintWriter(text)) {
            exception.printStackTrace(writer);
        }
        for (String trace : new String[] {bytes.toString(StandardCharsets.UTF_8), text.toString()}) {
            assertTrue(trace.contains("request rejected"));
            assertTrue(trace.contains("Caused by: java.lang.IllegalArgumentException: invalid header"));
        }
    }
}
