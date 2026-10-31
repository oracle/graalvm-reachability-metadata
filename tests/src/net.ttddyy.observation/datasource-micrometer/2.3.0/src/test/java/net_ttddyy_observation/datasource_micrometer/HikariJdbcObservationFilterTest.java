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
import java.util.Set;
import java.util.UUID;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.observation.ObservationRegistry;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.observation.tracing.ConnectionContext;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import net.ttddyy.observation.tracing.HikariJdbcObservationFilter;
import net.ttddyy.observation.tracing.JdbcObservationDocumentation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class HikariJdbcObservationFilterTest {
    @Test
    void addsPoolAndDriverTagsToAnObservedPooledConnection() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        ObservationTestSupport.RecordingHandler recorder = new ObservationTestSupport.RecordingHandler();
        registry.observationConfig().observationHandler(recorder).observationFilter(new HikariJdbcObservationFilter());
        DataSourceObservationListener listener = new DataSourceObservationListener(registry);
        listener.setSupportedTypes(Set.of(JdbcObservationDocumentation.CONNECTION));
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setJdbcUrl("jdbc:h2:mem:pool_" + UUID.randomUUID());
            pool.setDriverClassName("org.h2.Driver");
            pool.setPoolName("inventory-pool");
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(30000);
            pool.setValidationTimeout(10000);
            pool.setInitializationFailTimeout(30000);
            try (ProxyDataSource dataSource = ObservationTestSupport.observe(pool, listener);
                    Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT 7")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(7);
            }
            assertThat(recorder.contexts(ConnectionContext.class)).hasSize(1);
            ConnectionContext context = recorder.contexts(ConnectionContext.class).get(0);
            assertThat(context.getLowCardinalityKeyValue("jdbc.datasource.pool").getValue()).isEqualTo("inventory-pool");
            assertThat(context.getLowCardinalityKeyValue("jdbc.datasource.driver").getValue()).isEqualTo("org.h2.Driver");
            assertThat(context.getLowCardinalityKeyValue("jdbc.datasource.name").getValue()).isEqualTo("inventory");
            assertThat(context.getDataSource()).isSameAs(pool);
        }
        assertThat(registry.getCurrentObservation()).isNull();
    }
}
