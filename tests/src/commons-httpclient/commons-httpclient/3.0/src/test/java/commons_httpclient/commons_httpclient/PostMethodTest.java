/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class PostMethodTest {
    @Test
    void submitsFormEncodedParameters() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.ok("stored")) {
            PostMethod method = new PostMethod(server.url("/form"));
            method.addParameter("message", "hello world");
            method.addParameter("symbol", "a+b");
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                assertEquals("stored", method.getResponseBodyAsString());
                String request = server.request();
                assertTrue(request.startsWith("POST /form HTTP/1.1"));
                assertTrue(request.contains("application/x-www-form-urlencoded"));
                assertTrue(request.endsWith("message=hello+world&symbol=a%2Bb"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
