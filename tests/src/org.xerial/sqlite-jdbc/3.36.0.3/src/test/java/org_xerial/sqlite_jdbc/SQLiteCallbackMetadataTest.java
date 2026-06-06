/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial.sqlite_jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.sqlite.Collation;
import org.sqlite.Function;
import org.sqlite.ProgressHandler;
import org.sqlite.SQLiteDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class SQLiteCallbackMetadataTest {
    @Test
    public void customScalarAndAggregateFunctionsExecuteThroughNativeCallbacks() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite::memory:");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            Function.create(connection, "echo_with_arg_count", new Function() {
                @Override
                protected void xFunc() throws SQLException {
                    result(value_text(0) + ":" + args());
                }
            });
            Function.create(connection, "sum_window", new Function.Aggregate() {
                private int total;

                @Override
                protected void xStep() throws SQLException {
                    total += value_int(0);
                }

                @Override
                protected void xFinal() throws SQLException {
                    result(total);
                }
            });

            try (ResultSet scalarResultSet = statement.executeQuery("SELECT echo_with_arg_count('sqlite')")) {
                assertThat(scalarResultSet.next()).isTrue();
                assertThat(scalarResultSet.getString(1)).isEqualTo("sqlite:1");
                assertThat(scalarResultSet.next()).isFalse();
            }

            statement.executeUpdate("CREATE TABLE callback_values (value INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO callback_values(value) VALUES (3), (7), (11)");

            try (ResultSet aggregateResultSet = statement.executeQuery("SELECT sum_window(value) FROM callback_values")) {
                assertThat(aggregateResultSet.next()).isTrue();
                assertThat(aggregateResultSet.getInt(1)).isEqualTo(21);
                assertThat(aggregateResultSet.next()).isFalse();
            }
        }
    }

    @Test
    public void customWindowFunctionAndCollationExecuteThroughNativeCallbacks() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite::memory:");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            Function.create(connection, "sliding_sum", new Function.Window() {
                private int total;

                @Override
                protected void xStep() throws SQLException {
                    total += value_int(0);
                }

                @Override
                protected void xInverse() throws SQLException {
                    total -= value_int(0);
                }

                @Override
                protected void xValue() throws SQLException {
                    result(total);
                }

                @Override
                protected void xFinal() throws SQLException {
                    result(total);
                }
            });
            Collation.create(connection, "REVERSE_TEXT", new Collation() {
                @Override
                protected int xCompare(String first, String second) {
                    return second.compareTo(first);
                }
            });

            statement.executeUpdate("CREATE TABLE window_values (id INTEGER PRIMARY KEY, label TEXT NOT NULL, value INTEGER NOT NULL)");
            statement.executeUpdate("""
                    INSERT INTO window_values(id, label, value)
                    VALUES (1, 'alpha', 2), (2, 'charlie', 5), (3, 'bravo', 7)
                    """);

            try (ResultSet windowResultSet = statement.executeQuery("""
                    SELECT sliding_sum(value) OVER (
                        ORDER BY id
                        ROWS BETWEEN 1 PRECEDING AND CURRENT ROW
                    )
                    FROM window_values
                    ORDER BY id
                    """)) {
                assertThat(windowResultSet.next()).isTrue();
                assertThat(windowResultSet.getInt(1)).isEqualTo(2);
                assertThat(windowResultSet.next()).isTrue();
                assertThat(windowResultSet.getInt(1)).isEqualTo(7);
                assertThat(windowResultSet.next()).isTrue();
                assertThat(windowResultSet.getInt(1)).isEqualTo(12);
                assertThat(windowResultSet.next()).isFalse();
            }

            try (ResultSet collationResultSet = statement.executeQuery("""
                    SELECT label
                    FROM window_values
                    ORDER BY label COLLATE REVERSE_TEXT
                    """)) {
                assertThat(collationResultSet.next()).isTrue();
                assertThat(collationResultSet.getString(1)).isEqualTo("charlie");
                assertThat(collationResultSet.next()).isTrue();
                assertThat(collationResultSet.getString(1)).isEqualTo("bravo");
                assertThat(collationResultSet.next()).isTrue();
                assertThat(collationResultSet.getString(1)).isEqualTo("alpha");
                assertThat(collationResultSet.next()).isFalse();
            }
        }
    }

    @Test
    public void progressHandlerExecutesThroughNativeCallback() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite::memory:");
        AtomicInteger progressInvocations = new AtomicInteger();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            ProgressHandler.setHandler(connection, 1, new ProgressHandler() {
                @Override
                protected int progress() {
                    progressInvocations.incrementAndGet();
                    return 0;
                }
            });

            try (ResultSet resultSet = statement.executeQuery("""
                    WITH RECURSIVE counter(value) AS (
                        VALUES (1)
                        UNION ALL
                        SELECT value + 1 FROM counter WHERE value < 200
                    )
                    SELECT SUM(value) FROM counter
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(20100);
                assertThat(resultSet.next()).isFalse();
            } finally {
                ProgressHandler.clearHandler(connection);
            }
        }

        assertThat(progressInvocations.get()).isGreaterThan(0);
    }
}
