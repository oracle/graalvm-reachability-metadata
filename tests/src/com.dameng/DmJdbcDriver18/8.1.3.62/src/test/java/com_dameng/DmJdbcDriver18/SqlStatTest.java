/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.filter.stat.SqlStat;
import dm.jdbc.filter.stat.SqlStatValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlStatTest {
    @Test
    void exportsStatisticsFromSnapshotThroughCompatibilityApi() throws Exception {
        SqlStat stat = new SqlStat("select name from inventory");
        stat.setDataSource("warehouse");
        stat.incrementExecuteSuccessCount();
        stat.addFetchRowCount(3);
        SqlStatValue snapshot = stat.getValue(false);

        Map<String, Object> data = SqlStat.getData(snapshot);
        assertEquals("select name from inventory", data.get("SQL"));
        assertEquals("warehouse", data.get("DataSource"));
        assertEquals(1L, data.get("ExecuteCount"));
        assertEquals(3L, data.get("FetchRowCount"));
        assertEquals(snapshot.getData().keySet(), data.keySet());
    }
}
