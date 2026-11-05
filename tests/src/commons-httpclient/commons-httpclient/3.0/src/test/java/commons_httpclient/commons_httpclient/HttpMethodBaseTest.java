/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpMethodBaseTest {
    @Test
    void constructsQueryAndManagesRequestHeaders() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.ok("base behavior")) {
            GetMethod method = new GetMethod(server.url("/search"));
            method.setQueryString(new NameValuePair[] {new NameValuePair("query", "hello world")});
            method.addRequestHeader("X-Client", "old");
            method.setRequestHeader("X-Client", "current");
            method.addRequestHeader("X-Remove", "unused");
            method.removeRequestHeader("X-Remove");
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                assertEquals("base behavior", method.getResponseBodyAsString());
                assertTrue(method.hasBeenUsed());
                String request = server.request();
                assertTrue(request.startsWith("GET /search?query=hello+world HTTP/1.1"));
                assertTrue(request.contains("X-Client: current"));
                assertFalse(request.contains("X-Remove"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
