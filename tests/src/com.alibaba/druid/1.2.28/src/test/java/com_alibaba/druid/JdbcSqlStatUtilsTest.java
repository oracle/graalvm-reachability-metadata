/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.stat.JdbcSqlStat;
import com.alibaba.druid.util.JdbcSqlStatUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JdbcSqlStatUtilsTest {
    @Test
    void readsStatisticsFromSubclass() {
        DerivedJdbcSqlStat stat = new DerivedJdbcSqlStat("select 1");
        stat.incrementExecuteSuccessCount();

        Map<String, Object> data = JdbcSqlStatUtils.getData(stat);
        assertNotNull(data);
        assertEquals("select 1", data.get("SQL"));
    }

    public static final class DerivedJdbcSqlStat extends JdbcSqlStat {
        DerivedJdbcSqlStat(String sql) {
            super(sql);
        }
    }
}
