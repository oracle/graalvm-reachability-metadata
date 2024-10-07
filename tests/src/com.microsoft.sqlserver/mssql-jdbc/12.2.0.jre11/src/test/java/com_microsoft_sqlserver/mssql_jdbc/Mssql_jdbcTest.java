/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class Mssql_jdbcTest {

    private static final String USERNAME = "sa";

    private static final String PASSWORD = "Secret12";

    private static final String JDBC_URL = "jdbc:sqlserver://localhost:1433;encrypt=false";

    private static Process process;

    private static Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, props);
    }

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
        waitUntilContainerStarted(60);

        System.out.println("MSSQL started");
    }

    private static void waitUntilContainerStarted(int startupTimeoutSeconds) {
        System.out.println("Waiting for database container to become available");

        Exception lastConnectionException = null;

        long end  = System.currentTimeMillis() + startupTimeoutSeconds * 1000;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                // continue
            }
            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (Exception e) {
                lastConnectionException = e;
            }
        }
        throw new IllegalStateException("Database container cannot be accessed by JDBC URL: " + JDBC_URL, lastConnectionException);
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down MSSQL");
            process.destroy();
        }
    }

    @Test
    void commitAndRollback() throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            conn.prepareStatement("CREATE TABLE foo (id INT IDENTITY PRIMARY KEY, name VARCHAR(255))").execute();
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
                    "(b1 BIT, t1 TINYINT, s1 SMALLINT, i1 INT, b2 BIGINT, " +
                    "r1 REAL, f1 FLOAT, d1 DECIMAL, " +
                    "d2 DATE, d3 DATETIME)").execute();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO simple_datatypes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setBoolean(1, true); // BIT
            statement.setByte(2, (byte) 127); // TINYINT
            statement.setShort(3, (short) 2); // SMALLINT
            statement.setInt(4, 4); // INT
            statement.setLong(5, Long.MAX_VALUE); // BIGINT
            statement.setFloat(6, 42.2f); // REAL
            statement.setDouble(7, Math.PI); // FLOAT
            statement.setBigDecimal(8, BigDecimal.ONE); // DECIMAL
            statement.setDate(9, new Date(System.currentTimeMillis())); // DATE
            statement.setTimestamp(10, new Timestamp(System.currentTimeMillis())); // DATETIME
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
