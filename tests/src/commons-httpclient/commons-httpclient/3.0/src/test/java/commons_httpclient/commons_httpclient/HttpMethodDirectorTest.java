/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpMethodDirectorTest {
    @Test
    void processesResponseCookiesDuringClientExecution() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer("HTTP/1.1 200 OK\r\nSet-Cookie: session=token; Path=/\r\nContent-Length: 4\r\nConnection: close\r\n\r\ndone")) {
            HttpClient client = LocalHttpServer.client();
            GetMethod method = new GetMethod(server.url("/session"));
            try {
                assertEquals(200, client.executeMethod(method));
                assertEquals("done", method.getResponseBodyAsString());
                assertEquals(1, client.getState().getCookies().length);
                assertEquals("token", client.getState().getCookies()[0].getValue());
                assertTrue(server.request().startsWith("GET /session HTTP/1.1"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
