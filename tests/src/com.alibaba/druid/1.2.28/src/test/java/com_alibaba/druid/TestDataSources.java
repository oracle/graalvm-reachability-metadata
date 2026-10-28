/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class TestDataSources {
    private TestDataSources() {
    }

    static DruidDataSource create(String name) {
        return configure(new DruidDataSource(), name);
    }

    static <T extends DruidDataSource> T configure(T dataSource, String name) {
        dataSource.setName(name);
        dataSource.setUrl("jdbc:h2:mem:" + name);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaxActive(2);
        dataSource.setMaxWait(10000);
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setValidationQueryTimeout(10);
        return dataSource;
    }

    static int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                throw new SQLException("Expected a result row");
            }
            return rows.getInt(1);
        }
    }
}
