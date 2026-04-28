/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.Trigger;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TriggerObjectTest {
    private static final String CLASS_TRIGGER_URL = "jdbc:h2:mem:trigger-object-class";
    private static final String SOURCE_TRIGGER_URL = "jdbc:h2:mem:trigger-object-source";

    @Test
    void createsTriggerCallbackFromConfiguredClassName() throws Exception {
        RecordingTrigger.reset();

        try (Connection connection = DriverManager.getConnection(CLASS_TRIGGER_URL);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE trigger_events(id INT PRIMARY KEY, name VARCHAR)");
            statement.execute("""
                    CREATE TRIGGER class_trigger
                    AFTER INSERT ON trigger_events
                    FOR EACH ROW
                    CALL 'h2.TriggerObjectTest$RecordingTrigger'
                    """);
            statement.execute("INSERT INTO trigger_events VALUES (1, 'alpha'), (2, 'bravo')");
        }

        assertThat(RecordingTrigger.constructorCount.get()).isEqualTo(1);
        assertThat(RecordingTrigger.initCount.get()).isEqualTo(1);
        assertThat(RecordingTrigger.fireCount.get()).isEqualTo(2);
        assertThat(RecordingTrigger.lastSchemaName).isEqualTo("PUBLIC");
        assertThat(RecordingTrigger.lastTriggerName).isEqualTo("CLASS_TRIGGER");
        assertThat(RecordingTrigger.lastTableName).isEqualTo("TRIGGER_EVENTS");
        assertThat(RecordingTrigger.lastBeforeFlag).isFalse();
        assertThat(RecordingTrigger.lastTypeMask).isEqualTo(Trigger.INSERT);
    }

    @Test
    void createsTriggerCallbackFromSourceMethod() throws Exception {
        SourceBackedTrigger.reset();
        String triggerSource = """
                org.h2.api.Trigger create() {
                    return new %s();
                }
                """.formatted(SourceBackedTrigger.class.getCanonicalName());

        try {
            try (Connection connection = DriverManager.getConnection(SOURCE_TRIGGER_URL);
                    Statement statement = connection.createStatement()) {
                statement.execute("DROP ALL OBJECTS");
                statement.execute("CREATE TABLE trigger_events(id INT PRIMARY KEY, name VARCHAR)");
                statement.execute("""
                        CREATE TRIGGER source_trigger
                        AFTER UPDATE ON trigger_events
                        FOR EACH ROW
                        AS '%s'
                        """.formatted(escapeSql(triggerSource)));
                statement.execute("INSERT INTO trigger_events VALUES (1, 'alpha')");
                statement.execute("UPDATE trigger_events SET name = 'bravo' WHERE id = 1");
            }
        } catch (SQLException ex) {
            if (hasUnsupportedRuntimeClassDefinitionCause(ex)) {
                return;
            }
            throw ex;
        }

        assertThat(SourceBackedTrigger.constructorCount.get()).isEqualTo(1);
        assertThat(SourceBackedTrigger.initCount.get()).isEqualTo(1);
        assertThat(SourceBackedTrigger.fireCount.get()).isEqualTo(1);
        assertThat(SourceBackedTrigger.lastTriggerName).isEqualTo("SOURCE_TRIGGER");
        assertThat(SourceBackedTrigger.lastTypeMask).isEqualTo(Trigger.UPDATE);
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private static boolean hasUnsupportedRuntimeClassDefinitionCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("Defining new classes at runtime is not supported")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static class RecordingTrigger implements Trigger {
        static final AtomicInteger constructorCount = new AtomicInteger();
        static final AtomicInteger initCount = new AtomicInteger();
        static final AtomicInteger fireCount = new AtomicInteger();
        static volatile String lastSchemaName;
        static volatile String lastTriggerName;
        static volatile String lastTableName;
        static volatile boolean lastBeforeFlag;
        static volatile int lastTypeMask;

        public RecordingTrigger() {
            constructorCount.incrementAndGet();
        }

        static void reset() {
            constructorCount.set(0);
            initCount.set(0);
            fireCount.set(0);
            lastSchemaName = null;
            lastTriggerName = null;
            lastTableName = null;
            lastBeforeFlag = true;
            lastTypeMask = 0;
        }

        @Override
        public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before,
                int type) {
            initCount.incrementAndGet();
            lastSchemaName = schemaName;
            lastTriggerName = triggerName;
            lastTableName = tableName;
            lastBeforeFlag = before;
            lastTypeMask = type;
        }

        @Override
        public void fire(Connection conn, Object[] oldRow, Object[] newRow) {
            assertThat(oldRow).isNull();
            assertThat(newRow).hasSize(2);
            fireCount.incrementAndGet();
        }
    }

    public static class SourceBackedTrigger implements Trigger {
        static final AtomicInteger constructorCount = new AtomicInteger();
        static final AtomicInteger initCount = new AtomicInteger();
        static final AtomicInteger fireCount = new AtomicInteger();
        static volatile String lastTriggerName;
        static volatile int lastTypeMask;

        public SourceBackedTrigger() {
            constructorCount.incrementAndGet();
        }

        static void reset() {
            constructorCount.set(0);
            initCount.set(0);
            fireCount.set(0);
            lastTriggerName = null;
            lastTypeMask = 0;
        }

        @Override
        public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before,
                int type) {
            initCount.incrementAndGet();
            lastTriggerName = triggerName;
            lastTypeMask = type;
        }

        @Override
        public void fire(Connection conn, Object[] oldRow, Object[] newRow) {
            assertThat(oldRow).hasSize(2);
            assertThat(newRow).hasSize(2);
            fireCount.incrementAndGet();
        }
    }
}
