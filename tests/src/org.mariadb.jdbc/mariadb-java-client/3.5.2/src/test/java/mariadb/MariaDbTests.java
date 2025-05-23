/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mariadb;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test uses docker to start a MariaDB database to test against.
 */
public class MariaDbTests {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static final String JDBC_URL = "jdbc:mariadb://localhost:3306/" + DATABASE;

    private static Process process;

    private static Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, props);
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting MariaDB ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "3306:3306", "-e", "MARIADB_DATABASE=" + DATABASE, "-e", "MARIADB_USER=" + USERNAME,
                "-e", "MARIADB_PASSWORD=" + PASSWORD, "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true", "mariadb:10.8").redirectOutput(new File("mariadb-stdout.txt"))
                .redirectError(new File("mariadb-stderr.txt")).start();

        // Wait until connection can be established
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptionsMatching(e ->
                e instanceof SQLNonTransientConnectionException
        ).until(() -> {
            openConnection().close();
            return true;
        });
        System.out.println("MariaDB started");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down MariaDB");
            process.destroy();
        }
    }

    @Test
    void commitAndRollback() throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            conn.prepareStatement("CREATE TABLE foo (id INT AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))").execute();
            conn.commit();
        }

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            PreparedStatement statement = conn.prepareStatement("INSERT INTO foo (name) VALUES (?)");
            statement.setString(1, "Adam");
            statement.execute();
            statement.setString(1, "Eve");
            statement.execute();
            conn.commit();
        }

        try (Connection conn = openConnection()) {
            // Test rollbacks
            conn.setAutoCommit(false);
            conn.prepareStatement("DELETE FROM foo").execute();
            conn.rollback();
        }

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try (ResultSet resultSet = conn.prepareStatement("SELECT * FROM foo").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
                assertThat(resultSet.getString(2)).isEqualTo("Adam");
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(2);
                assertThat(resultSet.getString(2)).isEqualTo("Eve");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    void simpleDatatypes() throws Exception {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            connection.prepareStatement("CREATE TABLE simple_datatypes " +
                    "(b1 BIT, t1 TINYINT, b2 BOOLEAN, s1 SMALLINT, m1 MEDIUMINT, c2 INT, d1 BIGINT, d2 DECIMAL, " +
                    "i1 FLOAT, j1 DOUBLE)").execute();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO simple_datatypes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setInt(1, 1); // BIT
            statement.setByte(2, (byte) 127); // TINYINT
            statement.setBoolean(3, true); // BOOLEAN
            statement.setShort(4, (short) 2); // SMALLINT
            statement.setInt(5, 3); // MEDIUMINT
            statement.setInt(6, 4); // INT
            statement.setLong(7, Long.MAX_VALUE); // BIGINT
            statement.setDouble(8, Math.PI); // DECIMAL
            statement.setFloat(9, 42.2f); // FLOAT
            statement.setDouble(10, Math.PI); // DOUBLE
            statement.execute();

            try (ResultSet resultSet = connection.prepareStatement("SELECT * FROM simple_datatypes").executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                for (int i = 1; i <= 10; i++) {
                    System.out.printf("column %d: %s%n", i, resultSet.getObject(i));
                }
            }
        }
    }
}
