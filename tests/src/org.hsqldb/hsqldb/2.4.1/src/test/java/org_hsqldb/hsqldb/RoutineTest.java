/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class RoutineTest {
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    @Test
    public void resolvesAndInvokesLibraryJavaFunctionDeclaredThroughSql() throws Exception {
        try (Connection connection = openConnection("libraryfunction")) {
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(
                    new RejectingClassLoader(originalClassLoader, Math.class.getName()));

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE FUNCTION ABS_VALUE(INT) RETURNS INT "
                        + "NO SQL LANGUAGE JAVA EXTERNAL NAME 'CLASSPATH:java.lang.Math.abs'");
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("CALL ABS_VALUE(-9)")) {
                assertTrue(resultSet.next());
                assertEquals(9, resultSet.getInt(1));
            }
        }
    }

    @Test
    public void invokesPublicStaticJavaMethodThroughRoutineCallSyntax() throws Exception {
        try (Connection connection = openConnection("directroutine")) {
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(
                    new RejectingClassLoader(originalClassLoader, Math.class.getName()));

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("CALL \"java.lang.Math.abs\"(-14)")) {
                assertTrue(resultSet.next());
                assertEquals(14, resultSet.getInt(1));
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    @Test
    public void invokesJavaFunctionDeclaredThroughSql() throws Exception {
        try (Connection connection = openConnection("function")) {
            String helperClassName = RoutineMethods.class.getName();
            ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(
                    new RejectingClassLoader(originalClassLoader, helperClassName));

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE FUNCTION ADD_MARKER(VARCHAR(40)) RETURNS VARCHAR(80) "
                        + "NO SQL LANGUAGE JAVA EXTERNAL NAME 'CLASSPATH:" + helperClassName + ".addMarker'");
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("CALL ADD_MARKER('routine')")) {
                assertTrue(resultSet.next());
                assertEquals("routine-invoked", resultSet.getString(1));
            }
        }
    }

    @Test
    public void invokesJavaProcedureWithInOutArgumentDeclaredThroughSql() throws Exception {
        try (Connection connection = openConnection("procedure")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE PROCEDURE ACCUMULATE_VALUE(IN LEFT_VALUE INT, INOUT TOTAL_VALUE INT) "
                        + "NO SQL LANGUAGE JAVA EXTERNAL NAME 'CLASSPATH:" + RoutineMethods.class.getName()
                        + ".accumulateValue'");
            }

            try (CallableStatement statement = connection.prepareCall("CALL ACCUMULATE_VALUE(?, ?)")) {
                statement.setInt(1, 7);
                statement.setInt(2, 5);
                statement.registerOutParameter(2, Types.INTEGER);
                statement.execute();

                assertEquals(12, statement.getInt(2));
            }
        }
    }

    @Test
    public void invokesJavaAuthenticationRoutineDirectlyDuringLogin() throws Exception {
        String databaseName = nextDatabaseName("authentication");
        String url = "jdbc:hsqldb:mem:" + databaseName;

        try (Connection setupConnection = DriverManager.getConnection(url, "SA", "")) {
            try (Statement statement = setupConnection.createStatement()) {
                statement.execute("SET DATABASE AUTHENTICATION FUNCTION EXTERNAL NAME 'CLASSPATH:"
                        + RoutineMethods.class.getName() + ".authenticate'");
            }

            try (Connection authenticatedConnection = DriverManager.getConnection(url, "external_user", "secret")) {
                assertNotNull(authenticatedConnection);
                assertEquals("external_user", authenticatedConnection.getMetaData().getUserName());
            }
        }
    }

    private static Connection openConnection(String prefix) throws Exception {
        return DriverManager.getConnection("jdbc:hsqldb:mem:" + nextDatabaseName(prefix), "SA", "");
    }

    private static String nextDatabaseName(String prefix) {
        return prefix + DATABASE_COUNTER.incrementAndGet();
    }

    public static final class RoutineMethods {
        private RoutineMethods() {
        }

        public static String addMarker(String value) {
            return value + "-invoked";
        }

        public static void accumulateValue(int leftValue, Integer[] totalValue) {
            totalValue[0] = leftValue + totalValue[0];
        }

        public static Array authenticate(String databaseName, String userName, String password) {
            if (!"secret".equals(password)) {
                throw new IllegalArgumentException("Unexpected password for " + userName + " in " + databaseName);
            }

            return new StringArray(new String[0]);
        }
    }

    private static final class StringArray implements Array {
        private final String[] values;

        private StringArray(String[] values) {
            this.values = values;
        }

        @Override
        public String getBaseTypeName() {
            return "VARCHAR";
        }

        @Override
        public int getBaseType() {
            return Types.VARCHAR;
        }

        @Override
        public Object getArray() {
            return values.clone();
        }

        @Override
        public Object getArray(Map<String, Class<?>> map) {
            return getArray();
        }

        @Override
        public Object getArray(long index, int count) {
            String[] slice = new String[count];
            System.arraycopy(values, (int) index - 1, slice, 0, count);
            return slice;
        }

        @Override
        public Object getArray(long index, int count, Map<String, Class<?>> map) {
            return getArray(index, count);
        }

        @Override
        public ResultSet getResultSet() throws SQLException {
            throw new SQLFeatureNotSupportedException("ResultSet view is not used by HSQLDB conversion");
        }

        @Override
        public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
            return getResultSet();
        }

        @Override
        public ResultSet getResultSet(long index, int count) throws SQLException {
            return getResultSet();
        }

        @Override
        public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException {
            return getResultSet();
        }

        @Override
        public void free() {
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
