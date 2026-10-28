/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceStatLogger;
import com.alibaba.druid.pool.DruidDataSourceStatLoggerAdapter;
import com.alibaba.druid.pool.DruidDataSourceStatValue;
import com.alibaba.druid.stat.DruidDataSourceStatManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DruidDataSourceStatManagerTest {
    @Test
    void resetsAndLogsRegisteredPoolStatistics() throws Exception {
        List<DruidDataSourceStatValue> snapshots = new ArrayList<>();
        DruidDataSourceStatLogger logger = new DruidDataSourceStatLoggerAdapter() {
            @Override
            public void log(DruidDataSourceStatValue value) {
                snapshots.add(value);
            }
        };
        try (DruidDataSource dataSource = TestDataSources.create("manager_reset")) {
            dataSource.setStatLogger(logger);
            dataSource.setFilters("stat");
            try (Connection connection = dataSource.getConnection()) {
                assertEquals(43, TestDataSources.queryInt(connection, "SELECT 43"));
            }
            assertTrue(dataSource.getConnectCount() > 0);
            DruidDataSourceStatManager manager = DruidDataSourceStatManager.getInstance();
            long resets = manager.getResetCount();
            manager.reset();
            assertEquals(resets + 1, manager.getResetCount());
            assertEquals(0, dataSource.getConnectCount());
            try (Connection connection = dataSource.getConnection()) {
                assertEquals(47, TestDataSources.queryInt(connection, "SELECT 47"));
            }
            manager.logAndResetDataSource();
            assertEquals(1, snapshots.size());
            assertEquals("manager_reset", snapshots.get(0).getName());
            assertEquals(1, snapshots.get(0).getConnectCount());
            assertEquals(0, dataSource.getConnectCount());
        }
    }
}
