/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_sqlserver.mssql_jdbc;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerResultSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Drives SQL Server server-side cursors through scrollable and updatable
/// JDBC result sets: keyset, dynamic, and forward-only cursors with
/// positioned updates, inserts, deletes, and optimistic concurrency.
///
/// The scenario is a task board that pages through its backlog, edits tasks
/// in place through the cursor, and notices what other sessions changed
/// while the cursor was open, against a real SQL Server.
public class ServerCursorTaskBoardTest {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04";

    private static final String PASSWORD = "TaskBoard#2024";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private static final String CONTAINER = "real-use-mssql-cursor-" + UUID.randomUUID();

    private static final String BACKLOG = "SELECT id, title, status FROM dbo.%s ORDER BY id";

    private static SQLServerDataSource dataSource;

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-e", "ACCEPT_EULA=Y", "-e", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1::1433", IMAGE);
        String mapping = docker("port", CONTAINER, "1433/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        dataSource = new SQLServerDataSource();
        dataSource.setServerName("127.0.0.1");
        dataSource.setPortNumber(port);
        dataSource.setUser("sa");
        dataSource.setPassword(PASSWORD);
        dataSource.setEncrypt("false");
        dataSource.setApplicationName("task-board");
        dataSource.setLoginTimeout(5);
        awaitServer();
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void keysetCursorScrollsThroughTheBacklogInBothDirections() throws Exception {
        seedBoard("scroll_board");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement backlog = connection.prepareStatement(BACKLOG.formatted("scroll_board"),
                        SQLServerResultSet.TYPE_SS_SCROLL_KEYSET, ResultSet.CONCUR_READ_ONLY)) {
            backlog.setFetchSize(2);
            try (ResultSet tasks = backlog.executeQuery()) {
                assertThat(tasks.last()).isTrue();
                assertThat(tasks.getRow()).isEqualTo(6);
                assertThat(tasks.getString("title")).isEqualTo("Write release notes");

                assertThat(tasks.absolute(3)).isTrue();
                assertThat(tasks.getInt("id")).isEqualTo(3);

                assertThat(tasks.relative(-2)).isTrue();
                assertThat(tasks.getInt("id")).isEqualTo(1);
                assertThat(tasks.isFirst()).isTrue();
                assertThat(tasks.previous()).isFalse();
                assertThat(tasks.isBeforeFirst()).isTrue();

                assertThat(tasks.absolute(-2)).isTrue();
                assertThat(tasks.getInt("id")).isEqualTo(5);

                tasks.afterLast();
                List<Integer> reversed = new ArrayList<>();
                while (tasks.previous()) {
                    reversed.add(tasks.getInt("id"));
                }
                assertThat(reversed).containsExactly(6, 5, 4, 3, 2, 1);
            }
        }
    }

    @Test
    void updatableCursorEditsInsertsAndDeletesTasksInPlace() throws Exception {
        seedBoard("edit_board");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
                        ResultSet.CONCUR_UPDATABLE);
                ResultSet tasks = statement.executeQuery(BACKLOG.formatted("edit_board"))) {
            assertThat(tasks.getConcurrency()).isEqualTo(ResultSet.CONCUR_UPDATABLE);

            assertThat(tasks.absolute(2)).isTrue();
            tasks.updateString("status", "DONE");
            tasks.updateRow();
            assertThat(tasks.getString("status")).isEqualTo("DONE");

            tasks.moveToInsertRow();
            tasks.updateInt("id", 7);
            tasks.updateString("title", "Rotate signing keys");
            tasks.updateString("status", "TODO");
            tasks.insertRow();
            tasks.moveToCurrentRow();

            assertThat(tasks.absolute(1)).isTrue();
            tasks.deleteRow();
        }

        assertThat(board("edit_board")).containsExactly(
                Map.entry(2, "DONE"),
                Map.entry(3, "IN_PROGRESS"),
                Map.entry(4, "TODO"),
                Map.entry(5, "TODO"),
                Map.entry(6, "DONE"),
                Map.entry(7, "TODO"));
    }

    @Test
    void optimisticCursorNeverOverwritesATaskChangedByAnotherSession() throws Exception {
        seedBoard("optimistic_board");
        try (Connection editor = dataSource.getConnection();
                Statement statement = editor.createStatement(SQLServerResultSet.TYPE_SS_SCROLL_KEYSET,
                        SQLServerResultSet.CONCUR_SS_OPTIMISTIC_CC);
                ResultSet tasks = statement.executeQuery(
                        "SELECT id, title, status, version FROM dbo.optimistic_board ORDER BY id")) {
            assertThat(tasks.next()).isTrue();
            assertThat(tasks.getString("status")).isEqualTo("TODO");

            updateStatus("optimistic_board", 1, "BLOCKED");

            tasks.updateString("status", "DONE");
            tasks.updateRow();
            assertThat(statusOf("optimistic_board", 1)).isEqualTo("BLOCKED");

            tasks.refreshRow();
            assertThat(tasks.getString("status")).isEqualTo("BLOCKED");
            tasks.updateString("status", "UNBLOCKED");
            tasks.updateRow();
        }

        assertThat(statusOf("optimistic_board", 1)).isEqualTo("UNBLOCKED");
    }

    @Test
    void keysetCursorRefreshesUpdatesAndDetectsDeletesFromAnotherSession() throws Exception {
        seedBoard("refresh_board");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement backlog = connection.prepareStatement(BACKLOG.formatted("refresh_board"),
                        SQLServerResultSet.TYPE_SS_SCROLL_KEYSET, ResultSet.CONCUR_UPDATABLE)) {
            backlog.setFetchSize(1);
            try (ResultSet tasks = backlog.executeQuery()) {
                assertThat(tasks.absolute(4)).isTrue();
                assertThat(tasks.getString("status")).isEqualTo("TODO");

                updateStatus("refresh_board", 4, "DONE");
                try (Connection other = dataSource.getConnection();
                        Statement delete = other.createStatement()) {
                    assertThat(delete.executeUpdate("DELETE FROM dbo.refresh_board WHERE id = 5")).isOne();
                }

                tasks.refreshRow();
                assertThat(tasks.getString("status")).isEqualTo("DONE");

                assertThat(tasks.next()).isTrue();
                assertThat(tasks.rowDeleted()).isTrue();

                assertThat(tasks.next()).isTrue();
                assertThat(tasks.rowDeleted()).isFalse();
                assertThat(tasks.getInt("id")).isEqualTo(6);
            }
        }
    }

    @Test
    void dynamicCursorPicksUpTasksFiledWhileItIsOpen() throws Exception {
        seedBoard("dynamic_board");
        List<String> titles = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement backlog = connection.prepareStatement(BACKLOG.formatted("dynamic_board"),
                        SQLServerResultSet.TYPE_SS_SCROLL_DYNAMIC, ResultSet.CONCUR_READ_ONLY)) {
            backlog.setFetchSize(1);
            try (ResultSet tasks = backlog.executeQuery()) {
                assertThat(tasks.next()).isTrue();
                titles.add(tasks.getString("title"));

                try (Connection other = dataSource.getConnection();
                        PreparedStatement file = other.prepareStatement(
                                "INSERT INTO dbo.dynamic_board (id, title, status) VALUES (?, ?, 'TODO')")) {
                    file.setInt(1, 100);
                    file.setString(2, "Investigate flaky login");
                    assertThat(file.executeUpdate()).isOne();
                }

                while (tasks.next()) {
                    titles.add(tasks.getString("title"));
                }
            }
        }

        assertThat(titles).hasSize(7).startsWith("Triage incoming bugs").endsWith("Investigate flaky login");
    }

    @Test
    void forwardOnlyServerCursorArchivesTasksOnTheSameConnection() throws Exception {
        seedBoard("archive_board");
        SQLServerDataSource cursorSource = new SQLServerDataSource();
        cursorSource.setServerName(dataSource.getServerName());
        cursorSource.setPortNumber(dataSource.getPortNumber());
        cursorSource.setUser("sa");
        cursorSource.setPassword(PASSWORD);
        cursorSource.setEncrypt("false");
        cursorSource.setSelectMethod("cursor");
        cursorSource.setResponseBuffering("adaptive");

        int archived = 0;
        try (Connection connection = cursorSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement scan = connection.createStatement(SQLServerResultSet.TYPE_SS_SERVER_CURSOR_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY);
                    PreparedStatement archive = connection.prepareStatement(
                            "UPDATE dbo.archive_board SET status = 'ARCHIVED' WHERE id = ?")) {
                scan.setFetchSize(2);
                try (ResultSet tasks = scan.executeQuery(
                        "SELECT id, title, status FROM dbo.archive_board WHERE status = 'TODO' ORDER BY id")) {
                    while (tasks.next()) {
                        archive.setInt(1, tasks.getInt("id"));
                        archived += archive.executeUpdate();
                    }
                }
            }
            connection.commit();
        }

        assertThat(archived).isEqualTo(4);
        assertThat(board("archive_board")).containsExactly(
                Map.entry(1, "ARCHIVED"),
                Map.entry(2, "ARCHIVED"),
                Map.entry(3, "IN_PROGRESS"),
                Map.entry(4, "ARCHIVED"),
                Map.entry(5, "ARCHIVED"),
                Map.entry(6, "DONE"));
    }

    private static void seedBoard(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE dbo.%s (
                        id INT PRIMARY KEY,
                        title NVARCHAR(100) NOT NULL,
                        status NVARCHAR(16) NOT NULL,
                        version ROWVERSION)
                    """.formatted(table));
            statement.execute("""
                    INSERT INTO dbo.%s (id, title, status) VALUES
                        (1, 'Triage incoming bugs', 'TODO'),
                        (2, 'Upgrade build plugins', 'TODO'),
                        (3, 'Fix checkout timeout', 'IN_PROGRESS'),
                        (4, 'Document the API', 'TODO'),
                        (5, 'Add audit logging', 'TODO'),
                        (6, 'Write release notes', 'DONE')
                    """.formatted(table));
        }
    }

    private static void updateStatus(String table, int id, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE dbo.%s SET status = ? WHERE id = ?".formatted(table))) {
            update.setString(1, status);
            update.setInt(2, id);
            assertThat(update.executeUpdate()).isOne();
        }
    }

    private static String statusOf(String table, int id) throws SQLException {
        return board(table).get(id);
    }

    private static Map<Integer, String> board(String table) throws SQLException {
        Map<Integer, String> statuses = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, status FROM dbo.%s ORDER BY id".formatted(table))) {
            while (rows.next()) {
                statuses.put(rows.getInt("id"), rows.getString("status"));
            }
        }
        return statuses;
    }

    private static void awaitServer() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("SQL Server did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }
}
