/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.StatementFacade;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class StatementFacadeTest {

    @Test
    void wrapsCreatedStatementsWithStatementFacadeProxy() throws Exception {
        TestStatementFacade statementFacade = new TestStatementFacade();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:statement_facade");
                Statement delegate = connection.createStatement()) {
            Statement statement = statementFacade.wrapCreatedStatement(delegate);
            statement.executeUpdate("""
                    CREATE TABLE statement_facade_item (
                        id INT NOT NULL PRIMARY KEY,
                        name VARCHAR(255)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO statement_facade_item(id, name)
                    VALUES (1, 'facade-statement')
                    """);

            assertInsertedItem(statement);
            assertThat(statement.toString()).contains("StatementFacade");
        }
    }

    private void assertInsertedItem(Statement statement) throws SQLException {
        String query = "SELECT name FROM statement_facade_item WHERE id = 1";
        try (ResultSet resultSet = statement.executeQuery(query)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("facade-statement");
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static final class TestStatementFacade extends StatementFacade {
        private TestStatementFacade() {
            super(new TerminalJdbcInterceptor());
        }

        private Statement wrapCreatedStatement(Statement statement) throws NoSuchMethodException {
            Method method = Connection.class.getMethod("createStatement");
            return (Statement) createStatement(null, method, new Object[0], statement, 0);
        }
    }

    private static final class TerminalJdbcInterceptor extends JdbcInterceptor {
        @Override
        public void reset(ConnectionPool parent, PooledConnection connection) {
            // No state is held by this terminal interceptor.
        }
    }
}
