/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.PoolBackedDataSource;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicPooledDataSourceManagerMBeanTest {
    @Test
    void readsWritesAndInvokesManagedAttributes() throws Exception {
        PoolBackedDataSource dataSource = C3p0TestSupport.newPoolBackedDataSource("mbean", false, 0);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        try {
            try (Connection ignored = dataSource.getConnection()) {
                ObjectName name = findManagedBean(server, dataSource.getIdentityToken());

                assertThat(server.getAttribute(name, "numHelperThreads")).isEqualTo(1);

                server.setAttribute(name, new Attribute("numHelperThreads", 2));
                assertThat(server.getAttribute(name, "numHelperThreads")).isEqualTo(2);
                assertThat(server.invoke(name, "getNumHelperThreads", new Object[0], new String[0])).isEqualTo(2);

                server.setAttribute(name, new Attribute("dataSourceName", "renamed"));
                ObjectName renamed = findManagedBean(server, dataSource.getIdentityToken());
                assertThat(renamed.toString()).contains("name=renamed");
                assertThat(server.getMBeanInfo(renamed).getAttributes()).isNotEmpty();
            }
        } finally {
            unregisterManagedBeans(server, dataSource.getIdentityToken());
            dataSource.close();
        }
    }

    private static ObjectName findManagedBean(MBeanServer server, String identityToken) throws Exception {
        Set<ObjectName> names = server.queryNames(new ObjectName("com.mchange.v2.c3p0:type=PooledDataSource,*"), null);
        for (ObjectName name : names) {
            if (name.toString().contains(identityToken)) {
                return name;
            }
        }
        throw new IllegalStateException("Managed c3p0 MBean not found for identity token: " + identityToken);
    }

    private static void unregisterManagedBeans(MBeanServer server, String identityToken) throws Exception {
        Set<ObjectName> names = server.queryNames(new ObjectName("com.mchange.v2.c3p0:type=PooledDataSource,*"), null);
        for (ObjectName name : names) {
            if (name.toString().contains(identityToken)) {
                server.unregisterMBean(name);
            }
        }
    }
}
