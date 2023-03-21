/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb.r2dbc_mariadb;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mariadb.r2dbc.MariadbConnectionConfiguration;
import org.mariadb.r2dbc.MariadbConnectionFactory;
import org.mariadb.r2dbc.api.MariadbConnection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test uses docker to start a MariaDB database to test against.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class R2DBCMariaDBTest {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private Process process;

    private MariadbConnectionFactory connectionFactory;

    @BeforeAll
    void beforeAll() throws IOException {
        System.out.println("Starting MariaDB ...");

        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "3306:3306", "-e", "MARIADB_DATABASE=" + DATABASE, "-e", "MARIADB_USER=" + USERNAME,
                "-e", "MARIADB_PASSWORD=" + PASSWORD, "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true", "mariadb:10.8")
                .redirectOutput(new File("mariadb-stdout.txt"))
                .redirectError(new File("mariadb-stderr.txt"))
                .start();

        connectionFactory = createConnectionFactory();

        // Wait until connection can be established
        waitUntilContainerStarted(60);

        System.out.println("MariaDB started");
    }

    @AfterAll
    void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down MariaDB");
            process.destroy();
        }
    }

    private void waitUntilContainerStarted(int startupTimeoutSeconds) {
        System.out.println("Waiting for database container to become available");

        Exception lastConnectionException = null;

        long end  = System.currentTimeMillis() + startupTimeoutSeconds * 1000;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                // continue
            }
            try {
                MariadbConnection connection = openConnection();
                connection.createStatement("SELECT 1").execute().blockFirst();
                closeConnection(connection);
                return;
            } catch (Exception e) {
                lastConnectionException = e;
            }
        }
        throw new IllegalStateException("Database container cannot be accessed", lastConnectionException);
    }

    private MariadbConnectionFactory createConnectionFactory() {
        MariadbConnectionConfiguration conf = MariadbConnectionConfiguration.builder()
                .host("localhost")
                .port(3306)
                .username(USERNAME)
                .password(PASSWORD)
                .database(DATABASE)
                .build();
        return new MariadbConnectionFactory(conf);
    }

    private MariadbConnection openConnection() {
        return connectionFactory.create().block();
    }

    private void closeConnection(MariadbConnection connection) {
        connection.close().block();
    }

    private void beginTransaction(MariadbConnection connection) {
        if (connection.isAutoCommit()) {
            connection.setAutoCommit(false).block();
        }
        connection.beginTransaction().block();
    }

    private void commitTransaction(MariadbConnection connection) {
        connection.commitTransaction().block();
    }

    private void rollbackTransaction(MariadbConnection connection) {
        connection.rollbackTransaction().block();
    }

    @Test
    void commitAndRollback() {
        MariadbConnection connection = openConnection();
        try {
            beginTransaction(connection);
            connection.createStatement("CREATE TABLE foo (id INT AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))")
                    .execute()
                    .blockFirst();
            commitTransaction(connection);
        } finally {
            closeConnection(connection);
        }

        connection = openConnection();
        try {
            beginTransaction(connection);
            connection.createStatement("INSERT INTO foo (name) VALUES (?), (?)")
                    .bind(0, "Adam")
                    .bind(1, "Eve")
                    .execute()
                    .blockFirst();
            commitTransaction(connection);
        } finally {
            closeConnection(connection);
        }

        connection = openConnection();
        try {
            beginTransaction(connection);
            connection.createStatement("DELETE FROM foo")
                    .execute()
                    .blockFirst();
            rollbackTransaction(connection);
        } finally {
            closeConnection(connection);
        }

        List<String> rows = new ArrayList<>();

        connection = openConnection();
        try {
            connection.createStatement("SELECT * FROM foo")
                    .execute()
                    .flatMap(result -> result.map((row, metadata) -> String.format("%s - %s", row.get("id"), row.get("name"))))
                    .subscribe(result -> rows.add(result));
            commitTransaction(connection);
        } finally {
            closeConnection(connection);
        }

        assertThat(rows.get(0)).isEqualTo("1 - Adam");
        assertThat(rows.get(1)).isEqualTo("2 - Eve");
    }

    @Test
    void simpleDatatypes() {
        MariadbConnection connection = openConnection();
        try {
            beginTransaction(connection);
            connection.createStatement("CREATE TABLE simple_datatypes " +
                    "(b1 BIT, t1 TINYINT, b2 BOOLEAN, s1 SMALLINT, m1 MEDIUMINT, c2 INT, d1 BIGINT, d2 DECIMAL, " +
                    "i1 FLOAT, j1 DOUBLE)")
                    .execute()
                    .blockFirst();

            connection.createStatement("INSERT INTO simple_datatypes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .bind(0, 1)  // BIT
                    .bind(1, (byte) 127) // TINYINT
                    .bind(2, true) // BOOLEAN
                    .bind(3, (short) 2) // SMALLINT
                    .bind(4, 3) // MEDIUMINT
                    .bind(5, 4) // INT
                    .bind(6, Long.MAX_VALUE) // BIGINT
                    .bind(7, Math.PI) // DECIMAL
                    .bind(8, 42.2f) // FLOAT
                    .bind(9, Math.PI) // DOUBLE
                    .execute()
                    .blockFirst();

            connection.createStatement("SELECT * FROM simple_datatypes")
                    .execute()
                    .flatMap(result -> result.map((row, metadata) -> String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s, %s",
                            row.get("b1"), row.get("t1"), row.get("b2"), row.get("s1"), row.get("m1"),
                            row.get("c2"), row.get("d1"), row.get("d2"), row.get("i1"), row.get("j1"))))
                    .subscribe(result -> System.out.println(result));
            commitTransaction(connection);
        } finally {
            closeConnection(connection);
        }
    }
}
