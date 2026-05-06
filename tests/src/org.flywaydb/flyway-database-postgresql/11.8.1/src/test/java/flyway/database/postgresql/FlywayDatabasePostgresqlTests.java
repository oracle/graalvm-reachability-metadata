/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway.database.postgresql;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.sql.DataSource;

import org.awaitility.Awaitility;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class FlywayDatabasePostgresqlTests {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static final String JDBC_URL = "jdbc:postgresql://localhost/" + DATABASE;

    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting PostgreSQL ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "5432:5432", "-e", "POSTGRES_DB=" + DATABASE, "-e", "POSTGRES_USER=" + USERNAME,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD, "postgres:18-alpine").redirectOutput(new File("postgres-stdout.txt"))
                .redirectError(new File("postgres-stderr.txt")).start();

        // Wait until connection can be established
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            getDataSource().getConnection().close();
            return true;
        });
        System.out.println("PostgreSQL started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down PostgreSQL");
            process.destroy();
        }
    }

    @Test
    void migrate() {
        DataSource dataSource = getDataSource();

        Configuration configuration = new FluentConfiguration()
                .dataSource(dataSource)
                .encoding(StandardCharsets.UTF_8)
                .resourceProvider(new FixedResourceProvider());
        configuration.getPluginRegister()
                .getPlugin(PostgreSQLConfigurationExtension.class)
                .setTransactionalLock(false);

        Flyway flyway = new Flyway(configuration);
        MigrateResult migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(2);
    }

    private static DataSource getDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(JDBC_URL);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        dataSource.setDatabaseName(DATABASE);
        return dataSource;
    }

}
