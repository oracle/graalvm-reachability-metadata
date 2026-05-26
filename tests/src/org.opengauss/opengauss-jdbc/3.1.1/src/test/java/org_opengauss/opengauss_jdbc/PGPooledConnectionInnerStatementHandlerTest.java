/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGConnectionPoolDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;

import javax.sql.PooledConnection;

import static org.assertj.core.api.Assertions.assertThat;

public class PGPooledConnectionInnerStatementHandlerTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final int PORT = 15436;
    private static final String JDBC_URL = "jdbc:postgresql://localhost:" + PORT + "/" + DATABASE;
    private static Process process;

    private static Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, props);
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for pooled statement tests ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", PORT + ":5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-pooled-statement-stdout.txt"))
                .redirectError(new File("opengauss-pooled-statement-stderr.txt"))
                .start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            openConnection().close();
            return true;
        });
        System.out.println("OpenGauss for pooled statement tests started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for pooled statement tests");
            process.destroy();
        }
    }

    @Test
    void delegatesStatementCallsToPhysicalStatement() throws Exception {
        PGConnectionPoolDataSource dataSource = new PGConnectionPoolDataSource();
        dataSource.setServerName("localhost");
        dataSource.setPortNumber(PORT);
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);

        PooledConnection pooledConnection = dataSource.getPooledConnection();
        try {
            try (Connection connection = pooledConnection.getConnection();
                    Statement statement = connection.createStatement()) {
                assertThat(statement.toString()).contains("Pooled statement wrapping physical statement");
                assertThat(statement.equals(statement)).isTrue();
                assertThat(statement.hashCode()).isEqualTo(System.identityHashCode(statement));
                assertThat(statement.getConnection()).isSameAs(connection);
                assertThat(statement.getMaxRows()).isZero();
                assertThat(statement.execute("SELECT 1")).isTrue();

                try (ResultSet resultSet = statement.getResultSet()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(1);
                    assertThat(resultSet.next()).isFalse();
                }
            }
        } finally {
            pooledConnection.close();
        }
    }
}
