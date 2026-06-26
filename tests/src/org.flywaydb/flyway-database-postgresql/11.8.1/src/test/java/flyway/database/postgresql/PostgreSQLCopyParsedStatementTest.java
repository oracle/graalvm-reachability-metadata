/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway.database.postgresql;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Types;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.awaitility.Awaitility;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.flywaydb.core.internal.jdbc.Results;
import org.flywaydb.database.postgresql.PostgreSQLCopyParsedStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgreSQLCopyParsedStatementTest {

    private static final String USERNAME = "copy_user";

    private static final String PASSWORD = "copy_secret";

    private static final String DATABASE = "copy_test";

    private static Process process;

    private static int port;

    @BeforeAll
    static void beforeAll() throws IOException {
        port = findAvailablePort();
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", port + ":5432", "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USERNAME, "-e", "POSTGRES_PASSWORD=" + PASSWORD, "postgres:18-alpine")
                .redirectOutput(new File("postgres-copy-stdout.txt"))
                .redirectError(new File("postgres-copy-stderr.txt"))
                .start();

        Awaitility.await().atMost(Duration.ofSeconds(45)).ignoreExceptions().until(() -> {
            try (Connection connection = getDataSource().getConnection()) {
                return true;
            }
        });
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void executeCopyFromStdinStatement() throws Exception {
        try (Connection connection = getDataSource().getConnection()) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(connection, Types.NULL);
            jdbcTemplate.execute("DROP TABLE IF EXISTS copy_statement_target");
            jdbcTemplate.execute("CREATE TABLE copy_statement_target (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");

            PostgreSQLCopyParsedStatement statement = new PostgreSQLCopyParsedStatement(
                    0,
                    1,
                    1,
                    "COPY copy_statement_target (id, name) FROM STDIN",
                    "1\tAlice\n2\tBob\n");
            Results results = statement.execute(jdbcTemplate, null, null);

            assertThat(results.getErrors()).isEmpty();
            assertThat(results.getResults()).hasSize(1);
            assertThat(results.getResults().get(0).updateCount()).isEqualTo(2L);
            assertThat(jdbcTemplate.queryForStringList("SELECT name FROM copy_statement_target ORDER BY id"))
                    .containsExactly("Alice", "Bob");
        }
    }

    private static int findAvailablePort() throws IOException {
        int candidatePort;
        do {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                candidatePort = serverSocket.getLocalPort();
            }
        } while (candidatePort == 5432);
        return candidatePort;
    }

    private static DataSource getDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://localhost:" + port + "/" + DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        dataSource.setDatabaseName(DATABASE);
        return dataSource;
    }
}
