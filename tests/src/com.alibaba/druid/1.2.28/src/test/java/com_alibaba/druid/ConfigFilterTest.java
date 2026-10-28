/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.filter.config.ConfigFilter;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class ConfigFilterTest {
    @Test
    void configuresPoolFromClasspathProperties() throws Exception {
        for (String location : new String[]{"classpath:druid-test.properties", "druid-test.properties"}) {
            try (DruidDataSource dataSource = new DruidDataSource()) {
                dataSource.setMaxWait(10000);
                dataSource.setConnectionProperties("config.file=" + location);
                dataSource.setFilters("config");
                dataSource.init();
                assertEquals("jdbc:h2:mem:configured", dataSource.getUrl());
                assertEquals("sa", dataSource.getUsername());
                try (Connection connection = dataSource.getConnection()) {
                    assertEquals(23, TestDataSources.queryInt(connection, "SELECT 23"));
                }
            }
        }
        assertEquals("sa", new ConfigFilter().loadConfig("classpath:druid-test.properties").getProperty("username"));
    }
}
