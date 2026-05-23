/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import org.hsqldb.ColumnSchema;
import org.hsqldb.Routine;
import org.hsqldb.SchemaObject;
import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.result.Result;
import org.hsqldb.types.Type;
import org.junit.jupiter.api.Test;

public class RoutineTest {
    private static volatile String authenticatedUser;

    @Test
    void routineObjectResolvesAndInvokesJavaMethodDirectly() {
        Routine routine = new Routine(SchemaObject.FUNCTION);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader classHidingLoader = new ClassHidingLoader(originalClassLoader, Math.class.getName());

        routine.setLanguage(Routine.LANGUAGE_JAVA);
        routine.setDataImpact(Routine.NO_SQL);
        routine.setReturnType(Type.SQL_INTEGER);
        routine.addParameter(new ColumnSchema(null, Type.SQL_INTEGER, false, false, null));
        routine.setMethodURL("CLASSPATH:java.lang.Math.abs");

        try {
            Thread.currentThread().setContextClassLoader(classHidingLoader);
            routine.resolve(null);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertThat(routine.getMethod()).isNotNull();

        Result result = routine.invokeJavaMethodDirect(new Object[] { Integer.valueOf(-5) });

        assertThat(result.isError()).isFalse();
        assertThat(result.getValueObject()).isEqualTo(5);
    }

    @Test
    void javaFunctionResolvesMethodWithFallbackClassLookup() throws Exception {
        try (Connection connection = openConnection("routine_function")) {
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader classHidingLoader = new ClassHidingLoader(originalClassLoader, Math.class.getName());

            try (Statement statement = connection.createStatement()) {
                Thread.currentThread().setContextClassLoader(classHidingLoader);
                statement.execute("""
                        CREATE FUNCTION ROUTINE_ABS(P1 INT)
                        RETURNS INT
                        LANGUAGE JAVA DETERMINISTIC NO SQL
                        EXTERNAL NAME 'CLASSPATH:java.lang.Math.abs'
                        """);
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("CALL ROUTINE_ABS(-9)")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(9);
            }
        }
    }

    @Test
    void javaProcedureConvertsOutParametersToJavaArrays() throws Exception {
        try (Connection connection = openConnection("routine_procedure")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE PROCEDURE ROUTINE_INCREMENT(IN P1 INT, OUT P2 INT)
                        LANGUAGE JAVA DETERMINISTIC NO SQL
                        EXTERNAL NAME 'CLASSPATH:org_hsqldb.hsqldb.RoutineTest.writeIncremented'
                        """);
            }

            try (CallableStatement statement = connection.prepareCall("CALL ROUTINE_INCREMENT(?, ?)")) {
                statement.setInt(1, 6);
                statement.registerOutParameter(2, Types.INTEGER);

                statement.execute();

                assertThat(statement.getInt(2)).isEqualTo(7);
            }
        }
    }

    @Test
    void databaseAuthenticationFunctionInvokesJavaMethodDirectly() throws Exception {
        String databaseName = "routine_auth_" + Long.toUnsignedString(System.nanoTime());
        String url = "jdbc:hsqldb:mem:" + databaseName + ";shutdown=true";

        authenticatedUser = null;

        try (Connection adminConnection = openConnection(url, "SA", "")) {
            try (Statement statement = adminConnection.createStatement()) {
                statement.execute("""
                        SET DATABASE AUTHENTICATION FUNCTION
                        EXTERNAL NAME 'CLASSPATH:org_hsqldb.hsqldb.RoutineTest.authenticate'
                        """);
            }

            try (Connection userConnection = openConnection(url, "EXTERNAL_USER", "secret")) {
                assertThat(userConnection.isClosed()).isFalse();
            }
        }

        assertThat(authenticatedUser).isEqualTo("EXTERNAL_USER");
    }

    private static Connection openConnection(String databaseName) throws Exception {
        return openConnection("jdbc:hsqldb:mem:" + databaseName + "_" + Long.toUnsignedString(System.nanoTime())
                + ";shutdown=true", "SA", "");
    }

    private static Connection openConnection(String url, String user, String password) throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);

        return dataSource.getConnection();
    }

    public static void writeIncremented(int input, Integer[] output) {
        output[0] = Integer.valueOf(input + 1);
    }

    public static Array authenticate(String database, String user, String password) {
        authenticatedUser = user;

        assertThat(database).isNotBlank();
        assertThat(password).isEqualTo("secret");

        return null;
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
