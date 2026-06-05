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
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
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

    private static final String SCHEMA = "copy_statement_test";

    private static int postgresPort;

    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        postgresPort = findAvailablePort();
        System.out.println("Starting PostgreSQL for COPY statement test ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", postgresPort + ":5432", "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USERNAME, "-e", "POSTGRES_PASSWORD=" + PASSWORD, "postgres:18-alpine")
                .redirectOutput(new File("postgres-copy-stdout.txt"))
                .redirectError(new File("postgres-copy-stderr.txt"))
                .start();

        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            getDataSource().getConnection().close();
            return true;
        });
        System.out.println("PostgreSQL for COPY statement test started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down PostgreSQL for COPY statement test");
            process.destroy();
        }
    }

    @Test
    void migratesCopyFromStdinStatement() throws Exception {
        DataSource dataSource = getDataSource();
        Configuration configuration = new FluentConfiguration()
                .dataSource(dataSource)
                .encoding(StandardCharsets.UTF_8)
                .schemas(SCHEMA)
                .resourceProvider(new CopyStatementResourceProvider());
        configuration.getPluginRegister()
                .getPlugin(PostgreSQLConfigurationExtension.class)
                .setTransactionalLock(false);

        Flyway flyway = new Flyway(configuration);
        MigrateResult migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(1);
        assertThat(copiedNames(dataSource)).isEqualTo("Ada,Grace");
    }

    private static String copiedNames(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT string_agg(name, ',' ORDER BY id) FROM " + SCHEMA + ".copied_people")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static DataSource getDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://localhost:" + postgresPort + "/" + DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        dataSource.setDatabaseName(DATABASE);
        return dataSource;
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class CopyStatementResourceProvider implements ResourceProvider {

        private static final String MIGRATION = "db/copy/V1__copy_from_stdin.sql";

        private static final LoadableResource RESOURCE = new StringLoadableResource(MIGRATION, """
                CREATE TABLE copy_statement_test.copied_people
                (
                    id   INT PRIMARY KEY,
                    name VARCHAR NOT NULL
                );
                COPY copy_statement_test.copied_people (id, name) FROM STDIN;
                1\tAda
                2\tGrace
                \\.
                """);

        @Override
        public LoadableResource getResource(String name) {
            if (MIGRATION.equals(name)) {
                return RESOURCE;
            }
            return null;
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            return Collections.singletonList(RESOURCE);
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
            return path.substring(path.lastIndexOf('/') + 1);
        }

        @Override
        public String getRelativePath() {
            return path;
        }
    }
}
