/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javassist.tools.web.Webserver;

import org.junit.jupiter.api.Test;

public class WebserverTest {
    private static final String RESOURCE_CLASS = "javassist/tools/web/Webserver.class";

    @Test
    void servesClassFileResourceFromClasspathWhenFileIsAbsent() throws Exception {
        Webserver server = new Webserver(0);
        try {
            ByteArrayOutputStream response = new ByteArrayOutputStream();

            server.doReply(
                    new ByteArrayInputStream(new byte[0]),
                    response,
                    "GET /" + RESOURCE_CLASS + " HTTP/1.0");

            byte[] responseBytes = response.toByteArray();
            int bodyOffset = headerLength(responseBytes);
            String header = new String(responseBytes, 0, bodyOffset, StandardCharsets.ISO_8859_1);
            byte[] body = copyOfRange(responseBytes, bodyOffset, responseBytes.length);

            assertThat(header).startsWith("HTTP/1.0 200 OK\r\n");
            assertThat(header).contains("Content-Type: application/octet-stream\r\n");
            assertThat(header).contains("Content-Length: " + body.length + "\r\n");
            assertThat(body).startsWith((byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
        } finally {
            server.end();
        }
    }

    private static int headerLength(byte[] responseBytes) throws IOException {
        for (int i = 0; i <= responseBytes.length - 4; i++) {
            if (responseBytes[i] == '\r'
                    && responseBytes[i + 1] == '\n'
                    && responseBytes[i + 2] == '\r'
                    && responseBytes[i + 3] == '\n') {
                return i + 4;
            }
        }

        throw new IOException("HTTP response did not include a header terminator");
    }

    private static byte[] copyOfRange(byte[] bytes, int from, int to) {
        byte[] copy = new byte[to - from];
        System.arraycopy(bytes, from, copy, 0, copy.length);
        return copy;
    }
}
