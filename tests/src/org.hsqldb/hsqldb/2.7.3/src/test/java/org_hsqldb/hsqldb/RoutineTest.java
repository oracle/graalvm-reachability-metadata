/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

import org.hsqldb.jdbc.JDBCArrayBasic;
import org.hsqldb.types.Type;
import org.junit.jupiter.api.Test;

public class RoutineTest {
    private static final String CLASS_NAME = RoutineTest.class.getName();
    private static final AtomicReference<String> AUTHENTICATED_USER = new AtomicReference<>();

    @Test
    void invokesJavaFunctionAfterContextClassLoaderFallback() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:routine_function");
                Statement statement = connection.createStatement()) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

            try {
                Thread.currentThread().setContextClassLoader(null);
                statement.execute("""
                    CREATE FUNCTION DOUBLE_VALUE(INPUT_VALUE INTEGER)
                    RETURNS INTEGER
                    LANGUAGE JAVA
                    DETERMINISTIC
                    NO SQL
                    EXTERNAL NAME 'CLASSPATH:%s.doubleValue'
                    """.formatted(CLASS_NAME));
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }

            try (ResultSet result = statement.executeQuery("VALUES DOUBLE_VALUE(21)")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(42);
            }
        }
    }

    @Test
    void invokesJavaAggregateWithInOutState() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:routine_aggregate");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE AGGREGATE_VALUES (INPUT_VALUE INTEGER)
                """);
            statement.execute("""
                INSERT INTO AGGREGATE_VALUES VALUES (2), (3), (5)
                """);
            statement.execute("""
                CREATE AGGREGATE FUNCTION TOTAL_VALUE(
                    IN INPUT_VALUE INTEGER,
                    IN FINAL_CALL BOOLEAN,
                    INOUT TOTAL INTEGER,
                    INOUT RESULT_VALUE INTEGER)
                RETURNS INTEGER
                LANGUAGE JAVA
                NO SQL
                EXTERNAL NAME 'CLASSPATH:%s.aggregateTotal'
                """.formatted(CLASS_NAME));

            try (ResultSet result = statement.executeQuery(
                    "SELECT TOTAL_VALUE(INPUT_VALUE) FROM AGGREGATE_VALUES")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(10);
            }
        }
    }

    @Test
    void invokesConfiguredAuthenticationFunctionDirectly() throws Exception {
        AUTHENTICATED_USER.set(null);

        try (Connection administrator = DriverManager.getConnection("jdbc:hsqldb:mem:routine_authentication");
                Statement statement = administrator.createStatement()) {
            statement.execute("""
                SET DATABASE AUTHENTICATION FUNCTION
                EXTERNAL NAME 'CLASSPATH:%s.authenticate'
                """.formatted(CLASS_NAME));

            try (Connection externalUser = DriverManager.getConnection(
                    "jdbc:hsqldb:mem:routine_authentication", "routine_user", "secret")) {
                assertThat(externalUser.isValid(10)).isTrue();
            } finally {
                statement.execute("SET DATABASE AUTHENTICATION FUNCTION NONE");
            }
        }

        assertThat(AUTHENTICATED_USER.get()).isEqualTo("routine_user");
    }

    public static Integer doubleValue(Integer value) {
        return value * 2;
    }

    public static Integer aggregateTotal(
            Integer value,
            Boolean finalCall,
            Integer[] total,
            Integer[] result) {
        int currentTotal = total[0] == null ? 0 : total[0];

        if (Boolean.TRUE.equals(finalCall)) {
            result[0] = currentTotal;

            return currentTotal;
        }

        total[0] = currentTotal + value;

        return total[0];
    }

    public static Array authenticate(String databaseName, String userName, String password) {
        AUTHENTICATED_USER.set(userName);

        return new JDBCArrayBasic(new Object[0], Type.SQL_VARCHAR);
    }
}
