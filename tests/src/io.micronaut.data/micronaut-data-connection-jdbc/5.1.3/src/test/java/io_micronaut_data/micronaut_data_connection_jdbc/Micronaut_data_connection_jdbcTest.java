/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_data.micronaut_data_connection_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@MicronautTest(startApplication = false)
@Property(name = "datasources.default.url", value = "jdbc:h2:mem:connection_operations;DB_CLOSE_DELAY=-1")
@Property(name = "datasources.default.driver-class-name", value = "org.h2.Driver")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Timeout(45)
public class Micronaut_data_connection_jdbcTest {

    @Inject
    ConnectionOperations<Connection> connectionOperations;

    @Inject
    DefaultDataSourceConnectionOperations dataSourceConnectionOperations;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void prepareTable() {
        connectionOperations.executeWrite(status -> {
            try (var statement = status.getConnection().createStatement()) {
                statement.executeUpdate("DROP TABLE IF EXISTS connection_event");
                statement.executeUpdate(
                        "CREATE TABLE connection_event (id INT PRIMARY KEY, message VARCHAR(100))");
                statement.executeUpdate(
                        "INSERT INTO connection_event (id, message) VALUES (1, 'ready')");
                return null;
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not prepare connection events", exception);
            }
        });
    }

    @Test
    void executesReadAgainstManagedJdbcConnection() {
        String message = connectionOperations.executeRead(status -> readMessage(status, 1));

        assertThat(message).isEqualTo("ready");
    }

    @Test
    void executesWithNamedConnectionDefinitionAndCommitsChanges() {
        ConnectionDefinition definition = ConnectionDefinition.named("connection-write");

        int updatedRows = connectionOperations.execute(definition, status -> {
            try (var statement = status.getConnection().prepareStatement(
                    "UPDATE connection_event SET message = ? WHERE id = ?")) {
                statement.setString(1, "written");
                statement.setInt(2, 1);
                return statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not update connection event", exception);
            }
        });

        assertThat(updatedRows).isEqualTo(1);
        String message = connectionOperations.executeRead(status -> readMessage(status, 1));
        assertThat(message).isEqualTo("written");
    }

    @Test
    void executesThroughDataSourceConnectionOperations() {
        String message = dataSourceConnectionOperations.executeRead(status -> readMessage(status, 1));

        assertThat(message).isEqualTo("ready");
        assertThat(dataSourceConnectionOperations.findConnectionStatus()).isEmpty();
    }

    @Test
    void executesReadThroughContextualDataSource() {
        String message = connectionOperations.executeRead(status -> {
            try (Connection connection = dataSource.getConnection()) {
                try (var statement = connection.prepareStatement(
                        "SELECT message FROM connection_event WHERE id = ?")) {
                    statement.setInt(1, 1);
                    try (var resultSet = statement.executeQuery()) {
                        assertThat(resultSet.next()).isTrue();
                        return resultSet.getString("message");
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not read through the data source", exception);
            }
        });

        assertThat(message).isEqualTo("ready");
    }

    @Test
    void reusesConnectionForNestedRequiredOperation() {
        connectionOperations.executeWrite(outer -> {
            Connection outerConnection = outer.getConnection();
            String message = connectionOperations.executeRead(inner -> {
                assertThat(inner.getConnection()).isSameAs(outerConnection);
                assertThat(inner.isNew()).isFalse();
                return readMessage(inner, 1);
            });

            assertThat(message).isEqualTo("ready");
            return null;
        });
    }

    @Test
    void runsConnectionSynchronizationDuringConnectionLifecycle() {
        List<String> events = new ArrayList<>();

        connectionOperations.executeWrite(status -> {
            status.registerSynchronization(new ConnectionSynchronization() {
                @Override
                public void executionComplete() {
                    events.add("execution-complete");
                }

                @Override
                public void beforeClosed() {
                    events.add("before-closed");
                }

                @Override
                public void afterClosed() {
                    events.add("after-closed");
                }
            });
            return readMessage(status, 1);
        });

        assertThat(events).containsExactly("execution-complete", "before-closed", "after-closed");
    }

    @Test
    void opensSeparateConnectionForRequiresNewOperation() {
        connectionOperations.executeWrite(outer -> {
            Connection outerConnection = outer.getConnection();
            String message = connectionOperations.execute(
                    ConnectionDefinition.REQUIRES_NEW,
                    inner -> {
                        assertThat(inner.getConnection()).isNotSameAs(outerConnection);
                        assertThat(inner.isNew()).isTrue();
                        return readMessage(inner, 1);
                    });

            assertThat(message).isEqualTo("ready");
            return null;
        });
    }

    private String readMessage(ConnectionStatus<Connection> status, int id) {
        try (var statement = status.getConnection().prepareStatement(
                "SELECT message FROM connection_event WHERE id = ?")) {
            statement.setInt(1, id);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("message");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read connection event", exception);
        }
    }
}
