/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpParserTest {
    @Test
    void parsesFoldedAndRepeatedResponseHeaders() throws Exception {
        byte[] wire = "Content-Type: text/plain\r\nX-Note: first\r\n second\r\nX-Note: third\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        Header[] headers = HttpParser.parseHeaders(new ByteArrayInputStream(wire), "US-ASCII");
        assertEquals(3, headers.length);
        assertEquals("Content-Type", headers[0].getName());
        assertEquals("first second", headers[1].getValue());
        assertEquals("third", headers[2].getValue());
    }
}
