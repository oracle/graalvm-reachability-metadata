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

import org.apache.commons.httpclient.methods.OptionsMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class OptionsMethodTest {
    @Test
    void discoversMethodsFromAllowHeader() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer("HTTP/1.1 200 OK\r\nAllow: GET, HEAD, POST\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")) {
            OptionsMethod method = new OptionsMethod(server.url("/resource"));
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                assertTrue(method.isAllowed("GET"));
                assertTrue(method.isAllowed("HEAD"));
                assertTrue(method.isAllowed("POST"));
                assertFalse(method.isAllowed("DELETE"));
                assertTrue(server.request().startsWith("OPTIONS /resource HTTP/1.1"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
