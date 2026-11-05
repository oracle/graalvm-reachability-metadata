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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import io.micrometer.observation.ObservationRegistry;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.observation.tracing.ConnectionContext;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import net.ttddyy.observation.tracing.JdbcObservationDocumentation;
import net.ttddyy.observation.tracing.QueryContext;
import net.ttddyy.observation.tracing.QueryObservationConvention;
import net.ttddyy.observation.tracing.ResultSetContext;
import net.ttddyy.observation.tracing.ResultSetOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(120)
public class DataSourceObservationListenerTest {
    @Test
    void observesPreparedQueriesGeneratedKeysAndTransactions() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        ObservationTestSupport.RecordingHandler recorder = new ObservationTestSupport.RecordingHandler();
        registry.observationConfig().observationHandler(recorder);
        DataSourceObservationListener listener = new DataSourceObservationListener(registry);
        listener.setIncludeParameterValues(true);
        String insertSql = "INSERT INTO items (name) VALUES (?)";

        try (ProxyDataSource dataSource = ObservationTestSupport.observe(ObservationTestSupport.database(), listener);
                Connection connection = dataSource.getConnection()) {
            assertThat(registry.getCurrentObservation()).isNull();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE items (
                            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            name VARCHAR(40)
                        )
                        """);
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "notebook");
                assertThat(insert.executeUpdate()).isEqualTo(1);
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertThat(keys.next()).isTrue();
                    assertThat(keys.getLong(1)).isEqualTo(1L);
                    assertThat(keys.next()).isFalse();
                }
            }
            connection.commit();
            try (PreparedStatement select = connection.prepareStatement("SELECT name FROM items WHERE id = ?")) {
                select.setLong(1, 1L);
                try (ResultSet rows = select.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("notebook");
                    assertThat(rows.next()).isFalse();
                }
            }
            connection.rollback();
        }

        assertThat(recorder.contexts(ConnectionContext.class)).hasSize(1);
        ConnectionContext connectionContext = recorder.contexts(ConnectionContext.class).get(0);
        assertThat(connectionContext.getDataSourceName()).isEqualTo("inventory");
        assertThat(connectionContext.getUrl()).startsWith("jdbc:h2:mem:observations_");
        assertThat(connectionContext.getLowCardinalityKeyValue("jdbc.datasource.name").getValue()).isEqualTo("inventory");
        assertThat(recorder.events).containsExactly("acquired", "commit", "rollback");
        assertThat(recorder.contexts(QueryContext.class)).hasSize(3);
        QueryContext insert = recorder.contexts(QueryContext.class).stream()
                .filter(context -> context.getQueries().contains(insertSql)).findFirst().orElseThrow();
        assertThat(insert.getParams()).containsExactly("(notebook)");
        assertThat(insert.getAffectedRowCount()).isEqualTo("1");
        assertThat(insert.getHighCardinalityKeyValue("jdbc.query[0]").getValue()).isEqualTo(insertSql);
        assertThat(insert.getHighCardinalityKeyValue("jdbc.params[0]").getValue()).isEqualTo("(notebook)");
        assertThat(insert.getHighCardinalityKeyValue("jdbc.row-affected").getValue()).isEqualTo("1");
        assertThat(insert.getParentObservation().getContextView()).isSameAs(connectionContext);
        assertThat(recorder.contexts(ResultSetContext.class)).hasSize(2).allSatisfy(context -> {
            assertThat(context.getCount()).isEqualTo(1);
            assertThat(context.getHighCardinalityKeyValue("jdbc.row-count").getValue()).isEqualTo("1");
            assertThat(context.getParentObservation().getContextView()).isSameAs(connectionContext);
            assertThat(context.getOperations()).anyMatch(ResultSetOperation::isDataRetrievalOperation);
        });
        ResultSetContext keys = recorder.contexts(ResultSetContext.class).stream()
                .filter(context -> context.getHighCardinalityKeyValue("jdbc.generated-keys") != null)
                .findFirst().orElseThrow();
        assertThat(keys.getHighCardinalityKeyValue("jdbc.generated-keys").getValue()).isEqualTo("1");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void observesBatchCountsWithoutExposingParametersByDefault() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        ObservationTestSupport.RecordingHandler recorder = new ObservationTestSupport.RecordingHandler();
        registry.observationConfig().observationHandler(recorder);
        DataSourceObservationListener listener = new DataSourceObservationListener(() -> registry);
        try (ProxyDataSource dataSource = ObservationTestSupport.observe(ObservationTestSupport.database(), listener);
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE messages (body VARCHAR(40))");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO messages VALUES (?)")) {
                insert.setString(1, "private-first");
                insert.addBatch();
                insert.setString(1, "private-second");
                insert.addBatch();
                assertThat(insert.executeBatch()).containsExactly(1, 1);
            }
        }
        QueryContext batch = recorder.contexts(QueryContext.class).stream()
                .filter(context -> context.getExecutionInfo().isBatch()).findFirst().orElseThrow();
        assertThat(batch.getAffectedRowCount()).isEqualTo("[1, 1]");
        assertThat(batch.getHighCardinalityKeyValue("jdbc.row-affected").getValue()).isEqualTo("[1, 1]");
        assertThat(batch.getParams()).isEmpty();
        assertThat(batch.getHighCardinalityKeyValue("jdbc.params[0]")).isNull();
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void recordsSqlErrorsAndRestoresTheObservationScope() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        ObservationTestSupport.RecordingHandler recorder = new ObservationTestSupport.RecordingHandler();
        registry.observationConfig().observationHandler(recorder);
        DataSourceObservationListener listener = new DataSourceObservationListener(registry);
        SQLException failure;
        try (ProxyDataSource dataSource = ObservationTestSupport.observe(ObservationTestSupport.database(), listener);
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            failure = assertThrows(SQLException.class, () -> statement.executeQuery("SELECT * FROM missing_items"));
            assertThat(registry.getCurrentObservation()).isNull();
            try (ResultSet rows = statement.executeQuery("SELECT 42")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(42);
            }
        }
        assertThat(recorder.contexts(QueryContext.class)).hasSize(2);
        assertThat(recorder.contexts(QueryContext.class).get(0).getError()).isSameAs(failure);
        assertThat(recorder.contexts(QueryContext.class).get(1).getError()).isNull();
        assertThat(recorder.contexts(ConnectionContext.class).get(0).getError()).isNull();
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void selectsObservationTypesAndDisablesResultSetOperationRecording() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        ObservationTestSupport.RecordingHandler recorder = new ObservationTestSupport.RecordingHandler();
        registry.observationConfig().observationHandler(recorder);
        DataSourceObservationListener listener = new DataSourceObservationListener(registry);
        listener.setSupportedTypes(Set.of(JdbcObservationDocumentation.QUERY, JdbcObservationDocumentation.RESULT_SET));
        listener.setIncludeResultSetOperations(false);
        listener.setQueryObservationConvention(new QueryObservationConvention() {
            @Override
            public String getName() {
                return "inventory.query";
            }
        });
        try (ProxyDataSource dataSource = ObservationTestSupport.observe(ObservationTestSupport.database(), listener);
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT 'pencil'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("pencil");
            assertThat(rows.next()).isFalse();
        }
        assertThat(recorder.contexts(ConnectionContext.class)).isEmpty();
        assertThat(recorder.contexts(QueryContext.class)).hasSize(1);
        assertThat(recorder.contexts(QueryContext.class).get(0).getName()).isEqualTo("inventory.query");
        assertThat(recorder.contexts(ResultSetContext.class)).hasSize(1);
        ResultSetContext resultSet = recorder.contexts(ResultSetContext.class).get(0);
        assertThat(resultSet.getCount()).isEqualTo(1);
        assertThat(resultSet.getOperations()).isEmpty();
        assertThat(registry.getCurrentObservation()).isNull();
    }
}
