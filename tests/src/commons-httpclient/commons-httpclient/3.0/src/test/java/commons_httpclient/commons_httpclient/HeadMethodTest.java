/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.methods.HeadMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HeadMethodTest {
    @Test
    void readsHeadersWithoutReadingResponseBody() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer("HTTP/1.1 200 OK\r\nContent-Length: 123\r\nX-Resource: available\r\nConnection: close\r\n\r\n")) {
            HeadMethod method = new HeadMethod(server.url("/resource"));
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                assertEquals("available", method.getResponseHeader("X-Resource").getValue());
                assertEquals(123, method.getResponseContentLength());
                assertNull(method.getResponseBodyAsStream());
                assertTrue(server.request().startsWith("HEAD /resource HTTP/1.1"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
