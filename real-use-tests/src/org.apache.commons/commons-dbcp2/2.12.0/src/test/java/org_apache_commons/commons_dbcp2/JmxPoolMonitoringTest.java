/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises BasicDataSource JMX monitoring of application connection usage and pool shutdown.
/// A shipment service's monitoring collector reports available and exhausted capacity while
/// dispatch requests hold leases, then removes the pool from discovery when the service stops.
public class JmxPoolMonitoringTest {

    @Test
    @Timeout(60)
    public void monitoringTracksDispatchCapacityAndPoolShutdown() throws Exception {
        MBeanServer monitoring = ManagementFactory.getPlatformMBeanServer();
        ObjectName poolName = new ObjectName("shipment.service:type=DataSource,name=dispatch");
        assertFalse(monitoring.isRegistered(poolName));

        try (BasicDataSource dispatch = new BasicDataSource()) {
            dispatch.setJmxName(poolName.toString());
            dispatch.setDriverClassName("org.h2.Driver");
            dispatch.setUrl("jdbc:h2:mem:jmx_dispatch_monitoring;DB_CLOSE_ON_EXIT=FALSE");
            dispatch.setUsername("sa");
            dispatch.setPassword("");
            dispatch.setMaxTotal(2);
            dispatch.setMaxIdle(2);
            dispatch.setMaxWait(Duration.ofSeconds(10));
            dispatch.setDefaultQueryTimeout(Duration.ofSeconds(10));
            dispatch.setDefaultAutoCommit(true);

            try (Connection setup = dispatch.getConnection();
                    Statement schema = setup.createStatement()) {
                schema.executeUpdate("CREATE TABLE shipments (id INTEGER PRIMARY KEY, dispatched BOOLEAN)");
                schema.executeUpdate("INSERT INTO shipments VALUES (101, FALSE), (102, FALSE)");
            }

            assertTrue(monitoring.isRegistered(poolName));
            assertEquals(new PoolUsage(0, 1, 2), collect(monitoring, poolName));

            try (Connection firstDispatch = dispatch.getConnection();
                    Statement firstUpdate = firstDispatch.createStatement()) {
                assertEquals(new PoolUsage(1, 0, 2), collect(monitoring, poolName));
                assertEquals(1, firstUpdate.executeUpdate("UPDATE shipments SET dispatched = TRUE WHERE id = 101"));

                try (Connection secondDispatch = dispatch.getConnection();
                        Statement secondUpdate = secondDispatch.createStatement()) {
                    PoolUsage busy = collect(monitoring, poolName);
                    assertEquals(new PoolUsage(2, 0, 2), busy);
                    assertFalse(busy.hasCapacity(), "The dashboard must report all pool slots in use");
                    assertEquals(1, secondUpdate.executeUpdate(
                            "UPDATE shipments SET dispatched = TRUE WHERE id = 102"));
                }

                PoolUsage available = collect(monitoring, poolName);
                assertEquals(new PoolUsage(1, 1, 2), available);
                assertTrue(available.hasCapacity(), "Returning a lease must restore reported capacity");
            }

            assertEquals(new PoolUsage(0, 2, 2), collect(monitoring, poolName));
            try (Connection report = dispatch.getConnection();
                    Statement query = report.createStatement();
                    ResultSet rows = query.executeQuery("SELECT COUNT(*) FROM shipments WHERE dispatched = TRUE")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
                assertFalse(rows.next());
            }
        }

        assertFalse(monitoring.isRegistered(poolName), "A stopped service must not leave stale pool metrics");
    }

    private static PoolUsage collect(MBeanServer monitoring, ObjectName poolName) throws JMException {
        return new PoolUsage(
                (Integer) monitoring.getAttribute(poolName, "NumActive"),
                (Integer) monitoring.getAttribute(poolName, "NumIdle"),
                (Integer) monitoring.getAttribute(poolName, "MaxTotal"));
    }

    private record PoolUsage(int active, int idle, int limit) {
        private boolean hasCapacity() {
            return active < limit;
        }
    }
}
