/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class MultiThreadedHttpConnectionManagerTest {
    @Test
    void returnsReleasedConnectionsToPool() throws Exception {
        MultiThreadedHttpConnectionManager manager = new MultiThreadedHttpConnectionManager();
        manager.getParams().setMaxTotalConnections(1);
        HostConfiguration host = new HostConfiguration();
        host.setHost("example.test", 8080);
        try {
            HttpConnection first = manager.getConnectionWithTimeout(host, 10000);
            assertEquals(1, manager.getConnectionsInPool());
            assertEquals("example.test", first.getHost());
            first.releaseConnection();
            assertEquals(1, manager.getConnectionsInPool());
            HttpConnection next = manager.getConnectionWithTimeout(host, 10000);
            assertEquals(1, manager.getConnectionsInPool(host));
            assertEquals(8080, next.getPort());
            next.releaseConnection();
            manager.deleteClosedConnections();
            assertEquals(0, manager.getConnectionsInPool());
        } finally {
            manager.shutdown();
        }
    }
}
