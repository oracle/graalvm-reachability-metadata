/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.util.HttpURLConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpURLConnectionTest {
    @Test
    void exposesExecutedMethodThroughUrlConnectionApi() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.ok("adapted body")) {
            GetMethod method = new GetMethod(server.url("/adapter"));
            try {
                assertEquals(200, LocalHttpServer.client().executeMethod(method));
                HttpURLConnection connection = new HttpURLConnection(method, URI.create(server.url("/adapter")).toURL());
                assertEquals("GET", connection.getRequestMethod());
                assertEquals(200, connection.getResponseCode());
                assertEquals("OK", connection.getResponseMessage());
                assertEquals("text/plain; charset=UTF-8", connection.getHeaderField("Content-Type"));
                assertEquals("adapted body", new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                assertTrue(server.request().startsWith("GET /adapter HTTP/1.1"));
            } finally {
                method.releaseConnection();
            }
        }
    }
}
