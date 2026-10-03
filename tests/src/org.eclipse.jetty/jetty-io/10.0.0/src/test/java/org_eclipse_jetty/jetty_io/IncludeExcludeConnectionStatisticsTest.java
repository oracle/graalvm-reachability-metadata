/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteArrayEndPoint;
import org.eclipse.jetty.io.IncludeExcludeConnectionStatistics;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.junit.jupiter.api.Test;

public class IncludeExcludeConnectionStatisticsTest {
    @Test
    void includesNamedConnectionTypeInCollectedStatistics() throws Exception {
        String connectionType = "org.eclipse.jetty.io.ssl.SslConnection";
        IncludeExcludeConnectionStatistics statistics = new IncludeExcludeConnectionStatistics();
        statistics.include(connectionType);
        statistics.start();

        ByteArrayEndPoint networkEndPoint = new ByteArrayEndPoint();
        SSLEngine engine = SSLContext.getDefault().createSSLEngine("localhost", 8443);
        SslConnection connection = new SslConnection(
                new ArrayByteBufferPool(), Runnable::run, networkEndPoint, engine);
        try {
            statistics.onOpened(connection);

            assertEquals(1, statistics.getConnections());
            assertEquals(1, statistics.getConnectionsTotal());

            statistics.onClosed(connection);
            assertEquals(0, statistics.getConnections());
            assertEquals(1, statistics.getConnectionsTotal());
        } finally {
            networkEndPoint.close();
            statistics.stop();
        }
    }
}
