/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.stat.JdbcSqlStatValue;
import com.alibaba.druid.support.monitor.dao.MonitorDaoJdbcImpl;
import com.alibaba.druid.support.monitor.dao.MonitorDaoJdbcImpl.BeanInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class MonitorDaoJdbcImplInnerBeanInfoTest {
    @Test
    void derivesInsertColumnsAndHashMappingFromSnapshotAnnotations() {
        BeanInfo info = new BeanInfo(JdbcSqlStatValue.class);
        MonitorDaoJdbcImpl dao = new MonitorDaoJdbcImpl();
        String insert = dao.buildInsertSql(info);
        assertEquals("druid_sql", info.getTableName());
        assertTrue(insert.startsWith("INSERT INTO druid_sql"));
        assertTrue(insert.contains("sqlHash"));
        assertTrue(insert.contains("execSuccessCount"));
        assertTrue(insert.contains("fetchRowCount"));
        assertEquals(1, info.getHashFields().size());
        assertEquals("sql", info.getHashFields().get(0).getHashForType());
        assertEquals("sqlHash", info.getGroupByFields().get(0).getColumnName());
        assertEquals(info.getFields().size() + 6, insert.chars().filter(character -> character == '?').count());
    }
}
