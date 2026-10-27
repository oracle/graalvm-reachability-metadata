/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_mysql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.CleanResult;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/// Runs Flyway against a MySQL server so that the MySQL database support of flyway-mysql performs the work.
public class MySQLDatabaseTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";

    private static final String ROOT_PASSWORD = "flyway-secret";

    @TempDir
    static Path migrations;

    private static DockerDatabaseServer server;

    @BeforeAll
    static void startServer() throws IOException, InterruptedException {
        writeMigration("V1__create_library.sql", """
                # Tables created by the first versioned migration
                CREATE TABLE author (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL
                ) ENGINE = InnoDB;

                CREATE TABLE book (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    author_id INT NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    CONSTRAINT fk_book_author FOREIGN KEY (author_id) REFERENCES author (id)
                ) ENGINE = InnoDB;

                /*!40101 SET @previous_client = @@character_set_client */;
                INSERT INTO author (name) VALUES ('Ada'), ('Grace');
                SET @grace = (SELECT id FROM author WHERE name = 'Grace');
                INSERT INTO book (author_id, title) VALUES (@grace, 'Compilers');
                """);
        writeMigration("V2__add_routines.sql", """
                DELIMITER $$
                CREATE PROCEDURE add_book(IN p_author VARCHAR(100), IN p_title VARCHAR(200))
                BEGIN
                    DECLARE v_author INT;
                    INSERT INTO author (name) VALUES (p_author);
                    SET v_author = LAST_INSERT_ID();
                    INSERT INTO book (author_id, title) VALUES (v_author, p_title);
                END $$

                CREATE FUNCTION book_count_for(p_author VARCHAR(100)) RETURNS INT
                READS SQL DATA
                BEGIN
                    DECLARE v_count INT;
                    SELECT COUNT(*) INTO v_count FROM book b JOIN author a ON a.id = b.author_id
                        WHERE a.name = p_author;
                    RETURN v_count;
                END $$
                DELIMITER ;

                CALL add_book('Barbara', 'Abstraction');
                """);
        writeMigration("V3__add_view_trigger_and_event.sql", """
                CREATE VIEW book_titles AS
                    SELECT a.name AS author, b.title AS title FROM book b JOIN author a ON a.id = b.author_id;

                -- Normalizes titles written after this migration
                CREATE TRIGGER book_title_trim BEFORE INSERT ON book
                    FOR EACH ROW SET NEW.title = TRIM(NEW.title);

                CREATE EVENT purge_untitled_books ON SCHEDULE EVERY 1 DAY
                    DO DELETE FROM book WHERE title = '';
                """);
        writeMigration("R__book_summary.sql", """
                CREATE OR REPLACE VIEW book_summary AS SELECT COUNT(*) AS total FROM book;
                """);
        server = DockerDatabaseServer.start(IMAGE, "jdbc:mysql:", ROOT_PASSWORD,
                List.of("MYSQL_ROOT_PASSWORD=" + ROOT_PASSWORD, "MYSQL_ROOT_HOST=%"));
    }

    @AfterAll
    static void stopServer() throws IOException, InterruptedException {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void migratesVersionedAndRepeatableScripts() throws SQLException {
        Flyway flyway = flyway("flyway_migrate");

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.databaseType).isEqualTo("MySQL");
        assertThat(result.schemaName).isEqualTo("flyway_migrate");
        assertThat(result.migrationsExecuted).isEqualTo(4);
        assertThat(result.targetSchemaVersion).isEqualTo("3");
        assertThat(queryStrings("flyway_migrate", "SELECT CONCAT(author, ':', title) FROM book_titles ORDER BY title"))
                .containsExactly("Barbara:Abstraction", "Grace:Compilers");
        assertThat(queryStrings("flyway_migrate", "SELECT CAST(total AS CHAR) FROM book_summary"))
                .containsExactly("2");
        assertThat(queryStrings("flyway_migrate", "SELECT CAST(book_count_for('Barbara') AS CHAR)"))
                .containsExactly("1");

        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void appliedRoutinesAndTriggersKeepWorking() throws SQLException {
        flyway("flyway_routines").migrate();

        try (Connection connection = server.openConnection("flyway_routines");
                PreparedStatement call = connection.prepareStatement("CALL add_book(?, ?)")) {
            call.setString(1, "Edsger");
            call.setString(2, "   Structured Programming   ");
            call.execute();
        }

        assertThat(queryStrings("flyway_routines", "SELECT title FROM book_titles WHERE author = 'Edsger'"))
                .containsExactly("Structured Programming");
        assertThat(queryStrings("flyway_routines", "SELECT event_name FROM information_schema.events "
                + "WHERE event_schema = 'flyway_routines'"))
                .containsExactly("purge_untitled_books");
    }

    @Test
    void reportsInfoAndValidatesAppliedMigrations() {
        Flyway flyway = flyway("flyway_info");
        assertThat(flyway.info().pending()).hasSize(4);

        flyway.migrate();

        MigrationInfo current = flyway.info().current();
        assertThat(current.getVersion().getVersion()).isEqualTo("3");
        assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getScript)
                .contains("V1__create_library.sql", "V2__add_routines.sql", "V3__add_view_trigger_and_event.sql",
                        "R__book_summary.sql");
        ValidateResult validation = flyway.validateWithResult();
        assertThat(validation.validationSuccessful).isTrue();
        assertThat(validation.invalidMigrations).isEmpty();
    }

    @Test
    void cleansExistingSchemaAndDropsSchemaItCreated() throws SQLException {
        execute("CREATE SCHEMA flyway_clean");
        Flyway flyway = flyway("flyway_clean", "flyway_clean_created");
        flyway.migrate();
        assertThat(count("SELECT COUNT(*) FROM information_schema.schemata "
                + "WHERE schema_name = 'flyway_clean_created'")).isEqualTo(1);

        CleanResult result = flyway.clean();

        assertThat(result.schemasCleaned).contains("flyway_clean");
        assertThat(result.schemasDropped).contains("flyway_clean_created");
        assertThat(count("SELECT COUNT(*) FROM information_schema.schemata "
                + "WHERE schema_name = 'flyway_clean_created'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'flyway_clean'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM information_schema.views WHERE table_schema = 'flyway_clean'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = 'flyway_clean'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM information_schema.events WHERE event_schema = 'flyway_clean'"))
                .isZero();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
    }

    private static Flyway flyway(String... schemas) {
        return Flyway.configure()
                .dataSource(server.jdbcUrl(""), server.user(), server.password())
                .schemas(schemas)
                .createSchemas(true)
                .cleanDisabled(false)
                .locations("filesystem:" + migrations.toAbsolutePath())
                .load();
    }

    private static void writeMigration(String fileName, String sql) throws IOException {
        Files.writeString(migrations.resolve(fileName), sql, StandardCharsets.UTF_8);
    }

    private static List<String> queryStrings(String database, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = server.openConnection(database);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = server.openConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(String sql) throws SQLException {
        return Integer.parseInt(queryStrings("", sql).get(0));
    }
}
