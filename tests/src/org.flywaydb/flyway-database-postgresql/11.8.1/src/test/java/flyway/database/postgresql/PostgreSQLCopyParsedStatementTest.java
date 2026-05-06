/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway.database.postgresql;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.awaitility.Awaitility;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgreSQLCopyParsedStatementTest {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/" + DATABASE;

    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting PostgreSQL for COPY migration ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "5433:5432", "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USERNAME, "-e", "POSTGRES_PASSWORD=" + PASSWORD, "postgres:18-alpine")
                .redirectOutput(new File("postgres-copy-stdout.txt"))
                .redirectError(new File("postgres-copy-stderr.txt"))
                .start();

        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            getDataSource().getConnection().close();
            return true;
        });
        System.out.println("PostgreSQL for COPY migration started");
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down PostgreSQL for COPY migration");
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void migratesCopyFromStdinData() throws Exception {
        DataSource dataSource = getDataSource();
        String schema = "copy_statement_test_" + Long.toUnsignedString(System.nanoTime(), 36);

        Configuration configuration = new FluentConfiguration()
                .dataSource(dataSource)
                .encoding(StandardCharsets.UTF_8)
                .schemas(schema)
                .defaultSchema(schema)
                .resourceProvider(new CopyMigrationResourceProvider());
        configuration.getPluginRegister()
                .getPlugin(PostgreSQLConfigurationExtension.class)
                .setTransactionalLock(false);

        Flyway flyway = new Flyway(configuration);
        MigrateResult migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(1);
        assertCopiedRows(dataSource, schema);
    }

    private static void assertCopiedRows(DataSource dataSource, String schema) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT id, title FROM " + schema + ".copy_statement_test ORDER BY id")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt("id")).isEqualTo(1);
            assertThat(resultSet.getString("title")).isEqualTo("alpha");
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt("id")).isEqualTo(2);
            assertThat(resultSet.getString("title")).isEqualTo("beta");
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static DataSource getDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(JDBC_URL);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        dataSource.setDatabaseName(DATABASE);
        return dataSource;
    }

    private static final class CopyMigrationResourceProvider implements ResourceProvider {
        private static final LoadableResource MIGRATION = new StringLoadableResource(
                "db/migration/V1__copy_from_stdin.sql",
                """
                        CREATE TABLE copy_statement_test
                        (
                            id INT PRIMARY KEY,
                            title VARCHAR NOT NULL
                        );

                        COPY copy_statement_test (id, title) FROM STDIN;
                        1	alpha
                        2	beta
                        \\.
                        """);

        @Override
        public LoadableResource getResource(String name) {
            if (MIGRATION.getRelativePath().equals(name)) {
                return MIGRATION;
            }
            return null;
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            return List.of(MIGRATION);
        }
    }

    private static final class StringLoadableResource extends LoadableResource {
        private final String path;

        private final String contents;

        private StringLoadableResource(String path, String contents) {
            this.path = path;
            this.contents = contents;
        }

        @Override
        public Reader read() {
            return new StringReader(contents);
        }

        @Override
        public String getAbsolutePath() {
            return path;
        }

        @Override
        public String getAbsolutePathOnDisk() {
            return path;
        }

        @Override
        public String getFilename() {
            int lastSlash = path.lastIndexOf('/');
            return path.substring(lastSlash + 1);
        }

        @Override
        public String getRelativePath() {
            return path;
        }
    }
}
