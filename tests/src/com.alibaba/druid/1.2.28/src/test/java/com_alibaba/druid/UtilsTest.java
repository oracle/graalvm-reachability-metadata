/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class UtilsTest {
    @Test
    void readsPackagedConfigurationThroughEachResourceApi() throws Exception {
        String resource = "META-INF/druid-filter.properties";
        assertEquals("com.alibaba.druid.filter.stat.StatFilter",
                Utils.loadProperties(resource).getProperty("druid.filters.stat"));
        assertTrue(Utils.readLines(resource).stream().anyMatch(line -> line.contains("druid.filters.stat=")));
        String text = Utils.readFromResource(resource);
        assertNotNull(text);
        assertEquals(text, new String(Utils.readByteArrayFromResource(resource), StandardCharsets.UTF_8));
        assertEquals(text, Utils.readFromResource("/" + resource));
        Set<String> keywords = new HashSet<>();
        Utils.loadFromFile("META-INF/druid/parser/mysql/keywords", keywords);
        assertTrue(keywords.contains("select"));
    }

    @Test
    void loadsConfiguredConnectionValidationStrategy() throws Exception {
        try (DruidDataSource dataSource = TestDataSources.create("utils_strategy")) {
            dataSource.setValidConnectionCheckerClassName("com.alibaba.druid.pool.JDBC4ValidConnectionChecker");
            try (Connection connection = dataSource.getConnection()) {
                assertTrue(dataSource.getValidConnectionChecker().isValidConnection(connection, "SELECT 1", 10));
            }
        }
    }
}
