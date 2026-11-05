/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpClientTest {
    @Test
    void instantiatesConfiguredConnectionManagerAndExecutesRequest() throws Exception {
        HttpClientParams params = new HttpClientParams();
        params.setConnectionManagerClass(MultiThreadedHttpConnectionManager.class);
        HttpClient client = new HttpClient(params);
        assertInstanceOf(MultiThreadedHttpConnectionManager.class, client.getHttpConnectionManager());
        MultiThreadedHttpConnectionManager manager = (MultiThreadedHttpConnectionManager) client.getHttpConnectionManager();
        manager.getParams().setConnectionTimeout(10000);
        manager.getParams().setSoTimeout(10000);
        try (LocalHttpServer server = LocalHttpServer.ok("configured manager")) {
            GetMethod method = new GetMethod(server.url("/manager"));
            try {
                assertEquals(200, client.executeMethod(method));
                assertEquals("configured manager", method.getResponseBodyAsString());
                assertTrue(server.request().startsWith("GET /manager HTTP/1.1"));
            } finally {
                method.releaseConnection();
            }
        } finally {
            manager.shutdown();
        }
    }
}
