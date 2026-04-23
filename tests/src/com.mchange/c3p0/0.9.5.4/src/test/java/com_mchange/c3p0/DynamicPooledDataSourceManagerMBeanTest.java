/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.PoolBackedDataSource;
import com.mchange.v2.c3p0.management.DynamicPooledDataSourceManagerMBean;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicPooledDataSourceManagerMBeanTest {
    @Test
    void readsWritesAndInvokesManagedAttributes() throws Exception {
        PoolBackedDataSource dataSource = C3p0TestSupport.newPoolBackedDataSource("mbean", false, 0);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        DynamicPooledDataSourceManagerMBean mBean = new DynamicPooledDataSourceManagerMBean(
            dataSource,
            "com.mchange.v2.c3p0.test:type=PooledDataSource,name=" + UUID.randomUUID(),
            server
        );

        try {
            assertThat(mBean.getAttribute("numHelperThreads")).isEqualTo(1);

            mBean.setAttribute(new Attribute("numHelperThreads", 2));
            assertThat(mBean.getAttribute("numHelperThreads")).isEqualTo(2);
            assertThat(mBean.invoke("getNumHelperThreads", new Object[0], new String[0])).isEqualTo(2);

            mBean.setAttribute(new Attribute("dataSourceName", "renamed"));
            assertThat(mBean.getMBeanInfo().getAttributes()).isNotEmpty();
        } finally {
            unregisterManagedBeans(server, dataSource.getIdentityToken());
            dataSource.close();
        }
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
