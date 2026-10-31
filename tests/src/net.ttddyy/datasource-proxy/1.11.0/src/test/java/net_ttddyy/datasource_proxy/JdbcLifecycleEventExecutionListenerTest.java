/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.lifecycle.JdbcLifecycleEventListenerAdapter;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcLifecycleEventExecutionListenerTest {
    @Test
    void ordersGenericSpecificAndQueryCallbacksAroundExecution() throws Exception {
        List<String> events = new ArrayList<>();
        JdbcLifecycleEventListenerAdapter listener = new JdbcLifecycleEventListenerAdapter() {
            @Override
            public void beforeMethod(MethodExecutionContext context) {
                if ("executeQuery".equals(context.getMethod().getName())) {
                    events.add("beforeMethod");
                }
            }

            @Override
            public void beforeExecuteQuery(MethodExecutionContext context) {
                events.add("beforeExecuteQuery");
            }

            @Override
            public void beforeQuery(ExecutionInfo info, List<QueryInfo> queries) {
                events.add("beforeQuery");
                assertEquals("SELECT 6 * 7", queries.get(0).getQuery());
            }

            @Override
            public void afterQuery(ExecutionInfo info, List<QueryInfo> queries) {
                events.add("afterQuery");
                assertTrue(info.isSuccess());
                assertEquals("lifecycle-query", info.getDataSourceName());
            }

            @Override
            public void afterExecuteQuery(MethodExecutionContext context) {
                events.add("afterExecuteQuery");
                assertNull(context.getThrown());
                assertInstanceOf(ResultSet.class, context.getResult());
            }

            @Override
            public void afterMethod(MethodExecutionContext context) {
                if ("executeQuery".equals(context.getMethod().getName())) {
                    events.add("afterMethod");
                }
            }
        };
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("lifecycleExecution"))
                .name("lifecycle-query").listener(listener).build();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT 6 * 7")) {
            assertTrue(rows.next());
            assertEquals(42, rows.getInt(1));
        }
        assertEquals(List.of("beforeMethod", "beforeExecuteQuery", "beforeQuery", "afterQuery",
                "afterExecuteQuery", "afterMethod"), events);
    }
}
