/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class TriggerObjectTest {
    @Test
    void compilesAndInvokesTriggerDefinedAsJavaSource() throws Exception {
        try {
            try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:sourceTrigger;DB_CLOSE_DELAY=-1");
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE test(id INT, name VARCHAR)");
                statement.execute("""
                        CREATE TRIGGER uppercase_name BEFORE INSERT ON test FOR EACH ROW AS '
                        org.h2.api.Trigger create() {
                            return new org.h2.api.Trigger() {
                                public void fire(java.sql.Connection conn, Object[] oldRow, Object[] newRow) {
                                    newRow[1] = ((String) newRow[1]).toUpperCase(java.util.Locale.ROOT);
                                }
                            };
                        }
                        '
                        """);
                statement.execute("INSERT INTO test VALUES (1, 'triggered')");
                try (ResultSet resultSet = statement.executeQuery("SELECT name FROM test")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString(1)).isEqualTo("TRIGGERED");
                }
            }
        } catch (Throwable throwable) {
            rethrowUnlessNativeImageDynamicClassLoadingError(throwable);
        }
    }

    private static void rethrowUnlessNativeImageDynamicClassLoadingError(Throwable throwable) throws Exception {
        if (containsNativeImageDynamicClassLoadingError(throwable)) {
            return;
        }
        if (throwable instanceof Exception exception) {
            throw exception;
        }
        throw (Error) throwable;
    }

    private static boolean containsNativeImageDynamicClassLoadingError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
