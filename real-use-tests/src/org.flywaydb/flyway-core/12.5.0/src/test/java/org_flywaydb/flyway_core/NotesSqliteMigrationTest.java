/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.CleanResult;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SQLite database support: migrating a file-based SQLite database configured by JDBC URL,
 * parsing SQLite scripts (trigger bodies with nested `BEGIN ... CASE ... END` blocks,
 * `AUTOINCREMENT` tables, `PRAGMA` statements including the non-transactional
 * `PRAGMA foreign_keys`), keeping the schema history in the `main` schema across application
 * restarts, and `clean` dropping every table, view and trigger before a rebuild.
 *
 * A desktop notes application keeps its data in a single SQLite file in the user's profile.
 * Every start migrates that file: the first start creates notebooks, notes, an audit trigger
 * and a summary view, and stamps the file's `user_version`. A later start finds the schema up
 * to date, and the "reset all data" action cleans the file and rebuilds it from the scripts.
 */
public class NotesSqliteMigrationTest {

    @TempDir
    Path migrations;

    @TempDir
    Path profile;

    @Test
    void migratesSqliteFileAcrossRestartsAndResetsIt() throws Exception {
        writeScript("V1__create_notebooks.sql", """
                CREATE TABLE notebook (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL UNIQUE
                );
                CREATE TABLE note (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    notebook_id INTEGER NOT NULL REFERENCES notebook (id) ON DELETE CASCADE,
                    body TEXT NOT NULL,
                    size_class TEXT
                );
                """);
        writeScript("V2__audit_note_inserts.sql", """
                CREATE TABLE note_audit (
                    note_id INTEGER NOT NULL,
                    action TEXT NOT NULL
                );
                -- The trigger body nests a CASE ... END inside BEGIN ... END.
                CREATE TRIGGER note_after_insert AFTER INSERT ON note
                BEGIN
                    UPDATE note SET size_class = CASE
                        WHEN length(NEW.body) > 20 THEN 'long'
                        ELSE 'short'
                    END
                    WHERE id = NEW.id;
                    INSERT INTO note_audit (note_id, action) VALUES (NEW.id, 'created');
                END;
                """);
        writeScript("V3__enforce_foreign_keys.sql", """
                PRAGMA foreign_keys = ON;
                """);
        writeScript("V4__stamp_file_version.sql", """
                PRAGMA user_version = 4;
                """);
        writeScript("R__notebook_summary.sql", """
                DROP VIEW IF EXISTS notebook_summary;
                CREATE VIEW notebook_summary AS
                    SELECT b.title AS title, COUNT(n.id) AS notes,
                           SUM(CASE WHEN n.size_class = 'long' THEN 1 ELSE 0 END) AS long_notes
                    FROM notebook b LEFT JOIN note n ON n.notebook_id = b.id
                    GROUP BY b.title;
                """);

        String url = "jdbc:sqlite:" + profile.resolve("notes.db").toAbsolutePath();

        Flyway firstStart = configure(url, true);
        assertEquals(5, firstStart.info().pending().length);
        MigrateResult created = firstStart.migrate();
        assertTrue(created.success);
        assertEquals(5, created.migrationsExecuted);
        assertEquals("4", created.targetSchemaVersion);
        assertEquals(List.of("1:create notebooks", "2:audit note inserts",
                "3:enforce foreign keys", "4:stamp file version", "null:notebook summary"),
                applied(firstStart.info()));

        try (Connection connection = DriverManager.getConnection(url)) {
            long work = addNotebook(connection, "Work");
            long home = addNotebook(connection, "Home");
            addNote(connection, work, "Ship the release notes before Friday");
            addNote(connection, work, "Standup 9:30");
            addNote(connection, home, "Buy milk");

            assertEquals(4, queryInt(connection, "PRAGMA user_version"));
            assertEquals(List.of("Home:1:0", "Work:2:1"), strings(connection, """
                    SELECT title || ':' || notes || ':' || long_notes
                    FROM notebook_summary ORDER BY title
                    """));
            assertEquals(List.of("1:created", "2:created", "3:created"), strings(connection, """
                    SELECT note_id || ':' || action FROM note_audit ORDER BY note_id
                    """));
            assertEquals(List.of("flyway_schema_history", "note", "note_audit", "notebook",
                    "note_after_insert", "notebook_summary"), schemaObjects(connection));
        }

        Flyway restart = configure(url, true);
        MigrationInfoService upToDate = restart.info();
        assertEquals("4", upToDate.current().getVersion().getVersion());
        assertEquals(0, upToDate.pending().length);
        assertTrue(restart.validateWithResult().validationSuccessful);
        MigrateResult noop = restart.migrate();
        assertTrue(noop.success);
        assertEquals(0, noop.migrationsExecuted);
        assertEquals("4", noop.initialSchemaVersion);

        Flyway reset = configure(url, false);
        CleanResult cleaned = reset.clean();
        assertEquals(List.of("main"), cleaned.schemasCleaned);
        try (Connection connection = DriverManager.getConnection(url)) {
            assertEquals(List.of(), schemaObjects(connection));
        }
        assertEquals(5, reset.info().pending().length);

        MigrateResult rebuilt = reset.migrate();
        assertTrue(rebuilt.success);
        assertEquals(5, rebuilt.migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(url)) {
            long inbox = addNotebook(connection, "Inbox");
            assertEquals(1, inbox);
            assertEquals(1, addNote(connection, inbox, "Fresh start after reset"));
            assertEquals(List.of("Inbox:1:1"), strings(connection, """
                    SELECT title || ':' || notes || ':' || long_notes FROM notebook_summary
                    """));
        }
    }

    private Flyway configure(String url, boolean cleanDisabled) {
        return Flyway.configure()
                .dataSource(url, null, null)
                .locations("filesystem:" + migrations.toAbsolutePath())
                .cleanDisabled(cleanDisabled)
                .load();
    }

    private static long addNotebook(Connection connection, String title) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO notebook (title) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, title);
            insert.executeUpdate();
            return generatedKey(insert);
        }
    }

    private static long addNote(Connection connection, long notebookId, String body)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO note (notebook_id, body) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, notebookId);
            insert.setString(2, body);
            insert.executeUpdate();
            return generatedKey(insert);
        }
    }

    private static long generatedKey(Statement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertTrue(keys.next());
            return keys.getLong(1);
        }
    }

    private static List<String> applied(MigrationInfoService info) {
        List<String> applied = new ArrayList<>();
        for (MigrationInfo migration : info.applied()) {
            applied.add(migration.getVersion() + ":" + migration.getDescription());
        }
        return applied;
    }

    private static List<String> schemaObjects(Connection connection) throws SQLException {
        return strings(connection, """
                SELECT name FROM sqlite_master
                WHERE type IN ('table', 'view', 'trigger') AND name NOT LIKE 'sqlite_%'
                ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'trigger' THEN 1 ELSE 2 END, name
                """);
    }

    private static int queryInt(Connection connection, String sql) throws SQLException {
        return Integer.parseInt(strings(connection, sql).get(0));
    }

    private static List<String> strings(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private void writeScript(String fileName, String sql) throws IOException {
        Files.writeString(migrations.resolve(fileName), sql, StandardCharsets.UTF_8);
    }
}
