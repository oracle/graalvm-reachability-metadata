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
import com.alibaba.druid.support.monitor.dao.MonitorDaoJdbcImpl.BeanInfo;
import com.alibaba.druid.util.JdbcUtils;
import com.alibaba.druid.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class MonitorDaoJdbcImplTest {
    @Test
    void persistsPoolAndSqlSnapshotsWithHashedSqlText() throws Exception {
        try (DruidDataSource store = TestDataSources.create("monitor_store")) {
            store.setUrl(store.getUrl() + ";MODE=MySQL;NON_KEYWORDS=VALUE");
            MonitorDaoJdbcImpl dao = new MonitorDaoJdbcImpl();
            dao.setDataSource(store);
            for (String resource : List.of("datasource.sql", "sql.sql", "const.sql")) {
                String ddl = Utils.readFromResource("/support/monitor/mysql/" + resource);
                for (String sql : ddl.split(";")) {
                    if (!sql.isBlank()) {
                        JdbcUtils.execute(store, sql);
                    }
                }
            }
            JdbcUtils.execute(store, "ALTER TABLE druid_datasource ADD keepAliveCheckCount BIGINT");
            JdbcSqlStatValue sql = (JdbcSqlStatValue) dao.createInstance(new BeanInfo(JdbcSqlStatValue.class));
            sql.setSql("SELECT id FROM shipments");
            sql.setSqlHash(Utils.fnv_64(sql.getSql()));
            sql.setExecuteSuccessCount(3);
            sql.setFetchRowCount(6);
            DruidDataSourceStatValue pool = new DruidDataSourceStatValue();
            pool.setName("shipping");
            pool.setSqlList(List.of(sql));
            MonitorContext context = new MonitorContext();
            context.setPID(100);
            context.setCollectTime(new Date());
            dao.saveSql(context, List.of(pool));
            try (Connection connection = store.getConnection(); Statement statement = connection.createStatement()) {
                assertEquals(1, TestDataSources.queryInt(connection, "SELECT COUNT(*) FROM druid_datasource"));
                try (ResultSet rows = statement.executeQuery("SELECT execSuccessCount, fetchRowCount FROM druid_sql")) {
                    assertTrue(rows.next());
                    assertEquals(3, rows.getLong(1));
                    assertEquals(6, rows.getLong(2));
                }
                try (ResultSet rows = statement.executeQuery("SELECT value FROM druid_const WHERE type = 'sql'")) {
                    assertTrue(rows.next());
                    assertEquals("SELECT id FROM shipments", rows.getString(1));
                }
            }
            assertEquals(sql.getSql(), dao.cacheGet("sql", sql.getSqlHash()));
        }
    }
}
