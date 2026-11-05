/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy_observation.datasource_micrometer;

import java.sql.Connection;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.observation.tracing.ConnectionTracingObservationHandler;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ConnectionTracingObservationHandlerTest {
    @Test
    void finishesAClientSpanWithTheDatasourceNameAndTransactionEvents() throws Exception {
        SimpleTracer tracer = new SimpleTracer();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ConnectionTracingObservationHandler(tracer));
        DataSourceObservationListener listener = new DataSourceObservationListener(registry);
        try (ProxyDataSource dataSource = ObservationTestSupport.observe(ObservationTestSupport.database(), listener);
                Connection connection = dataSource.getConnection()) {
            assertThat(tracer.currentSpan()).isNull();
            connection.setAutoCommit(false);
            connection.commit();
            connection.rollback();
        }
        SimpleSpan span = tracer.onlySpan();
        assertThat(span.getName()).isEqualTo("connection");
        assertThat(span.getKind()).isEqualTo(Span.Kind.CLIENT);
        assertThat(span.getRemoteServiceName()).isEqualTo("inventory");
        assertThat(span.getTags()).containsEntry("jdbc.datasource.name", "inventory");
        assertThat(span.getEvents()).extracting(event -> event.getValue()).containsExactly("acquired", "commit", "rollback");
        assertThat(span.getEndTimestamp()).isNotNull();
        assertThat(span.getError()).isNull();
        assertThat(tracer.currentSpan()).isNull();
    }
}
