/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class ConnectionDialogSwingTest {
    @Test
    public void createsJdbcConnectionUsingConfiguredDriverClass() throws Exception {
        String databaseName = "connection_dialog_swing_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:hsqldb:mem:" + databaseName;

        try (Connection connection = createConnection("org.hsqldb.jdbc.JDBCDriver", url, "SA", "")) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("VALUES 42")) {
                assertTrue(resultSet.next());
                assertEquals(42, resultSet.getInt(1));
            }
        }
    }

    private static Connection createConnection(String driver, String url, String user, String password) throws Exception {
        Class<?> connectionDialogClass = Class.forName("org.hsqldb.util.ConnectionDialogSwing");
        Method createConnection = connectionDialogClass.getDeclaredMethod("createConnection", String.class, String.class,
                String.class, String.class);
        createConnection.setAccessible(true);

        try {
            return (Connection) createConnection.invoke(null, driver, url, user, password);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();

            if (cause instanceof Exception) {
                throw (Exception) cause;
            }

            if (cause instanceof Error) {
                throw (Error) cause;
            }

            throw new AssertionError(cause);
        }
    }
}
