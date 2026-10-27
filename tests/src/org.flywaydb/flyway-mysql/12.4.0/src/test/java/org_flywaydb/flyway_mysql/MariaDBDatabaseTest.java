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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.CleanResult;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/// Runs Flyway against a MariaDB server so that the MariaDB database support of flyway-mysql performs the work.
public class MariaDBDatabaseTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String ROOT_PASSWORD = "flyway-secret";

    @TempDir
    static Path migrations;

    private static DockerDatabaseServer server;

    @BeforeAll
    static void startServer() throws IOException, InterruptedException {
        writeMigration("V1__create_inventory.sql", """
                CREATE SEQUENCE item_ids START WITH 100 INCREMENT BY 10;

                CREATE TABLE item (
                    id BIGINT PRIMARY KEY DEFAULT NEXTVAL(item_ids),
                    name VARCHAR(100) NOT NULL
                );

                CREATE TABLE price (
                    item_id BIGINT PRIMARY KEY,
                    amount DECIMAL(10, 2) NOT NULL
                ) WITH SYSTEM VERSIONING;

                INSERT INTO item (name) VALUES ('anvil'), ('rope');
                """);
        writeMigration("V2__add_pricing_procedure.sql", """
                DELIMITER //
                CREATE PROCEDURE set_price(IN p_name VARCHAR(100), IN p_amount DECIMAL(10, 2))
                BEGIN
                    DECLARE v_item BIGINT;
                    SELECT id INTO v_item FROM item WHERE name = p_name;
                    REPLACE INTO price (item_id, amount) VALUES (v_item, p_amount);
                END //
                DELIMITER ;

                CALL set_price('anvil', 99.50);
                CALL set_price('rope', 4.25);
                """);
        writeMigration("R__priced_items.sql", """
                CREATE OR REPLACE VIEW priced_item AS
                    SELECT i.name AS name, p.amount AS amount FROM item i JOIN price p ON p.item_id = i.id;
                """);
        server = DockerDatabaseServer.start(IMAGE, "jdbc:mariadb:", ROOT_PASSWORD,
                List.of("MARIADB_ROOT_PASSWORD=" + ROOT_PASSWORD));
    }

    @AfterAll
    static void stopServer() throws IOException, InterruptedException {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void migratesScriptsWithSequencesAndSystemVersionedTables() throws SQLException {
        Flyway flyway = flyway("flyway_maria");

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.databaseType).isEqualTo("MariaDB");
        assertThat(result.migrationsExecuted).isEqualTo(3);
        assertThat(result.targetSchemaVersion).isEqualTo("2");
        assertThat(queryStrings("flyway_maria", "SELECT CONCAT(name, '=', amount) FROM priced_item ORDER BY name"))
                .containsExactly("anvil=99.50", "rope=4.25");
        assertThat(queryStrings("flyway_maria", "SELECT CAST(id AS CHAR) FROM item ORDER BY id"))
                .containsExactly("100", "110");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void cleansSequencesAndSystemVersionedTables() throws SQLException {
        execute("CREATE SCHEMA flyway_maria_clean");
        Flyway flyway = flyway("flyway_maria_clean");
        flyway.migrate();

        CleanResult result = flyway.clean();

        assertThat(result.schemasCleaned).contains("flyway_maria_clean");
        assertThat(queryStrings("", "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'flyway_maria_clean'")).isEmpty();
        assertThat(queryStrings("", "SELECT routine_name FROM information_schema.routines "
                + "WHERE routine_schema = 'flyway_maria_clean'")).isEmpty();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
    }

    private static Flyway flyway(String schema) {
        return Flyway.configure()
                .dataSource(server.jdbcUrl(""), server.user(), server.password())
                .schemas(schema)
                .createSchemas(true)
                .cleanDisabled(false)
                .locations("filesystem:" + migrations.toAbsolutePath())
                .load();
    }

    private static void writeMigration(String fileName, String sql) throws IOException {
        Files.writeString(migrations.resolve(fileName), sql, StandardCharsets.UTF_8);
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = server.openConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
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
}
