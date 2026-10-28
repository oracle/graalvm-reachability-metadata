/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceStatLoggerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DruidAbstractDataSourceTest {
    @Test
    void configuresAndUsesNamedPoolStrategies() throws Exception {
        try (DruidDataSource dataSource = TestDataSources.create("configured_strategies")) {
            dataSource.setStatLoggerClassName("com.alibaba.druid.pool.DruidDataSourceStatLoggerImpl");
            dataSource.setExceptionSorter("com.alibaba.druid.pool.vendor.MySqlExceptionSorter");
            dataSource.setValidConnectionCheckerClassName("com.alibaba.druid.pool.JDBC4ValidConnectionChecker");
            dataSource.setPasswordCallbackClassName("com.alibaba.druid.util.DruidPasswordCallback");
            dataSource.getPasswordCallback().setPassword(new char[0]);
            dataSource.setFilters("stat");
            try (Connection connection = dataSource.getConnection()) {
                assertEquals(37, TestDataSources.queryInt(connection, "SELECT 37"));
                assertTrue(dataSource.getValidConnectionChecker().isValidConnection(connection, "SELECT 1", 10));
            }
            assertTrue(dataSource.getExceptionSorter().isExceptionFatal(new SQLRecoverableException("disconnected")));
            assertFalse(dataSource.getExceptionSorter().isExceptionFatal(new SQLException("syntax", "42000", 1064)));
            assertInstanceOf(DruidDataSourceStatLoggerImpl.class, dataSource.getStatLogger());
            dataSource.logStats();
            assertEquals(0, dataSource.getSqlStatMap().values().iterator().next().getExecuteCount());
        }
    }
}
