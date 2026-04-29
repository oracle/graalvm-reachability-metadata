/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.hsqldb.Trigger;
import org.junit.jupiter.api.Test;

public class TriggerDefTest {
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    @Test
    public void createsAndInvokesSqlTriggerLoadedFromClassName() throws Exception {
        RecordingTrigger.reset();

        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE audited_event (id INT PRIMARY KEY, description VARCHAR(40))");

                ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(
                        new RejectingClassLoader(originalClassLoader, RecordingTrigger.class.getName()));

                try {
                    statement.execute("CREATE TRIGGER audit_event_insert AFTER INSERT ON audited_event "
                            + "FOR EACH ROW QUEUE 0 CALL \"" + RecordingTrigger.class.getName() + "\"");
                } finally {
                    Thread.currentThread().setContextClassLoader(originalClassLoader);
                }

                statement.executeUpdate("INSERT INTO audited_event VALUES (7, 'created')");
            }
        }

        assertEquals(1, RecordingTrigger.invocationCount());
        assertEquals(Trigger.INSERT_AFTER_ROW, RecordingTrigger.lastType());
        assertEquals("AUDIT_EVENT_INSERT", RecordingTrigger.lastTriggerName());
        assertEquals("AUDITED_EVENT", RecordingTrigger.lastTableName());
        assertArrayEquals(new Object[] { Integer.valueOf(7), "created" }, RecordingTrigger.lastNewRow());
    }

    private static Connection openConnection() throws Exception {
        return DriverManager.getConnection("jdbc:hsqldb:mem:triggerdef" + DATABASE_COUNTER.incrementAndGet(), "SA", "");
    }

    public static final class RecordingTrigger implements Trigger {
        private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();
        private static final AtomicInteger LAST_TYPE = new AtomicInteger();
        private static final AtomicReference<String> LAST_TRIGGER_NAME = new AtomicReference<>();
        private static final AtomicReference<String> LAST_TABLE_NAME = new AtomicReference<>();
        private static final AtomicReference<Object[]> LAST_NEW_ROW = new AtomicReference<>();

        public RecordingTrigger() {
        }

        @Override
        public void fire(int type, String triggerName, String tableName, Object[] oldRow, Object[] newRow) {
            INVOCATION_COUNT.incrementAndGet();
            LAST_TYPE.set(type);
            LAST_TRIGGER_NAME.set(triggerName);
            LAST_TABLE_NAME.set(tableName);
            LAST_NEW_ROW.set(newRow == null ? null : newRow.clone());
        }

        private static void reset() {
            INVOCATION_COUNT.set(0);
            LAST_TYPE.set(-1);
            LAST_TRIGGER_NAME.set(null);
            LAST_TABLE_NAME.set(null);
            LAST_NEW_ROW.set(null);
        }

        private static int invocationCount() {
            return INVOCATION_COUNT.get();
        }

        private static int lastType() {
            return LAST_TYPE.get();
        }

        private static String lastTriggerName() {
            return LAST_TRIGGER_NAME.get();
        }

        private static String lastTableName() {
            return LAST_TABLE_NAME.get();
        }

        private static Object[] lastNewRow() {
            Object[] row = LAST_NEW_ROW.get();
            assertNotNull(row);

            return row;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }

            return super.loadClass(name, resolve);
        }
    }
}
