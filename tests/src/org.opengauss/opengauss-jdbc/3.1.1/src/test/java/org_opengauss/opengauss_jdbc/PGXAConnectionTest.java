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
import org.postgresql.PGConnection;
import org.postgresql.xa.PGXADataSource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

import static org.assertj.core.api.Assertions.assertThat;

public class PGXAConnectionTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final int PORT = 15439;
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
        System.out.println("Starting OpenGauss for XA connection tests ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", PORT + ":5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-xa-stdout.txt"))
                .redirectError(new File("opengauss-xa-stderr.txt"))
                .start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            openConnection().close();
            return true;
        });
        System.out.println("OpenGauss for XA connection tests started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for XA connection tests");
            process.destroy();
        }
    }

    @Test
    void createsConnectionProxyFromXaConnectionHandle() throws Exception {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setServerName("localhost");
        dataSource.setPortNumber(PORT);
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);

        XAConnection xaConnection = dataSource.getXAConnection();
        try {
            XAResource xaResource = xaConnection.getXAResource();
            assertThat(xaResource).isNotNull();

            try (Connection connection = xaConnection.getConnection()) {
                assertThat(connection).isInstanceOf(PGConnection.class);
                assertThat(connection.getAutoCommit()).isTrue();
                assertThat(connection.isWrapperFor(PGConnection.class)).isTrue();
                assertThat(connection.nativeSQL("SELECT 1")).isEqualTo("SELECT 1");
            }
        } finally {
            xaConnection.close();
        }
    }
}
