/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.interceptor.StatementDecoratorInterceptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class StatementDecoratorInterceptorTest {

    @Test
    void statementDecoratorWrapsStatementResultSets() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:statement_decorator");
                Statement statement = connection.createStatement()) {
            createTestTable(statement);

            Statement decoratedStatement = decorateStatement(connection, statement);
            assertDecoratedResultSet(decoratedStatement);
        }
    }

    private Statement decorateStatement(Connection connection, Statement statement) throws Exception {
        TestStatementDecoratorInterceptor interceptor = new TestStatementDecoratorInterceptor();
        return interceptor.decorate(connection, statement);
    }

    private void createTestTable(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE TABLE decorated_item (id INT NOT NULL PRIMARY KEY, name VARCHAR(255))");
        statement.executeUpdate("INSERT INTO decorated_item(id, name) VALUES (1, 'proxy-result')");
    }

    private void assertDecoratedResultSet(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT name FROM decorated_item WHERE id = 1")) {
            assertThat(resultSet.getStatement()).isSameAs(statement);
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("proxy-result");
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static final class TestStatementDecoratorInterceptor extends StatementDecoratorInterceptor {
        private Statement decorate(Connection connection, Statement statement)
                throws InstantiationException, IllegalAccessException, InvocationTargetException,
                NoSuchMethodException {
            return (Statement) createDecorator(
                    connection,
                    null,
                    null,
                    statement,
                    getConstructor(CREATE_STATEMENT_IDX, Statement.class),
                    null);
        }
    }
}
