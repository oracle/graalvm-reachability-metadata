/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy_observation.datasource_micrometer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.observation.tracing.ConnectionTracingObservationHandler;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import net.ttddyy.observation.tracing.QueryTracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class QueryTracingObservationHandlerTest {
    @Test
    void tracesPreparedQueryAsAChildOfTheConnectionSpan() throws Exception {
        SimpleTracer tracer = new SimpleTracer();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new ConnectionTracingObservationHandler(tracer))
                .observationHandler(new QueryTracingObservationHandler(tracer));
        DataSourceObservationListener listener = new DataSourceObservationListener(registry);
        listener.setIncludeParameterValues(true);
        String sql = "SELECT CAST(? AS VARCHAR)";
        try (ProxyDataSource dataSource = ObservationTestSupport.observe(ObservationTestSupport.database(), listener);
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "paper");
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("paper");
            }
        }
        assertThat(tracer.getSpans()).hasSize(2);
        SimpleSpan query = tracer.getSpans().stream().filter(span -> "query".equals(span.getName())).findFirst().orElseThrow();
        SimpleSpan connection = tracer.getSpans().stream().filter(span -> "connection".equals(span.getName())).findFirst().orElseThrow();
        assertThat(query.getKind()).isEqualTo(Span.Kind.CLIENT);
        assertThat(query.getParentId()).isEqualTo(connection.getSpanId());
        assertThat(query.getTraceId()).isEqualTo(connection.getTraceId());
        assertThat(query.getTags()).containsEntry("jdbc.query[0]", sql)
                .containsEntry("jdbc.params[0]", "(paper)")
                .containsEntry("jdbc.datasource.name", "inventory");
        assertThat(query.getRemoteServiceName()).isEqualTo("inventory");
        assertThat(query.getEndTimestamp()).isNotNull();
        assertThat(query.getError()).isNull();
        assertThat(tracer.currentSpan()).isNull();
    }
}
