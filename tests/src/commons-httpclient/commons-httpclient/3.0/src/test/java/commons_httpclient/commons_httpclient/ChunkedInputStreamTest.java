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
import org.apache.commons.httpclient.ChunkedInputStream;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class ChunkedInputStreamTest {
    @Test
    void decodesChunksAndPublishesTrailers() throws Exception {
        GetMethod method = new GetMethod();
        byte[] wire = "5;extension=value\r\nhello\r\n6\r\n world\r\n0\r\nX-Checksum: verified\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        try (ChunkedInputStream input = new ChunkedInputStream(new ByteArrayInputStream(wire), method)) {
            assertEquals("hello world", new String(input.readAllBytes(), StandardCharsets.US_ASCII));
            assertEquals(-1, input.read());
            assertEquals("verified", method.getResponseFooter("X-Checksum").getValue());
        }
    }
}
