/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceStatValue;
import com.alibaba.druid.stat.JdbcSqlStatValue;
import com.alibaba.druid.support.monitor.MonitorContext;
import com.alibaba.druid.support.monitor.dao.MonitorDaoJdbcImpl;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class MonitorDaoJdbcImplTest {
    @Test
    void createsMonitoredValueFromBeanDescription() {
        MonitorDaoJdbcImpl dao = new MonitorDaoJdbcImpl();
        MonitorDaoJdbcImpl.BeanInfo beanInfo = new MonitorDaoJdbcImpl.BeanInfo(JdbcSqlStatValue.class);
        assertInstanceOf(JdbcSqlStatValue.class, dao.createInstance(beanInfo));
    }

    @Test
    void persistsAndLoadsSqlStatistics() {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:druid_monitor;MODE=MySQL;DB_CLOSE_DELAY=-1");

        try {
            MonitorDaoJdbcImpl dao = new MonitorDaoJdbcImpl();
            dao.setDataSource(dataSource);
            dao.createTables("mysql");

            long collectedAt = System.currentTimeMillis();
            MonitorContext context = new MonitorContext();
            context.setCollectTime(new Date(collectedAt));

            JdbcSqlStatValue sqlStat = new JdbcSqlStatValue();
            sqlStat.setSqlHash(42L);
            sqlStat.setSql("select 42");
            sqlStat.setDbType("h2");

            DruidDataSourceStatValue dataSourceStat = new DruidDataSourceStatValue();
            dataSourceStat.setName("monitor-pool");
            dataSourceStat.setDbType("h2");
            dataSourceStat.setSqlList(List.of(sqlStat));
            dao.saveSql(context, List.of(dataSourceStat));

            Map<String, Object> filters = Map.of(
                    "startTime", collectedAt - 1000L,
                    "endTime", collectedAt + 1000L);
            List<JdbcSqlStatValue> loaded = dao.loadSqlList(filters);

            assertEquals(1, loaded.size());
            assertEquals("select 42", loaded.get(0).getSql());
            assertEquals(42L, loaded.get(0).getSqlHash());
        } finally {
            dataSource.close();
        }
    }
}
