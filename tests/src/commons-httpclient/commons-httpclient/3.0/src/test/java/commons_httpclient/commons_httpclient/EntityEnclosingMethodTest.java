/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class EntityEnclosingMethodTest {
    @Test
    void writesRequestEntityAndContentType() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.ok("updated")) {
            PutMethod method = new PutMethod(server.url("/document"));
            method.setRequestEntity(new StringRequestEntity("document payload", "text/plain", "UTF-8"));
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                assertEquals("updated", method.getResponseBodyAsString());
                String request = server.request();
                assertTrue(request.startsWith("PUT /document HTTP/1.1"));
                assertTrue(request.contains("Content-Type: text/plain; charset=UTF-8"));
                assertTrue(request.endsWith("document payload"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
