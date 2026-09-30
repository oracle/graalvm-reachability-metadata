/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_data.micronaut_data_tx_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@MicronautTest(startApplication = false)
@Property(name = "datasources.default.url", value = "jdbc:h2:mem:read_transactions;DB_CLOSE_DELAY=-1")
@Property(name = "datasources.default.driver-class-name", value = "org.h2.Driver")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Timeout(45)
public class Micronaut_data_tx_jdbcTest {

    @Test
    void test() throws Exception {
        System.out.println("This is just a placeholder, implement your test");
    }

    @Inject
    DataSource dataSource;

    @Inject
    TransactionOperations<Connection> transactionOperations;

    @BeforeEach
    void prepareTable() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("DROP TABLE IF EXISTS transaction_event");
            connection.createStatement().executeUpdate(
                    "CREATE TABLE transaction_event (id INT PRIMARY KEY, message VARCHAR(100))");
            connection.createStatement().executeUpdate(
                    "INSERT INTO transaction_event (id, message) VALUES (1, 'ready')");
        }
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

        try (Connection connection = dataSource.getConnection();
                var resultSet = connection.createStatement().executeQuery(
                        "SELECT message FROM transaction_event WHERE id = 1")) {
            resultSet.next();
            assertThat(resultSet.getString("message")).isEqualTo("written");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not verify transaction event", exception);
        }
    }
}
