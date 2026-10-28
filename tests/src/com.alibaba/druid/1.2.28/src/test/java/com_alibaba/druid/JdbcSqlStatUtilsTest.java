/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.stat.JdbcSqlStat;
import com.alibaba.druid.stat.JdbcSqlStatValue;
import com.alibaba.druid.util.JdbcSqlStatUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(120)
public class JdbcSqlStatUtilsTest {
    @Test
    void exposesStatisticsFromLiveCounterAndSnapshot() {
        JdbcSqlStat stat = new JdbcSqlStat("SELECT id FROM inventory");
        stat.setId(71);
        stat.incrementExecuteSuccessCount();
        stat.addFetchRowCount(4);
        JdbcSqlStatValue snapshot = stat.getValue(false);
        for (Object value : new Object[]{stat, snapshot}) {
            Map<String, Object> data = JdbcSqlStatUtils.getData(value);
            assertNotNull(data);
            assertEquals("SELECT id FROM inventory", data.get("SQL"));
            assertEquals(1L, data.get("ExecuteCount"));
            assertEquals(4L, data.get("FetchRowCount"));
        }
    }
}
