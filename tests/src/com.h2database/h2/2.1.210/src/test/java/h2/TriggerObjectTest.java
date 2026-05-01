/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.api.Trigger;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class TriggerObjectTest {
    @Test
    void loadsTriggerClassWithNoArgumentConstructorAndFiresIt() throws Exception {
        RecordingTrigger.reset();

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:trigger_class")) {
            execute(connection, "CREATE TABLE test(id INTEGER PRIMARY KEY)");
            execute(connection, """
                    CREATE TRIGGER class_trigger
                    AFTER INSERT ON test
                    FOR EACH ROW
                    CALL 'h2.TriggerObjectTest$RecordingTrigger'
                    """);
            execute(connection, "INSERT INTO test VALUES (42)");
        }

        assertThat(RecordingTrigger.constructed()).isEqualTo(1);
        assertThat(RecordingTrigger.initializedTrigger()).isEqualTo("CLASS_TRIGGER");
        assertThat(RecordingTrigger.initializedTable()).isEqualTo("TEST");
        assertThat(RecordingTrigger.initializedBefore()).isFalse();
        assertThat(RecordingTrigger.initializedType()).isEqualTo(Trigger.INSERT);
        assertThat(RecordingTrigger.fireCount()).isEqualTo(1);
        assertThat(RecordingTrigger.lastOldRow()).isNull();
        assertThat(RecordingTrigger.lastNewRow()).containsExactly(42);
    }

    @Test
    void compilesTriggerSourceAndInvokesFactoryMethod() throws Exception {
        SourceTrigger.reset();

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:trigger_source")) {
            execute(connection, "CREATE TABLE test(id INTEGER PRIMARY KEY)");
            boolean sourceCompiled = runDynamicCompilationIfSupported(() -> {
                execute(connection, """
                        CREATE TRIGGER source_trigger
                        AFTER INSERT ON test
                        FOR EACH ROW
                        AS 'org.h2.api.Trigger create() { return new h2.TriggerObjectTest.SourceTrigger(); }'
                        """);
                execute(connection, "INSERT INTO test VALUES (7)");
            });

            if (sourceCompiled) {
                assertThat(SourceTrigger.constructed()).isEqualTo(1);
                assertThat(SourceTrigger.initializedTrigger()).isEqualTo("SOURCE_TRIGGER");
                assertThat(SourceTrigger.fireCount()).isEqualTo(1);
                assertThat(SourceTrigger.lastNewRow()).containsExactly(7);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean runDynamicCompilationIfSupported(DynamicCompilationAssertion assertion) throws Exception {
        try {
            assertion.run();
            return true;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return false;
        } catch (SQLException exception) {
            if (!containsUnsupportedFeatureError(exception)) {
                throw exception;
            }
            return false;
        }
    }

    private static boolean containsUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private interface DynamicCompilationAssertion {
        void run() throws Exception;
    }

    public static final class RecordingTrigger implements Trigger {
        private static final AtomicInteger CONSTRUCTED = new AtomicInteger();
        private static final AtomicInteger FIRE_COUNT = new AtomicInteger();
        private static final AtomicReference<String> INITIALIZED_TRIGGER = new AtomicReference<>();
        private static final AtomicReference<String> INITIALIZED_TABLE = new AtomicReference<>();
        private static final AtomicReference<Boolean> INITIALIZED_BEFORE = new AtomicReference<>();
        private static final AtomicReference<Integer> INITIALIZED_TYPE = new AtomicReference<>();
        private static final AtomicReference<Object[]> LAST_OLD_ROW = new AtomicReference<>();
        private static final AtomicReference<Object[]> LAST_NEW_ROW = new AtomicReference<>();

        public RecordingTrigger() {
            CONSTRUCTED.incrementAndGet();
        }

        @Override
        public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before,
                int type) {
            INITIALIZED_TRIGGER.set(triggerName);
            INITIALIZED_TABLE.set(tableName);
            INITIALIZED_BEFORE.set(before);
            INITIALIZED_TYPE.set(type);
        }

        @Override
        public void fire(Connection conn, Object[] oldRow, Object[] newRow) {
            FIRE_COUNT.incrementAndGet();
            LAST_OLD_ROW.set(oldRow);
            LAST_NEW_ROW.set(newRow);
        }

        private static void reset() {
            CONSTRUCTED.set(0);
            FIRE_COUNT.set(0);
            INITIALIZED_TRIGGER.set(null);
            INITIALIZED_TABLE.set(null);
            INITIALIZED_BEFORE.set(null);
            INITIALIZED_TYPE.set(null);
            LAST_OLD_ROW.set(null);
            LAST_NEW_ROW.set(null);
        }

        private static int constructed() {
            return CONSTRUCTED.get();
        }

        private static int fireCount() {
            return FIRE_COUNT.get();
        }

        private static String initializedTrigger() {
            return INITIALIZED_TRIGGER.get();
        }

        private static String initializedTable() {
            return INITIALIZED_TABLE.get();
        }

        private static boolean initializedBefore() {
            return INITIALIZED_BEFORE.get();
        }

        private static int initializedType() {
            return INITIALIZED_TYPE.get();
        }

        private static Object[] lastOldRow() {
            return LAST_OLD_ROW.get();
        }

        private static Object[] lastNewRow() {
            return LAST_NEW_ROW.get();
        }
    }

    public static final class SourceTrigger implements Trigger {
        private static final AtomicInteger CONSTRUCTED = new AtomicInteger();
        private static final AtomicInteger FIRE_COUNT = new AtomicInteger();
        private static final AtomicReference<String> INITIALIZED_TRIGGER = new AtomicReference<>();
        private static final AtomicReference<Object[]> LAST_NEW_ROW = new AtomicReference<>();

        public SourceTrigger() {
            CONSTRUCTED.incrementAndGet();
        }

        @Override
        public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before,
                int type) {
            INITIALIZED_TRIGGER.set(triggerName);
        }

        @Override
        public void fire(Connection conn, Object[] oldRow, Object[] newRow) {
            FIRE_COUNT.incrementAndGet();
            LAST_NEW_ROW.set(newRow);
        }

        private static void reset() {
            CONSTRUCTED.set(0);
            FIRE_COUNT.set(0);
            INITIALIZED_TRIGGER.set(null);
            LAST_NEW_ROW.set(null);
        }

        private static int constructed() {
            return CONSTRUCTED.get();
        }

        private static int fireCount() {
            return FIRE_COUNT.get();
        }

        private static String initializedTrigger() {
            return INITIALIZED_TRIGGER.get();
        }

        private static Object[] lastNewRow() {
            return LAST_NEW_ROW.get();
        }
    }
}
