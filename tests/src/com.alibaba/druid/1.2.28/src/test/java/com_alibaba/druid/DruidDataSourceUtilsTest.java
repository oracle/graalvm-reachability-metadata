/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.stat.JdbcSqlStat;
import com.alibaba.druid.util.DruidDataSourceUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DruidDataSourceUtilsTest {
    @Test
    void monitorsAnExtendedPoolThroughUtilityFacade() throws Exception {
        try (ApplicationDataSource dataSource = TestDataSources.configure(new ApplicationDataSource(), "facade")) {
            dataSource.setFilters("stat,wall");
            dataSource.setRemoveAbandoned(true);
            dataSource.setRemoveAbandonedTimeoutMillis(60000);
            dataSource.init();
            try (Connection connection = dataSource.getConnection()) {
                assertEquals(31, TestDataSources.queryInt(connection, "SELECT 31"));
                assertFalse(DruidDataSourceUtils.getActiveConnectionStackTrace(dataSource).isEmpty());
                assertEquals(1, DruidDataSourceUtils.getStatData(dataSource).get("ActiveCount"));
            }
            JdbcSqlStat sql = dataSource.getSqlStatMap().values().iterator().next();
            assertSame(sql, DruidDataSourceUtils.getSqlStat(dataSource, (int) sql.getId()));
            assertEquals(dataSource.getSqlStatMap(), DruidDataSourceUtils.getSqlStatMap(dataSource));
            assertEquals(dataSource.getUrl(), DruidDataSourceUtils.getUrl(dataSource));
            assertEquals(dataSource.getID(), DruidDataSourceUtils.getID(dataSource));
            assertEquals("facade", DruidDataSourceUtils.getName(dataSource));
            assertEquals(dataSource.getObjectName(), DruidDataSourceUtils.getObjectName(dataSource));
            assertNotNull(DruidDataSourceUtils.getObjectName(dataSource));
            assertTrue(DruidDataSourceUtils.isRemoveAbandoned(dataSource));
            assertEquals("facade", DruidDataSourceUtils.getStatDataForMBean(dataSource).get("Name"));
            assertEquals(dataSource.getUrl(), DruidDataSourceUtils.getStatDataForMBean(dataSource).get("URL"));
            assertNotNull(DruidDataSourceUtils.getWallStatMap(dataSource));
            assertFalse(DruidDataSourceUtils.getWallStatMap(dataSource).isEmpty());
            assertEquals(1, DruidDataSourceUtils.getPoolingConnectionInfo(dataSource).size());
        }
    }

    public static class ApplicationDataSource extends DruidDataSource {
    }
}
