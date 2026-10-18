/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.stat.DruidDataSourceStatManager;
import java.lang.management.ManagementFactory;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DruidDataSourceStatManagerTest {
    @Test
    void managesDataSourceStatisticsAndPublishesMBeans() throws Exception {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setName("jmx-pool");
        dataSource.setUrl("jdbc:h2:mem:druid_jmx;DB_CLOSE_DELAY=-1");

        try {
            dataSource.init();
            DruidDataSourceStatManager manager = DruidDataSourceStatManager.getInstance();
            manager.reset();
            manager.logAndResetDataSource();

            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            assertHasAttributes(server, new ObjectName("com.alibaba.druid:type=DruidDataSourceStat"));
            assertNotNull(dataSource.getObjectName());
            assertHasAttributes(server, dataSource.getObjectName());
        } finally {
            dataSource.close();
        }
    }

    private static void assertHasAttributes(MBeanServer server, ObjectName name) throws Exception {
        MBeanInfo info = server.getMBeanInfo(name);
        assertTrue(info.getAttributes().length > 0, name.toString());
    }
}
