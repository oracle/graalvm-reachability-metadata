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
import org.postgresql.PGStatement;
import org.postgresql.ds.PGConnectionPoolDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.PooledConnection;

import static org.assertj.core.api.Assertions.assertThat;

public class PGPooledConnectionInnerConnectionHandlerTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final int PORT = 15434;
    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for pooled connection tests ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", PORT + ":5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-pooled-connection-stdout.txt"))
                .redirectError(new File("opengauss-pooled-connection-stderr.txt"))
                .start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            PooledConnection pooledConnection = newDataSource().getPooledConnection();
            pooledConnection.close();
            return true;
        });
        System.out.println("OpenGauss started for pooled connection tests");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for pooled connection tests");
            process.destroy();
        }
    }

    @Test
    void pooledConnectionProxyWrapsConnectionAndStatementMethods() throws Exception {
        PooledConnection pooledConnection = newDataSource().getPooledConnection();
        try (Connection connection = pooledConnection.getConnection()) {
            assertThat(connection.toString())
                    .contains("Pooled connection wrapping physical connection");
            assertThat(connection.equals(connection)).isTrue();
            assertThat(connection.hashCode()).isEqualTo(System.identityHashCode(connection));
            assertThat(connection.isClosed()).isFalse();
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(connection.nativeSQL("SELECT 1")).isEqualTo("SELECT 1");

            try (Statement statement = connection.createStatement()) {
                assertThat(statement).isInstanceOf(PGStatement.class);
                assertThat(statement.getConnection()).isSameAs(connection);
            }

            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT ?")) {
                assertThat(preparedStatement).isInstanceOf(PGStatement.class);
                assertThat(preparedStatement.getConnection()).isSameAs(connection);
            }

            try (CallableStatement callableStatement = connection.prepareCall("SELECT 1")) {
                assertThat(callableStatement).isInstanceOf(PGStatement.class);
                assertThat(callableStatement.getConnection()).isSameAs(connection);
            }
        } finally {
            pooledConnection.close();
        }
    }

    private static PGConnectionPoolDataSource newDataSource() {
        PGConnectionPoolDataSource dataSource = new PGConnectionPoolDataSource();
        dataSource.setServerName("localhost");
        dataSource.setPortNumber(PORT);
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        dataSource.setDefaultAutoCommit(true);
        return dataSource;
    }
}
