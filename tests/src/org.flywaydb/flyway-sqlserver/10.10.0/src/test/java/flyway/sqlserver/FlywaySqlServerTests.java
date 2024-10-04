/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway.sqlserver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.sql.DataSource;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.awaitility.Awaitility;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FlywaySqlServerTests {

    private static final String USERNAME = "sa";

    private static final String PASSWORD = "Secret12";

    private static final String JDBC_URL = "jdbc:sqlserver://localhost:1433;encrypt=false";

    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting MSSQL ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "1433:1433", "-e", "ACCEPT_EULA=Y",
                "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04")
                .redirectOutput(new File("mssql-stdout.txt"))
                .redirectError(new File("mssql-stderr.txt"))
                .start();

        // Wait until connection can be established
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            getDataSource().getConnection().close();
            return true;
        });
        System.out.println("MSSQL started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down MSSQL");
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

        Flyway flyway = new Flyway(configuration);
        MigrateResult migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(2);
    }

    private static DataSource getDataSource() {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setURL(JDBC_URL);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

}
