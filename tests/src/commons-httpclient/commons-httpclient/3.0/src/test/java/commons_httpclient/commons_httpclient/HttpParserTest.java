/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpParser;
import org.apache.commons.httpclient.ProtocolException;
import org.junit.jupiter.api.Test;

public class HttpParserTest {
    private static final String HTTP_PARSER_CLASS_NAME = "org.apache.commons.httpclient.HttpParser";

    @Test
    void readLineDecodesConfiguredCharsetAndStripsLineTerminator() throws Exception {
        byte[] rawResponse = "X-Name: caf\u00e9\r\nNext: value\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream input = new ByteArrayInputStream(rawResponse);

        assertThat(HttpParser.readLine(input, StandardCharsets.UTF_8.name()))
                .isEqualTo("X-Name: caf\u00e9");
        assertThat(HttpParser.readLine(input, StandardCharsets.US_ASCII.name()))
                .isEqualTo("Next: value");
        assertThat(HttpParser.readLine(input, StandardCharsets.US_ASCII.name())).isNull();
    }

    @Test
    void readRawLineReturnsLineIncludingTerminatorAndPartialFinalLine() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(
                "first\nlast".getBytes(StandardCharsets.US_ASCII));

        assertThat(HttpParser.readRawLine(input))
                .isEqualTo("first\n".getBytes(StandardCharsets.US_ASCII));
        assertThat(HttpParser.readRawLine(input))
                .isEqualTo("last".getBytes(StandardCharsets.US_ASCII));
        assertThat(HttpParser.readRawLine(input)).isNull();
    }

    @Test
    void parseHeadersPreservesOrderAndCombinesFoldedContinuationLines() throws Exception {
        String headerBlock = "Host: example.test\r\n"
                + "X-Folded: first\r\n"
                + "\tsecond\r\n"
                + " third\r\n"
                + "X-Empty:   \r\n"
                + "\r\n";
        ByteArrayInputStream input = new ByteArrayInputStream(
                headerBlock.getBytes(StandardCharsets.US_ASCII));

        Header[] headers = HttpParser.parseHeaders(input, StandardCharsets.US_ASCII.name());

        assertThat(headers)
                .extracting(Header::getName, Header::getValue)
                .containsExactly(
                        tuple("Host", "example.test"),
                        tuple("X-Folded", "first second third"),
                        tuple("X-Empty", ""));
    }

    @Test
    void parseHeadersRejectsLineWithoutColon() {
        ByteArrayInputStream input = new ByteArrayInputStream("Invalid header\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII));

        assertThatExceptionOfType(ProtocolException.class)
                .isThrownBy(() -> HttpParser.parseHeaders(input, StandardCharsets.US_ASCII.name()))
                .withMessage("Unable to parse header: Invalid header");
    }

    @Test
    void compilerGeneratedClassLookupReturnsHttpParserClass() throws Exception {
        Method classLookup = HttpParser.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        Object resolvedClass = classLookup.invoke(null, HTTP_PARSER_CLASS_NAME);

        assertThat(resolvedClass).isSameAs(HttpParser.class);
    }
}
