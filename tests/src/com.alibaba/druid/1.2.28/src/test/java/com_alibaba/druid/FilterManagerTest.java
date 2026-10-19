/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterManagerTest {
    @Test
    void resolvesFilterAliasesAndCollectsSqlStatistics() throws Exception {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:druid_filters;DB_CLOSE_DELAY=-1");
        dataSource.setFilters("stat,mergeStat,wall,encoding,slf4j," + FilterAdapter.class.getName());

        try {
            dataSource.init();
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("create table filter_item (id integer primary key)");
                statement.executeUpdate("insert into filter_item values (1)");
                assertTrue(statement.executeQuery("select id from filter_item").next());
            }

            List<Filter> filters = dataSource.getProxyFilters();
            assertTrue(filters.stream().anyMatch(StatFilter.class::isInstance));
            assertTrue(filters.stream().anyMatch(FilterAdapter.class::isInstance));
            assertTrue(filters.size() >= 6);
            assertFalse(dataSource.getDataSourceStat().getSqlStatMap().isEmpty());
        } finally {
            dataSource.close();
        }
    }
}
