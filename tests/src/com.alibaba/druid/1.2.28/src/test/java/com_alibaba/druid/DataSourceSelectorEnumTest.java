/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.ha.HighAvailableDataSource;
import com.alibaba.druid.pool.ha.selector.DataSourceSelectorEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DataSourceSelectorEnumTest {
    @Test
    void createsEveryBuiltInSelector() {
        HighAvailableDataSource dataSource = new HighAvailableDataSource();
        try {
            for (DataSourceSelectorEnum selector : DataSourceSelectorEnum.values()) {
                assertNotNull(selector.newInstance(dataSource));
            }
        } finally {
            dataSource.close();
        }
    }
}
