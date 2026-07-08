/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

import org.hsqldb.trigger.Trigger;
import org.junit.jupiter.api.Test;

public class TriggerDefTest {
    private static final AtomicReference<String[]> COLUMN_NAMES = new AtomicReference<>();
    private static final AtomicReference<Object[]> NEW_ROW = new AtomicReference<>();

    @Test
    void invokesJavaTriggerAfterContextClassLoaderFallback() throws Exception {
        COLUMN_NAMES.set(null);
        NEW_ROW.set(null);

        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:trigger_def");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE EVENTS (ID INTEGER, DESCRIPTION VARCHAR(32))");

            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

            try {
                Thread.currentThread().setContextClassLoader(null);
                statement.execute("""
                    CREATE TRIGGER RECORD_INSERT
                    AFTER INSERT ON EVENTS
                    FOR EACH ROW
                    CALL "%s"
                    """.formatted(RecordingTrigger.class.getName()));
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }

            statement.executeUpdate("INSERT INTO EVENTS VALUES (1, 'created')");
        }

        assertThat(COLUMN_NAMES.get()).containsExactly("ID", "DESCRIPTION");
        assertThat(NEW_ROW.get()).containsExactly(1, "created");
    }

    public static final class RecordingTrigger implements Trigger {
        public RecordingTrigger() {}

        @Override
        public void fire(
                int type,
                String triggerName,
                String tableName,
                String[] columnNames,
                Object[] oldRow,
                Object[] newRow) {
            COLUMN_NAMES.set(columnNames);
            NEW_ROW.set(newRow);
        }
    }
}
