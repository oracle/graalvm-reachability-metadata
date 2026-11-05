/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.httpclient.ConnectMethod;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class ConnectMethodTest {
    @Test
    void sendsConnectAuthorityAndReadsProxyResponse() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer("HTTP/1.1 200 Connection Established\r\n\r\n")) {
            HttpConnection connection = new HttpConnection("127.0.0.1", server.port());
            connection.getParams().setConnectionTimeout(10000);
            connection.getParams().setSoTimeout(10000);
            ConnectMethod method = new ConnectMethod();
            try {
                connection.open();
                assertEquals(200, method.execute(new HttpState(), connection));
                assertEquals("Connection Established", method.getStatusText());
                assertTrue(server.request().startsWith("CONNECT 127.0.0.1:" + server.port() + " HTTP/1.1"));
            } finally {
                method.releaseConnection();
                connection.close();
            }
        }
    }
}
