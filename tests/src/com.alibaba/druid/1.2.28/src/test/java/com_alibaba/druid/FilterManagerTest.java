/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.FilterManager;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Timeout(120)
public class FilterManagerTest {
    @Test
    void resolvesAliasAndInstallsStatisticsFilter() throws Exception {
        List<Filter> filters = new ArrayList<>();
        FilterManager.loadFilter(filters, "stat");
        FilterManager.loadFilter(filters, "stat");
        assertEquals(1, filters.size());
        assertInstanceOf(StatFilter.class, filters.get(0));
        try (DruidDataSource dataSource = TestDataSources.create("filter_alias")) {
            dataSource.setProxyFilters(filters);
            try (Connection connection = dataSource.getConnection()) {
                assertEquals(13, TestDataSources.queryInt(connection, "SELECT 13"));
            }
            assertEquals(1, dataSource.getSqlStatMap().size());
        }
    }
}
