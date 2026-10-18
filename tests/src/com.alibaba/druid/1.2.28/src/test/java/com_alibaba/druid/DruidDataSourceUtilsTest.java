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
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DruidDataSourceUtilsTest {
    @Test
    void readsStatisticsFromDataSourceSubclass() throws Exception {
        DerivedDataSource dataSource = new DerivedDataSource();
        dataSource.setName("utility-pool");
        dataSource.setUrl("jdbc:h2:mem:druid_utils;DB_CLOSE_DELAY=-1");
        dataSource.setRemoveAbandoned(true);
        dataSource.setFilters("stat,wall");

        try {
            dataSource.init();
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("create table utility_item (id integer primary key, name varchar(40))");
                statement.executeUpdate("insert into utility_item values (1, 'one')");
                assertTrue(statement.executeQuery("select * from utility_item").next());
                assertNotNull(DruidDataSourceUtils.getActiveConnectionStackTrace(dataSource));
            }

            assertEquals(dataSource.getUrl(), DruidDataSourceUtils.getUrl(dataSource));
            assertEquals(dataSource.getID(), DruidDataSourceUtils.getID(dataSource));
            assertEquals("utility-pool", DruidDataSourceUtils.getName(dataSource));
            assertNotNull(DruidDataSourceUtils.getObjectName(dataSource));
            assertTrue(DruidDataSourceUtils.isRemoveAbandoned(dataSource));
            assertNotNull(DruidDataSourceUtils.getStatDataForMBean(dataSource));
            assertNotNull(DruidDataSourceUtils.getStatData(dataSource));
            Map<?, ?> sqlStats = DruidDataSourceUtils.getSqlStatMap(dataSource);
            assertNotNull(sqlStats);
            assertFalse(sqlStats.isEmpty());
            JdbcSqlStat sqlStat = (JdbcSqlStat) sqlStats.values().iterator().next();
            assertNotNull(DruidDataSourceUtils.getSqlStat(dataSource, (int) sqlStat.getId()));
            assertNotNull(DruidDataSourceUtils.getWallStatMap(dataSource));
            assertNotNull(DruidDataSourceUtils.getPoolingConnectionInfo(dataSource));
        } finally {
            dataSource.close();
        }
    }

    public static final class DerivedDataSource extends DruidDataSource {
    }
}
