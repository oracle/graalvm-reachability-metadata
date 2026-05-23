/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ConnectionDialogSwingTest {
    private static final String CONNECTION_DIALOG_SWING_CLASS_NAME = "org.hsqldb.util.ConnectionDialogSwing";
    private static final String HSQLDB_DRIVER_CLASS_NAME = "org.hsqldb.jdbc.JDBCDriver";

    @Test
    @Timeout(30)
    void staticConnectionFactoryLoadsDriverAndOpensConnection() throws Throwable {
        String databaseName = "connection_dialog_swing_" + Long.toUnsignedString(System.nanoTime());
        String url = "jdbc:hsqldb:mem:" + databaseName;
        MethodHandle createConnection = createConnectionMethodHandle();

        try (Connection connection = (Connection) createConnection.invoke(HSQLDB_DRIVER_CLASS_NAME, url, "SA", "")) {
            assertThat(connection.isValid(2)).isTrue();
        }

        try (Connection connection = DriverManager.getConnection(url + ";ifexists=true", "SA", "")) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }

    private static MethodHandle createConnectionMethodHandle() throws Exception {
        Class<?> connectionDialogSwingClass = Class.forName(CONNECTION_DIALOG_SWING_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                connectionDialogSwingClass,
                MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(
                Connection.class,
                String.class,
                String.class,
                String.class,
                String.class);

        return lookup.findStatic(connectionDialogSwingClass, "createConnection", methodType);
    }
}
