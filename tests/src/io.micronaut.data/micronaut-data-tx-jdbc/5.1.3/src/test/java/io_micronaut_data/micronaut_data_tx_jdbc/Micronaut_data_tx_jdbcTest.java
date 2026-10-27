/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_data.micronaut_data_tx_jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@MicronautTest(startApplication = false, transactional = false)
@Property(name = "datasources.default.url", value = "jdbc:h2:mem:read_transactions;DB_CLOSE_DELAY=-1")
@Property(name = "datasources.default.driver-class-name", value = "org.h2.Driver")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Timeout(45)
public class Micronaut_data_tx_jdbcTest {

    @Inject
    TransactionOperations<Connection> transactionOperations;

    @BeforeEach
    void prepareTable() {
        transactionOperations.executeWrite(status -> {
            try (var statement = status.getConnection().createStatement()) {
                statement.executeUpdate("DROP TABLE IF EXISTS transaction_event");
                statement.executeUpdate(
                        "CREATE TABLE transaction_event (id INT PRIMARY KEY, message VARCHAR(100))");
                statement.executeUpdate(
                        "INSERT INTO transaction_event (id, message) VALUES (1, 'ready')");
                return null;
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not prepare transaction events", exception);
            }
        });
    }

    @Test
    void readsRowsThroughReadTransactionConnection() {
        String message = transactionOperations.executeRead(status -> {
            try (var statement = status.getConnection().prepareStatement(
                    "SELECT message FROM transaction_event WHERE id = ?")) {
                statement.setInt(1, 1);
                try (var resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getString("message");
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not read transaction event", exception);
            }
        });

        assertThat(message).isEqualTo("ready");
    }

    @Test
    void commitsWriteTransactionUsingManagedConnection() {
        transactionOperations.executeWrite(status -> {
            try (var statement = status.getConnection().prepareStatement(
                    "UPDATE transaction_event SET message = ? WHERE id = ?")) {
                statement.setString(1, "written");
                statement.setInt(2, 1);
                assertThat(statement.executeUpdate()).isEqualTo(1);
                return null;
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not write transaction event", exception);
            }
        });

        assertThat(readMessage(1)).isEqualTo("written");
    }

    @Test
    void executesBatchWritesThroughManagedConnection() {
        int[] insertedRows = transactionOperations.executeWrite(status -> {
            try (var statement = status.getConnection().prepareStatement(
                    "INSERT INTO transaction_event (id, message) VALUES (?, ?)")) {
                statement.setInt(1, 3);
                statement.setString(2, "first batch event");
                statement.addBatch();
                statement.setInt(1, 4);
                statement.setString(2, "second batch event");
                statement.addBatch();
                return statement.executeBatch();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not insert transaction events", exception);
            }
        });

        assertThat(insertedRows).containsExactly(1, 1);
        transactionOperations.executeRead(status -> {
            try (var statement = status.getConnection().createStatement();
                    var resultSet = statement.executeQuery(
                            "SELECT id, message FROM transaction_event WHERE id IN (3, 4) ORDER BY id")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt("id")).isEqualTo(3);
                assertThat(resultSet.getString("message")).isEqualTo("first batch event");
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt("id")).isEqualTo(4);
                assertThat(resultSet.getString("message")).isEqualTo("second batch event");
                assertThat(resultSet.next()).isFalse();
                return null;
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not verify transaction events", exception);
            }
        });
    }

    @Test
    void rollsBackWriteTransactionWhenCallbackFails() {
        assertThatThrownBy(() -> transactionOperations.executeWrite(status -> {
            try (var statement = status.getConnection().prepareStatement(
                    "UPDATE transaction_event SET message = ? WHERE id = ?")) {
                statement.setString(1, "rolled back");
                statement.setInt(2, 1);
                assertThat(statement.executeUpdate()).isEqualTo(1);
                throw new IllegalStateException("abort transaction");
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not update transaction event", exception);
            }
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("abort transaction");

        assertThat(readMessage(1)).isEqualTo("ready");
    }

    @Test
    void returnsResultFromWriteTransaction() {
        int insertedRows = transactionOperations.executeWrite(status -> {
            try (var statement = status.getConnection().prepareStatement(
                    "INSERT INTO transaction_event (id, message) VALUES (?, ?)")) {
                statement.setInt(1, 2);
                statement.setString(2, "created");
                return statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not insert transaction event", exception);
            }
        });

        assertThat(insertedRows).isEqualTo(1);
        assertThat(readMessage(2)).isEqualTo("created");
    }

    private String readMessage(int id) {
        return transactionOperations.executeRead(status -> {
            try (var statement = status.getConnection().prepareStatement(
                    "SELECT message FROM transaction_event WHERE id = ?")) {
                statement.setInt(1, id);
                try (var resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    return resultSet.getString("message");
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not read transaction event", exception);
            }
        });
    }
}
