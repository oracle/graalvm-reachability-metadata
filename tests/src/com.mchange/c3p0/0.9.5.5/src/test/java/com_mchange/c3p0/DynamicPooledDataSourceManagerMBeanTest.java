/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.management.DynamicPooledDataSourceManagerMBean;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynamicPooledDataSourceManagerMBeanTest {
    @Test
    void managesPoolConfigurationAndOperationsThroughJmx() throws Exception {
        ComboPooledDataSource source = C3p0Support.pool();
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        try {
            new DynamicPooledDataSourceManagerMBean(source, "test:type=Pool", server);
            ObjectName name = server.queryNames(null, null).stream()
                            .filter(candidate -> candidate.getDomain().equals("com.mchange.v2.c3p0"))
                            .findFirst().orElseThrow();
            assertTrue(server.getMBeanInfo(name).getOperations().length > 0);
            assertEquals(1, server.getAttribute(name, "maxPoolSize"));
            server.setAttribute(name, new Attribute("maxPoolSize", 2));
            assertEquals(2, source.getMaxPoolSize());
            assertEquals(2, server.getAttribute(name, "maxPoolSize"));
            try (Connection connection = source.getConnection()) {
                C3p0Support.assertQuery(connection, 57);
                assertEquals(1, server.getAttribute(name, "numBusyConnectionsDefaultUser"));
            }
            server.invoke(name, "softResetDefaultUser", new Object[0], new String[0]);
            try (Connection connection = source.getConnection()) {
                C3p0Support.assertQuery(connection, 58);
            }
        } finally {
            source.close();
            for (ObjectName name : server.queryNames(new ObjectName("com.mchange.v2.c3p0:*"), null)) {
                server.unregisterMBean(name);
            }
        }
    }
}
