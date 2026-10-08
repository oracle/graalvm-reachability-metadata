/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.change.ColumnConfig;
import liquibase.database.PreparedStatementFactory;
import liquibase.database.core.PostgresDatabase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.statement.InsertExecutablePreparedStatement;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutablePreparedStatementBaseTest {

    @Test
    void insertFallsBackToStandardJdbcExecutionWhenDriverHasNoPostgresExtension() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:prepared-statement-test")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE messages (message VARCHAR(64))");
            }

            PostgresDatabase database = new PostgresDatabase();
            database.setConnection(new JdbcConnection(connection));
            ColumnConfig message = new ColumnConfig().setName("message").setValue("hello from Liquibase");
            InsertExecutablePreparedStatement insert = new InsertExecutablePreparedStatement(
                    database,
                    null,
                    null,
                    "messages",
                    List.of(message),
                    null,
                    new ClassLoaderResourceAccessor()
            );

            insert.execute(new PreparedStatementFactory(new JdbcConnection(connection)));

            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT message FROM messages")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("hello from Liquibase");
                assertThat(result.next()).isFalse();
            }
        }
    }
}
