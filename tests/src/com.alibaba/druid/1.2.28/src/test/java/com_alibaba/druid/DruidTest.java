/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.stat.JdbcSqlStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DruidTest {
    @Test
    void collectsSqlStatisticsAndExposesManagementBeans() throws Exception {
        assertTrue(DriverManager.drivers().anyMatch(driver ->
                driver.getClass().getName().equals("com.alibaba.druid.mock.MockDriver")));
        assertTrue(DriverManager.drivers().anyMatch(driver ->
                driver.getClass().getName().equals("com.alibaba.druid.proxy.DruidDriver")));
        try (DruidDataSource dataSource = TestDataSources.create("management")) {
            dataSource.setFilters("stat");
            dataSource.init();
            assertEquals(1, dataSource.getProxyFilters().size());
            assertInstanceOf(StatFilter.class, dataSource.getProxyFilters().get(0));
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE records (id INT)");
                statement.execute("INSERT INTO records VALUES (7)");
                assertEquals(1, TestDataSources.queryInt(connection, "SELECT COUNT(*) FROM records"));
            }
            assertEquals(3, dataSource.getDataSourceStat().getSqlStatMap().size());
            for (JdbcSqlStat stat : dataSource.getDataSourceStat().getSqlStatMap().values()) {
                assertEquals(1L, stat.getData().get("ExecuteCount"));
            }
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName poolName = dataSource.getObjectName();
            assertEquals(dataSource.getUrl(), server.getAttribute(poolName, "Url"));
            assertEquals(0, server.getAttribute(poolName, "ActiveCount"));
            assertTrue(server.getMBeanInfo(poolName).getAttributes().length > 10);
            ObjectName manager = new ObjectName("com.alibaba.druid:type=DruidDataSourceStat");
            assertTrue(server.getMBeanInfo(manager).getAttributes().length >= 2);
            TabularData pools = (TabularData) server.getAttribute(manager, "DataSourceList");
            assertFalse(pools.isEmpty());
            assertInstanceOf(Long.class, server.getAttribute(manager, "ResetCount"));
            ObjectName driver = new ObjectName("com.alibaba.druid:type=DruidDriver");
            assertEquals("jdbc:wrap-jdbc:", server.getAttribute(driver, "AcceptPrefix"));
            ObjectName mock = new ObjectName("com.alibaba.druid:type=MockDriver");
            assertInstanceOf(Integer.class, server.getAttribute(mock, "MajorVersion"));
            ObjectName service = new ObjectName("com.alibaba.druid:type=DruidStatService");
            String json = (String) server.invoke(service, "service", new Object[]{"/datasource.json"},
                    new String[]{"java.lang.String"});
            assertTrue(json.contains("management"));
            assertTrue(json.contains("\"ResultCode\":1"));
        }
    }
}
