/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.lifecycle.JdbcLifecycleEventListenerAdapter;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcLifecycleEventListenerUtilsTest {
    @Test
    void routesJdbcMethodNamesToTheirLifecycleCallbacks() throws Exception {
        List<String> events = new ArrayList<>();
        JdbcLifecycleEventListenerAdapter listener = new JdbcLifecycleEventListenerAdapter() {
            @Override
            public void beforePrepareStatement(MethodExecutionContext context) {
                events.add("beforePrepare");
                assertEquals("SELECT CAST(? AS INTEGER)", context.getMethodArgs()[0]);
            }

            @Override
            public void afterPrepareStatement(MethodExecutionContext context) {
                events.add("afterPrepare");
                assertInstanceOf(PreparedStatement.class, context.getResult());
            }

            @Override
            public void beforeSetInt(MethodExecutionContext context) {
                events.add("beforeSetInt");
                assertEquals(42, context.getMethodArgs()[1]);
            }

            @Override
            public void afterExecuteQuery(MethodExecutionContext context) {
                events.add("afterExecuteQuery");
                assertInstanceOf(ResultSet.class, context.getResult());
            }

            @Override
            public void beforeNext(MethodExecutionContext context) {
                events.add("beforeNext");
            }
        };
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("lifecycleMapping"))
                .listener(listener).proxyResultSet().build();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT CAST(? AS INTEGER)")) {
            query.setInt(1, 42);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(42, rows.getInt(1));
            }
        }
        assertEquals(List.of("beforePrepare", "afterPrepare", "beforeSetInt", "afterExecuteQuery", "beforeNext"), events);
    }
}
