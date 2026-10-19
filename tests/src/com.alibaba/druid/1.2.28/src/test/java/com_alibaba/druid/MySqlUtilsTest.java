/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.util.MySqlUtils;
import com.mysql.jdbc.Driver;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.sql.XAConnection;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MySqlUtilsTest {
    private static final String DATABASE = "druid";
    private static final String USERNAME = "druid";
    private static final String PASSWORD = "secret";
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/" + DATABASE
            + "?allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=10000&socketTimeout=10000";

    private static Process mariadb;

    @BeforeAll
    static void startMariaDb() throws IOException {
        mariadb = new ProcessBuilder(
                        "docker",
                        "run",
                        "--rm",
                        "-p",
                        "3306:3306",
                        "-e",
                        "MARIADB_DATABASE=" + DATABASE,
                        "-e",
                        "MARIADB_USER=" + USERNAME,
                        "-e",
                        "MARIADB_PASSWORD=" + PASSWORD,
                        "-e",
                        "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                        "mariadb:13.0")
                .redirectOutput(new File("druid-mariadb-stdout.txt"))
                .redirectError(new File("druid-mariadb-stderr.txt"))
                .start();

        Awaitility.await().atMost(Duration.ofSeconds(50)).ignoreExceptionsInstanceOf(SQLException.class).until(() -> {
            try (Connection connection = openConnection(false)) {
                return connection.isValid(10);
            }
        });
    }

    @AfterAll
    static void stopMariaDb() throws InterruptedException {
        if (mariadb != null && mariadb.isAlive()) {
            mariadb.destroy();
            if (!mariadb.waitFor(10, TimeUnit.SECONDS)) {
                mariadb.destroyForcibly();
                mariadb.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    @Timeout(59)
    @SuppressWarnings("deprecation")
    void exposesConnectionStateAndCreatesXaConnections() throws Exception {
        Driver driver = new Driver();
        try (Connection physical = openConnection(false)) {
            Long connectionId = MySqlUtils.getId(physical);
            assertNotNull(connectionId);
            assertTrue(connectionId > 0);
            assertEquals("KILL QUERY " + connectionId, MySqlUtils.buildKillQuerySql(physical, null));

            try (Statement statement = physical.createStatement()) {
                assertTrue(statement.executeQuery("SELECT 1").next());
            }
            long lastPacketReceived = MySqlUtils.getLastPacketReceivedTimeMs(physical);
            assertTrue(lastPacketReceived > 0);
            assertTrue(lastPacketReceived <= System.currentTimeMillis());

            assertXaConnectionWorks(driver, physical);
        }

        try (Connection pinnedPhysical = openConnection(true)) {
            assertXaConnectionWorks(driver, pinnedPhysical);
        }
    }

    private static Connection openConnection(boolean pinGlobalTransaction) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", USERNAME);
        properties.setProperty("password", PASSWORD);
        properties.setProperty("pinGlobalTxToPhysicalConnection", Boolean.toString(pinGlobalTransaction));
        return DriverManager.getConnection(JDBC_URL, properties);
    }

    private static void assertXaConnectionWorks(Driver driver, Connection physical) throws Exception {
        XAConnection xaConnection = MySqlUtils.createXAConnection(driver, physical);
        try {
            try (Connection logical = xaConnection.getConnection()) {
                assertTrue(logical.isValid(10));
            }
        } finally {
            xaConnection.close();
        }
    }
}
