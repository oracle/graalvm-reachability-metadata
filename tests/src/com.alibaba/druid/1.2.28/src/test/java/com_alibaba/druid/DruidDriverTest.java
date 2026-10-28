/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.proxy.DruidDriver;
import com.alibaba.druid.proxy.jdbc.DataSourceProxyImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Timeout(120)
public class DruidDriverTest {
    @Test
    void wrapsRealJdbcDriverAndRecordsSql() throws Exception {
        String url = "jdbc:wrap-jdbc:filters=stat:name=wrapped:jdbc:h2:mem:wrapped";
        Properties properties = new Properties();
        properties.setProperty("user", "sa");
        try {
            try (Connection connection = DruidDriver.getInstance().connect(url, properties);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE wrapped_values (id INT)");
                statement.execute("INSERT INTO wrapped_values VALUES (29)");
                assertEquals(29, TestDataSources.queryInt(connection, "SELECT id FROM wrapped_values"));
            }
            DataSourceProxyImpl proxy = DruidDriver.getDataSource(url, properties);
            assertEquals("org.h2.Driver", proxy.getRawDriver().getClass().getName());
            assertFalse(proxy.getDataSourceStat().getSqlStatMap().isEmpty());
        } finally {
            DruidDriver.getProxyDataSources().remove(url);
        }
    }
}
