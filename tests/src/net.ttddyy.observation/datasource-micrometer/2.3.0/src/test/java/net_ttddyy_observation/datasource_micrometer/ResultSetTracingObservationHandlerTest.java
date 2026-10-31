/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy_observation.datasource_micrometer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import net.ttddyy.observation.tracing.ResultSetTracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ResultSetTracingObservationHandlerTest {
    @Test
    void tagsTheNumberOfConsumedRowsAndEndsTheSpanOnClose() throws Exception {
        SimpleTracer tracer = new SimpleTracer();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ResultSetTracingObservationHandler(tracer));
        DataSourceObservationListener listener = new DataSourceObservationListener(registry);
        try (ProxyDataSource dataSource = ObservationTestSupport.observe(ObservationTestSupport.database(), listener);
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT X FROM SYSTEM_RANGE(1, 3)")) {
            int sum = 0;
            while (rows.next()) {
                sum += rows.getInt(1);
            }
            assertThat(sum).isEqualTo(6);
            assertThat(tracer.currentSpan()).isNotNull();
        }
        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("result-set");
        assertThat(span.getKind()).isEqualTo(Span.Kind.CLIENT);
        assertThat(span.getTags()).containsEntry("jdbc.row-count", "3")
                .containsEntry("jdbc.datasource.name", "inventory");
        assertThat(span.getRemoteServiceName()).isEqualTo("inventory");
        assertThat(span.getEndTimestamp()).isNotNull();
        assertThat(span.getError()).isNull();
        assertThat(tracer.currentSpan()).isNull();
        assertThat(registry.getCurrentObservation()).isNull();
    }
}
