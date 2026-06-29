/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.hsqldb.TriggerDef;
import org.hsqldb.dynamicaccess.RecordingTrigger;
import org.hsqldb.dynamicaccess.RecordingTrigger.Event;
import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.trigger.Trigger;
import org.junit.jupiter.api.Test;

public class TriggerDefTest {
    private static final String RECORDING_TRIGGER_CLASS_NAME = RecordingTrigger.class.getName();
    private static final String DEFAULT_TRIGGER_CLASS_NAME = TriggerDef.class.getName()
            + "$DefaultTrigger";

    @Test
    void createsTriggerInstanceWithContextClassLoader() throws Exception {
        RecordingTrigger.reset();

        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            createTriggerAndInsert(statement, "record_insert_context", "trigger_target_context");
        }

        assertInsertEvent("RECORD_INSERT_CONTEXT", "TRIGGER_TARGET_CONTEXT");
    }

    @Test
    void createsLibraryTriggerInstanceWithContextClassLoader() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            createTriggerAndInsert(
                    statement,
                    "record_insert_default_context",
                    "trigger_target_default_context",
                    DEFAULT_TRIGGER_CLASS_NAME);
            assertSingleRow(statement, "trigger_target_default_context");
        }
    }

    @Test
    void createsLibraryTriggerInstanceAfterContextClassLoaderFallback() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE trigger_target_default_fallback(
                        id INTEGER,
                        label VARCHAR(20))
                    """);

            withPlatformContextClassLoader(() -> createTrigger(
                    statement,
                    "record_insert_default_fallback",
                    "trigger_target_default_fallback",
                    DEFAULT_TRIGGER_CLASS_NAME));

            statement.execute("""
                    INSERT INTO trigger_target_default_fallback(id, label)
                    VALUES (7, 'seven')
                    """);
            assertSingleRow(statement, "trigger_target_default_fallback");
        }
    }

    @Test
    void createsTriggerInstanceAfterContextClassLoaderFallback() throws Exception {
        RecordingTrigger.reset();

        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE trigger_target_fallback(
                        id INTEGER,
                        label VARCHAR(20))
                    """);

            withPlatformContextClassLoader(() -> createTrigger(
                    statement,
                    "record_insert_fallback",
                    "trigger_target_fallback"));

            statement.execute("""
                    INSERT INTO trigger_target_fallback(id, label)
                    VALUES (7, 'seven')
                    """);
        }

        assertInsertEvent("RECORD_INSERT_FALLBACK", "TRIGGER_TARGET_FALLBACK");
    }

    private static void createTriggerAndInsert(
            Statement statement,
            String triggerName,
            String tableName) throws SQLException {

        createTriggerAndInsert(statement, triggerName, tableName, RECORDING_TRIGGER_CLASS_NAME);
    }

    private static void createTriggerAndInsert(
            Statement statement,
            String triggerName,
            String tableName,
            String triggerClassName) throws SQLException {

        statement.execute("""
                CREATE TABLE %s(
                    id INTEGER,
                    label VARCHAR(20))
                """.formatted(tableName));
        createTrigger(statement, triggerName, tableName, triggerClassName);
        statement.execute("INSERT INTO " + tableName + "(id, label) VALUES (7, 'seven')");
    }

    private static void createTrigger(
            Statement statement,
            String triggerName,
            String tableName) throws SQLException {

        createTrigger(statement, triggerName, tableName, RECORDING_TRIGGER_CLASS_NAME);
    }

    private static void createTrigger(
            Statement statement,
            String triggerName,
            String tableName,
            String triggerClassName) throws SQLException {

        statement.execute("""
                CREATE TRIGGER %s
                AFTER INSERT ON %s
                REFERENCING NEW ROW AS new_row
                FOR EACH ROW
                QUEUE 0
                CALL "%s"
                """.formatted(triggerName, tableName, triggerClassName));
    }

    private static void assertSingleRow(Statement statement, String tableName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT COUNT(*) FROM " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }

    private static void assertInsertEvent(String triggerName, String tableName) {
        Event event = RecordingTrigger.lastEvent();

        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo(Trigger.INSERT_AFTER_ROW);
        assertThat(event.triggerName()).isEqualTo(triggerName);
        assertThat(event.tableName()).isEqualTo(tableName);
        assertThat(event.columnNames()).containsExactly("ID", "LABEL");
        assertThat(event.oldRow()).isNull();
        assertThat(event.newRow()).containsExactly(7, "seven");
    }

    private static Connection openConnection() throws SQLException {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl("jdbc:hsqldb:mem:" + randomDatabaseName() + ";shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static String randomDatabaseName() {
        return "TriggerDefTest" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void withPlatformContextClassLoader(SqlOperation operation) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();

        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            operation.execute();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void execute() throws Exception;
    }
}
