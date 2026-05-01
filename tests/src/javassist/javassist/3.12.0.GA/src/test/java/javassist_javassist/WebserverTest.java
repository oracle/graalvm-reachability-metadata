/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javassist.tools.web.BadHttpRequest;
import javassist.tools.web.Webserver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebserverTest {
    @Test
    void doReplyServesClassResourceFromClasspathWhenFileIsNotPresent() throws IOException, BadHttpRequest {
        Webserver server = new Webserver(0);
        try {
            ByteArrayOutputStream response = new ByteArrayOutputStream();

            server.doReply(
                    new ByteArrayInputStream(new byte[0]),
                    response,
                    "GET /javassist/tools/web/Webserver.class HTTP/1.0");

            byte[] responseBytes = response.toByteArray();
            int bodyOffset = findBodyOffset(responseBytes);
            String header = new String(responseBytes, 0, bodyOffset, StandardCharsets.US_ASCII);

            assertThat(header).contains("HTTP/1.0 200 OK");
            assertThat(header).contains("Content-Type: application/octet-stream");
            assertThat(responseBytes[bodyOffset] & 0xFF).isEqualTo(0xCA);
            assertThat(responseBytes[bodyOffset + 1] & 0xFF).isEqualTo(0xFE);
            assertThat(responseBytes[bodyOffset + 2] & 0xFF).isEqualTo(0xBA);
            assertThat(responseBytes[bodyOffset + 3] & 0xFF).isEqualTo(0xBE);
        } finally {
            server.end();
        }
    }

    private static int findBodyOffset(byte[] responseBytes) {
        byte[] separator = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0; offset <= responseBytes.length - separator.length; offset++) {
            if (matchesAt(responseBytes, separator, offset)) {
                return offset + separator.length;
            }
        }
        throw new AssertionError("HTTP response header terminator was not found");
    }

    private static boolean matchesAt(byte[] actual, byte[] expected, int offset) {
        for (int index = 0; index < expected.length; index++) {
            if (actual[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
