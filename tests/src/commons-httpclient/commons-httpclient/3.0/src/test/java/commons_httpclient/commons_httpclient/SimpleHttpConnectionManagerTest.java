/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class SimpleHttpConnectionManagerTest {
    @Test
    void reusesReleasedConnectionAndUpdatesHost() throws Exception {
        SimpleHttpConnectionManager manager = new SimpleHttpConnectionManager();
        HostConfiguration firstHost = new HostConfiguration();
        firstHost.setHost("first.test", 8080);
        HttpConnection first = manager.getConnectionWithTimeout(firstHost, 10000);
        assertEquals("first.test", first.getHost());
        manager.releaseConnection(first);
        HostConfiguration nextHost = new HostConfiguration();
        nextHost.setHost("next.test", 8081);
        HttpConnection next = manager.getConnectionWithTimeout(nextHost, 10000);
        try {
            assertSame(first, next);
            assertEquals("next.test", next.getHost());
            assertEquals(8081, next.getPort());
        } finally {
            manager.releaseConnection(next);
            manager.closeIdleConnections(0);
        }
    }
}
