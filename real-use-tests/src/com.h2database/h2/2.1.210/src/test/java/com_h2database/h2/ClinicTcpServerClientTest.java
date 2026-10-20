/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.h2.api.ErrorCode;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The H2 client/server mode: a TCP server started with `org.h2.tools.Server`, remote JDBC
 * sessions over the H2 wire protocol opened through `DriverManager`, `JdbcDataSource`, and
 * `JdbcConnectionPool`, isolation and lock timeouts between remote sessions, remote
 * authentication, and remote server shutdown.
 *
 * A clinic runs one appointment database behind a TCP server that front-desk terminals reach
 * over the network: terminals book slots, compete for the same slot, keep their data across a
 * server restart, and the operator shuts the server down remotely at the end of the day.
 */
public class ClinicTcpServerClientTest {

    private static final String SERVER_PASSWORD = "night-shift";

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS slot (
                id INT PRIMARY KEY,
                doctor VARCHAR(40) NOT NULL,
                starts_at VARCHAR(5) NOT NULL,
                patient VARCHAR(40)
            );
            MERGE INTO slot KEY (id) VALUES
                (1, 'Dr. Ito', '09:00', NULL),
                (2, 'Dr. Ito', '09:30', NULL),
                (3, 'Dr. Okafor', '10:00', NULL);
            """;

    @TempDir
    Path baseDir;

    @Test
    void terminalsShareOneFileDatabaseThatSurvivesServerRestart() throws SQLException {
        Server server = startServer();
        String url = clinicUrl(server.getPort());
        try {
            assertTrue(server.isRunning(true));
            assertTrue(server.getURL().startsWith("tcp://"));
            try (Connection desk = DriverManager.getConnection(url, "sa", "");
                    Statement statement = desk.createStatement()) {
                assertTrue(desk.getMetaData().getURL().startsWith("jdbc:h2:tcp://localhost:"));
                statement.execute(SCHEMA);
                assertEquals(1, book(desk, 1, "Ana Silva"));
            }
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL(url);
            dataSource.setUser("sa");
            dataSource.setPassword("");
            try (Connection otherDesk = dataSource.getConnection()) {
                assertEquals(List.of("09:00 Dr. Ito Ana Silva"), bookedSlots(otherDesk));
            }
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning(false));
        assertTrue(Files.exists(baseDir.resolve("clinic.mv.db")));

        Server restarted = startServer();
        try (Connection desk = DriverManager.getConnection(clinicUrl(restarted.getPort()), "sa", "")) {
            assertEquals(1, book(desk, 3, "Bo Lindqvist"));
            assertEquals(List.of("09:00 Dr. Ito Ana Silva", "10:00 Dr. Okafor Bo Lindqvist"), bookedSlots(desk));
        } finally {
            restarted.stop();
        }
    }

    @Test
    void competingTerminalsSeeCommittedBookingsAndTimeOutOnLockedSlot() throws SQLException {
        Server server = startServer();
        JdbcConnectionPool pool = JdbcConnectionPool.create(clinicUrl(server.getPort()), "sa", "");
        try {
            pool.setMaxConnections(2);
            try (Connection first = pool.getConnection(); Connection second = pool.getConnection()) {
                assertEquals(2, pool.getActiveConnections());
                try (Statement statement = first.createStatement()) {
                    statement.execute(SCHEMA);
                }
                first.setAutoCommit(false);
                assertEquals(1, book(first, 2, "Chen Wei"));

                try (Statement statement = second.createStatement()) {
                    statement.execute("SET LOCK_TIMEOUT 200");
                }
                assertEquals(List.of(), bookedSlots(second));
                SQLException conflict = assertThrows(SQLException.class, () -> book(second, 2, "Dana Novak"));
                assertEquals(ErrorCode.LOCK_TIMEOUT_1, conflict.getErrorCode());

                first.commit();
                assertEquals(List.of("09:30 Dr. Ito Chen Wei"), bookedSlots(second));
                assertEquals(0, book(second, 2, "Dana Novak"));
                first.setAutoCommit(true);
            }
            assertEquals(0, pool.getActiveConnections());
        } finally {
            pool.dispose();
            server.stop();
        }
    }

    @Test
    void serverRejectsUnknownUsersAndAcceptsReadOnlyStaffAccounts() throws SQLException {
        Server server = startServer();
        String url = clinicUrl(server.getPort());
        try {
            try (Connection admin = DriverManager.getConnection(url, "sa", "");
                    Statement statement = admin.createStatement()) {
                statement.execute(SCHEMA);
                statement.execute("CREATE USER IF NOT EXISTS reception PASSWORD 'front-desk'");
                statement.execute("GRANT SELECT ON slot TO reception");
                assertEquals(1, book(admin, 1, "Eli Haddad"));
            }

            SQLException wrongPassword = assertThrows(SQLException.class,
                    () -> DriverManager.getConnection(url, "reception", "guess"));
            assertEquals(ErrorCode.WRONG_USER_OR_PASSWORD, wrongPassword.getErrorCode());

            try (Connection reception = DriverManager.getConnection(url, "reception", "front-desk")) {
                assertEquals(List.of("09:00 Dr. Ito Eli Haddad"), bookedSlots(reception));
                SQLException denied = assertThrows(SQLException.class, () -> book(reception, 2, "Fay Moreau"));
                assertEquals(ErrorCode.NOT_ENOUGH_RIGHTS_FOR_1, denied.getErrorCode());
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void operatorShutsDownServerRemotely() throws SQLException, InterruptedException {
        Server server = startServer();
        int port = server.getPort();
        String url = clinicUrl(port);
        try {
            try (Connection desk = DriverManager.getConnection(url, "sa", "");
                    Statement statement = desk.createStatement()) {
                statement.execute(SCHEMA);
            }
            SQLException wrongPassword = assertThrows(SQLException.class,
                    () -> Server.shutdownTcpServer("tcp://localhost:" + port, "guess", false, false));
            assertEquals(ErrorCode.WRONG_USER_OR_PASSWORD, wrongPassword.getErrorCode());
            assertTrue(server.isRunning(false));

            Server.shutdownTcpServer("tcp://localhost:" + port, SERVER_PASSWORD, true, false);
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (server.isRunning(false) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertFalse(server.isRunning(false));

            SQLException unreachable = assertThrows(SQLException.class,
                    () -> DriverManager.getConnection(url, "sa", ""));
            assertEquals(ErrorCode.CONNECTION_BROKEN_1, unreachable.getErrorCode());
        } finally {
            server.stop();
        }
    }

    private Server startServer() throws SQLException {
        return Server.createTcpServer(
                "-tcpPort", "0",
                "-baseDir", baseDir.toString(),
                "-ifNotExists",
                "-tcpPassword", SERVER_PASSWORD).start();
    }

    private static String clinicUrl(int port) {
        return "jdbc:h2:tcp://localhost:" + port + "/clinic";
    }

    private static int book(Connection connection, int slotId, String patient) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE slot SET patient = ? WHERE id = ? AND patient IS NULL")) {
            update.setString(1, patient);
            update.setInt(2, slotId);
            return update.executeUpdate();
        }
    }

    private static List<String> bookedSlots(Connection connection) throws SQLException {
        List<String> slots = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT starts_at, doctor, patient FROM slot WHERE patient IS NOT NULL ORDER BY id")) {
            while (rows.next()) {
                slots.add(rows.getString(1) + " " + rows.getString(2) + " " + rows.getString(3));
            }
        }
        return slots;
    }
}
