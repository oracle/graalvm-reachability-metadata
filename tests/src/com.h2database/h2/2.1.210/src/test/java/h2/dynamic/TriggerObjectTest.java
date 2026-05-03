/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class TriggerObjectTest {
    @Test
    void createsTriggerFromJavaSource() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:trigger-source;DB_CLOSE_DELAY=-1");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS trigger_events");
            statement.execute("CREATE TABLE trigger_events(id INT)");
            statement.execute("""
                    CREATE TRIGGER source_trigger BEFORE INSERT ON trigger_events AS $$
                    org.h2.api.Trigger create() {
                        return new org.h2.api.Trigger() {
                            public void init(java.sql.Connection conn, String schemaName, String triggerName,
                                    String tableName, boolean before, int type) {
                            }
                            public void fire(java.sql.Connection conn, Object[] oldRow, Object[] newRow) {
                                newRow[0] = Integer.valueOf(7);
                            }
                            public void close() {
                            }
                            public void remove() {
                            }
                        };
                    }
                    $$
                    """);
            statement.execute("INSERT INTO trigger_events VALUES (1)");
        } catch (SQLException exception) {
            Error unsupportedFeatureError = findUnsupportedFeatureError(exception);
            if (unsupportedFeatureError == null) {
                throw exception;
            }
        }
    }

    private static Error findUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return error;
            }
            current = current.getCause();
        }
        return null;
    }
}
