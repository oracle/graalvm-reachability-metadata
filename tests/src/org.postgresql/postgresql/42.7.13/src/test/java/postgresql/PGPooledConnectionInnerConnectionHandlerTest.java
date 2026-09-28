/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;

import javax.sql.PooledConnection;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGStatement;
import org.postgresql.ds.PGConnectionPoolDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the pooled connection handle that delegates JDBC calls through
 * {@code PGPooledConnection.ConnectionHandler}.
 */
public class PGPooledConnectionInnerConnectionHandlerTest {

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
            try (Connection connection = openDataSource().getConnection()) {
                return connection.isValid(1);
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
    void pooledConnectionHandleWrapsCreatedStatementsAndDelegatesConnectionMethods() throws Throwable {
        PGConnectionPoolDataSource dataSource = openDataSource();

        PooledConnection pooledConnection = dataSource.getPooledConnection();
        try (Connection connection = pooledConnection.getConnection()) {
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(connection.unwrap(PGConnection.class).getBackendPID()).isPositive();

            InvocationHandler handler = Proxy.getInvocationHandler(connection);
            Method getClassMethod = Object.class.getMethod("getClass");
            Object delegatedClass = handler.invoke(connection, getClassMethod, null);
            assertThat(Connection.class.isAssignableFrom((Class<?>) delegatedClass)).isTrue();

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertThat(statement).isInstanceOf(PGStatement.class);
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT ?::int")) {
                assertThat(statement).isInstanceOf(PGStatement.class);
                statement.setInt(1, 42);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(42);
                }
            }

            try (CallableStatement statement = connection.prepareCall("{ ? = call lower(?) }")) {
                assertThat(statement).isInstanceOf(PGStatement.class);
                statement.registerOutParameter(1, Types.VARCHAR);
                statement.setString(2, "POOLED");
                statement.execute();
                assertThat(statement.getString(1)).isEqualTo("pooled");
            }
        } finally {
            pooledConnection.close();
        }
    }

    private static PGConnectionPoolDataSource openDataSource() {
        PGConnectionPoolDataSource dataSource = new PGConnectionPoolDataSource();
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
