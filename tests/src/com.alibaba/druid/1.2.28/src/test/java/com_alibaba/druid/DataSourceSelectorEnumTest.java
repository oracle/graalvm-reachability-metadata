/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.ha.HighAvailableDataSource;
import com.alibaba.druid.pool.ha.selector.DataSourceSelector;
import com.alibaba.druid.pool.ha.selector.DataSourceSelectorEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@Timeout(120)
public class DataSourceSelectorEnumTest {
    @Test
    void constructsEachSelectorAndRoutesToConfiguredPool() throws Exception {
        try (DruidDataSource pool = TestDataSources.create("ha_selected")) {
            HighAvailableDataSource available = new HighAvailableDataSource();
            available.setDataSourceMap(Map.of("default", pool));
            available.setTestOnBorrow(true);
            try {
                for (DataSourceSelectorEnum choice : DataSourceSelectorEnum.values()) {
                    DataSourceSelector selector = choice.newInstance(available);
                    assertNotNull(selector);
                    selector.init();
                    try {
                        assertEquals(choice.getName(), selector.getName());
                        assertSame(pool, selector.get());
                        try (Connection connection = selector.get().getConnection()) {
                            assertEquals(59, TestDataSources.queryInt(connection, "SELECT 59"));
                        }
                    } finally {
                        selector.destroy();
                    }
                }
            } finally {
                available.close();
            }
        }
    }
}
