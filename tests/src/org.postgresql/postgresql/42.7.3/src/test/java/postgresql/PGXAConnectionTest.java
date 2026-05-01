/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.XAConnection;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.xa.PGXADataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic proxy creation in {@code org.postgresql.xa.PGXAConnection}.
 */
public class PGXAConnectionTest {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static String containerId;

    private static int databasePort;

    @BeforeAll
    static void beforeAll() throws Exception {
        containerId = commandOutput("docker", "run", "--rm", "-d", "-p", "127.0.0.1::5432", "-e", "POSTGRES_DB=" + DATABASE,
                "-e", "POSTGRES_USER=" + USERNAME, "-e", "POSTGRES_PASSWORD=" + PASSWORD, "postgres:18-alpine");
        databasePort = Integer.parseInt(commandOutput("docker", "inspect", "--format",
                "{{(index (index .NetworkSettings.Ports \"5432/tcp\") 0).HostPort}}", containerId));

        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            XAConnection xaConnection = openDataSource().getXAConnection();
            try {
                try (Connection connection = xaConnection.getConnection()) {
                    return connection.isValid(1);
                }
            } finally {
                xaConnection.close();
            }
        });
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (containerId != null) {
            commandOutput("docker", "rm", "-f", containerId);
        }
    }

    @Test
    void getConnectionReturnsXaGuardedProxyImplementingJdbcAndPostgresqlInterfaces() throws Exception {
        XAConnection xaConnection = openDataSource().getXAConnection();
        try {
            try (Connection connection = xaConnection.getConnection()) {
                assertThat(connection).isInstanceOf(PGConnection.class);
                assertThat(connection.getAutoCommit()).isTrue();
                assertThat(((PGConnection) connection).getBackendPID()).isPositive();

                try (Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(1);
                }
            }
        } finally {
            xaConnection.close();
        }
    }

    private static PGXADataSource openDataSource() {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setServerNames(new String[] {"127.0.0.1"});
        dataSource.setPortNumbers(new int[] {databasePort});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    private static String commandOutput(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        String text = new String(output, StandardCharsets.UTF_8).trim();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Command failed with exit code " + exitCode + ": " + String.join(" ", command) + "\n" + text);
        }
        return text;
    }
}
