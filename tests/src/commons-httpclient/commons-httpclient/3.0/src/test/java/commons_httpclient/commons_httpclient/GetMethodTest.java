/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class GetMethodTest {
    @Test
    void retrievesResponseBodyAndSendsQuery() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.ok("hello client")) {
            GetMethod method = new GetMethod(server.url("/greeting?language=en"));
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                assertEquals("hello client", method.getResponseBodyAsString());
                assertEquals("text/plain; charset=UTF-8", method.getResponseHeader("Content-Type").getValue());
                assertTrue(server.request().startsWith("GET /greeting?language=en HTTP/1.1"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
