/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.hsqldb.auth.AuthBeanMultiplexer;
import org.hsqldb.auth.AuthFunctionBean;
import org.hsqldb.dynamicaccess.RoutineJavaMethods;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RoutineTest {
    private static final String METHOD_CLASS_NAMES_PROPERTY = "hsqldb.method_class_names";
    private static final String TEST_METHOD_PATTERN = "org.hsqldb.*";
    private static final String JAVA_METHOD_CLASS_NAME = RoutineJavaMethods.class.getName();
    private static final String AUTH_METHOD_CLASS_NAME = AuthBeanMultiplexer.class.getName();

    static {
        System.setProperty(METHOD_CLASS_NAMES_PROPERTY, TEST_METHOD_PATTERN);
    }

    @BeforeAll
    static void allowTestJavaRoutines() {
        System.setProperty(METHOD_CLASS_NAMES_PROPERTY, TEST_METHOD_PATTERN);
    }

    @Test
    void invokesScalarJavaFunctionLoadedByFallbackClassForName() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            withPlatformContextClassLoader(() -> statement.execute("""
                    CREATE FUNCTION routine_increment(input_value INTEGER)
                    RETURNS INTEGER
                    LANGUAGE JAVA
                    DETERMINISTIC
                    NO SQL
                    EXTERNAL NAME '%s.increment'
                    """.formatted(JAVA_METHOD_CLASS_NAME)));

            try (ResultSet resultSet = statement.executeQuery("VALUES routine_increment(41)")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(42);
            }
        }
    }

    @Test
    void invokesJavaAggregateWithMutableStateArguments() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            withPlatformContextClassLoader(() -> statement.execute("""
                    CREATE AGGREGATE FUNCTION total_length(
                        input_text VARCHAR(20),
                        finished BOOLEAN,
                        INOUT total INTEGER,
                        INOUT item_count INTEGER)
                    RETURNS INTEGER
                    LANGUAGE JAVA
                    DETERMINISTIC
                    NO SQL
                    EXTERNAL NAME '%s.totalLength'
                    """.formatted(JAVA_METHOD_CLASS_NAME)));
            statement.execute("CREATE TABLE words(word VARCHAR(20))");
            statement.execute("INSERT INTO words(word) VALUES ('alpha')");
            statement.execute("INSERT INTO words(word) VALUES ('beta')");
            statement.execute("INSERT INTO words(word) VALUES ('gamma')");

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT total_length(word) FROM words")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(14);
            }
        }
    }

    @Test
    void invokesJavaAuthenticationFunctionDirectly() throws Exception {
        String databaseName = randomDatabaseName();
        JDBCDataSource dataSource = dataSource(databaseName, "SA", "");

        try (Connection adminConnection = dataSource.getConnection();
                Statement statement = adminConnection.createStatement()) {
            AuthBeanMultiplexer.getSingleton().clear();
            AuthBeanMultiplexer.getSingleton().setAuthFunctionBean(
                    adminConnection,
                    new AcceptingAuthFunctionBean());
            withPlatformContextClassLoader(() -> statement.execute("""
                    SET DATABASE AUTHENTICATION FUNCTION
                    EXTERNAL NAME '%s.authenticate'
                    """.formatted(AUTH_METHOD_CLASS_NAME)));

            JDBCDataSource externalDataSource = dataSource(databaseName, "EXTERNAL_USER", "secret");

            try (Connection externalConnection = externalDataSource.getConnection()) {
                assertThat(externalConnection.isValid(10)).isTrue();
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        return dataSource(randomDatabaseName(), "SA", "").getConnection();
    }

    private static JDBCDataSource dataSource(String databaseName, String user, String password) {
        JDBCDataSource dataSource = new JDBCDataSource();

        dataSource.setUrl("jdbc:hsqldb:mem:" + databaseName + ";shutdown=true");
        dataSource.setUser(user);
        dataSource.setPassword(password);

        return dataSource;
    }

    private static String randomDatabaseName() {
        return "RoutineTest" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void withPlatformContextClassLoader(SqlOperation operation) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();

        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            operation.execute();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    private static final class AcceptingAuthFunctionBean implements AuthFunctionBean {
        @Override
        public String[] authenticate(String userName, String password) throws Exception {
            if ("EXTERNAL_USER".equals(userName) && "secret".equals(password)) {
                return null;
            }

            throw new SQLException("Access denied");
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void execute() throws Exception;
    }
}
