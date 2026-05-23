/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.hsqldb.Trigger;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;

public class TriggerDefTest implements Trigger {
    private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
    private static final AtomicInteger FIRE_CALLS = new AtomicInteger();
    private static final AtomicReference<String> TRIGGER_NAME = new AtomicReference<>();
    private static final AtomicReference<String> TABLE_NAME = new AtomicReference<>();
    private static final AtomicReference<Object[]> NEW_ROW = new AtomicReference<>();

    public TriggerDefTest() {
        CONSTRUCTOR_CALLS.incrementAndGet();
    }

    @Test
    void sqlTriggerLoadsImplementationWithContextClassLoader() throws Exception {
        resetRecording();

        try (Connection connection = openConnection("trigger_def_context_loader")) {
            createTriggeredTable(connection);
            createRecordInsertTrigger(connection);
            insertTriggeredRow(connection);
        }

        assertRecordInsertTriggerFired();
    }

    @Test
    void sqlTriggerLoadsImplementationWithFallbackClassLookup() throws Exception {
        resetRecording();

        try (Connection connection = openConnection("trigger_def_fallback_loader")) {
            createTriggeredTable(connection);

            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader classHidingLoader = new ClassHidingLoader(originalClassLoader, TriggerDefTest.class.getName());

            try {
                Thread.currentThread().setContextClassLoader(classHidingLoader);
                createRecordInsertTrigger(connection);
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }

            insertTriggeredRow(connection);
        }

        assertRecordInsertTriggerFired();
    }

    @Override
    public void fire(int type, String trigName, String tabName, Object[] oldRow, Object[] newRow) {
        assertThat(type).isEqualTo(Trigger.INSERT_BEFORE_ROW);
        assertThat(oldRow).isNull();

        TRIGGER_NAME.set(trigName);
        TABLE_NAME.set(tabName);
        NEW_ROW.set(newRow.clone());
        FIRE_CALLS.incrementAndGet();
    }

    private static void resetRecording() {
        CONSTRUCTOR_CALLS.set(0);
        FIRE_CALLS.set(0);
        TRIGGER_NAME.set(null);
        TABLE_NAME.set(null);
        NEW_ROW.set(null);
    }

    private static void assertRecordInsertTriggerFired() {
        assertThat(CONSTRUCTOR_CALLS.get()).isGreaterThanOrEqualTo(1);
        assertThat(FIRE_CALLS.get()).isEqualTo(1);
        assertThat(TRIGGER_NAME.get()).isEqualTo("RECORD_INSERT");
        assertThat(TABLE_NAME.get()).isEqualTo("TRIGGERED_TABLE");
        assertThat(NEW_ROW.get()).containsExactly(7, "seven");
    }

    private static void createTriggeredTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE TRIGGERED_TABLE (ID INT, NAME VARCHAR(20))");
        }
    }

    private static void createRecordInsertTrigger(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER RECORD_INSERT
                    BEFORE INSERT ON TRIGGERED_TABLE
                    FOR EACH ROW
                    CALL "org_hsqldb.hsqldb.TriggerDefTest"
                    """);
        }
    }

    private static void insertTriggeredRow(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO TRIGGERED_TABLE VALUES (7, 'seven')");
        }
    }

    private static Connection openConnection(String databaseName) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl("jdbc:hsqldb:mem:" + databaseName + "_" + Long.toUnsignedString(System.nanoTime())
                + ";shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static final class ClassHidingLoader extends ClassLoader {
        private final String hiddenClassName;

        private ClassHidingLoader(ClassLoader parent, String hiddenClassName) {
            super(parent);
            this.hiddenClassName = hiddenClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (hiddenClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }

            return super.loadClass(name, resolve);
        }
    }
}
