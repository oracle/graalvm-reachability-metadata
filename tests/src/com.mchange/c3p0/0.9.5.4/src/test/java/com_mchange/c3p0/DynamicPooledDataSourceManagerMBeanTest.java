/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.management.DynamicPooledDataSourceManagerMBean;
import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

public class DynamicPooledDataSourceManagerMBeanTest {
    @Test
    void exposesPooledDataSourceAttributesAndOperationsThroughDynamicMBean() throws Exception {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ComboPooledDataSource dataSource = new ComboPooledDataSource(false);
        dataSource.setIdentityToken("dynamicPdsToken");
        dataSource.setDataSourceName("dynamicPds");

        DynamicPooledDataSourceManagerMBean manager = new DynamicPooledDataSourceManagerMBean(
                dataSource,
                objectNameFor(dataSource),
                server);

        try {
            MBeanInfo mBeanInfo = manager.getMBeanInfo();
            assertThat(mBeanInfo.getAttributes())
                    .extracting(MBeanAttributeInfo::getName)
                    .contains("dataSourceName", "identityToken", "maxPoolSize");
            assertThat(mBeanInfo.getOperations())
                    .extracting(MBeanOperationInfo::getName)
                    .contains("hardReset", "softResetAllUsers");

            assertThat(manager.getAttribute("dataSourceName")).isEqualTo("dynamicPds");

            manager.setAttribute(new Attribute("dataSourceName", "dynamicPdsRenamed"));
            assertThat(dataSource.getDataSourceName()).isEqualTo("dynamicPdsRenamed");
            assertThat(manager.getAttribute("dataSourceName")).isEqualTo("dynamicPdsRenamed");

            Object invokedValue = manager.invoke("getDataSourceName", new Object[0], new String[0]);
            assertThat(invokedValue).isEqualTo("dynamicPdsRenamed");
            assertThat(server.isRegistered(new ObjectName(objectNameFor(dataSource)))).isTrue();
        } finally {
            unregisterIfPresent(server, objectNameFor(dataSource));
            dataSource.close(true);
        }
    }

    private static String objectNameFor(ComboPooledDataSource dataSource) {
        return "com.mchange.v2.c3p0:type=PooledDataSource,identityToken="
                + dataSource.getIdentityToken()
                + ",name="
                + dataSource.getDataSourceName();
    }

    private static void unregisterIfPresent(MBeanServer server, String objectName) throws Exception {
        ObjectName name = new ObjectName(objectName);
        if (server.isRegistered(name)) {
            server.unregisterMBean(name);
        }
    }
}
