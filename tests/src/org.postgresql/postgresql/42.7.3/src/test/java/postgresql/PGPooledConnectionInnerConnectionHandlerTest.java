/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import javax.sql.PooledConnection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGStatement;
import org.postgresql.ds.PGConnectionPoolDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the pooled connection handle that delegates JDBC calls through
 * {@code PGPooledConnection.ConnectionHandler}.
 */
public class PGPooledConnectionInnerConnectionHandlerTest {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static PostgresqlTestContainer container;

    @BeforeAll
    static void beforeAll() throws Exception {
        container = PostgresqlTestContainer.start(DATABASE, USERNAME, PASSWORD);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (container != null) {
            container.close();
        }
    }

    @Test
    void pooledConnectionHandleWrapsCreatedStatementsAndDelegatesConnectionMethods() throws Throwable {
        PGConnectionPoolDataSource dataSource = openDataSource();

        PooledConnection pooledConnection = dataSource.getPooledConnection();
        try (Connection connection = pooledConnection.getConnection()) {
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(connection.unwrap(PGConnection.class).getBackendPID()).isPositive();

            InvocationHandler handler = Proxy.getInvocationHandler(connection);
            Method getClassMethod = Object.class.getMethod("getClass");
            Object delegatedClass = handler.invoke(connection, getClassMethod, null);
            assertThat(Connection.class.isAssignableFrom((Class<?>) delegatedClass)).isTrue();

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertThat(statement).isInstanceOf(PGStatement.class);
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT ?::int")) {
                assertThat(statement).isInstanceOf(PGStatement.class);
                statement.setInt(1, 42);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(42);
                }
            }

            try (CallableStatement statement = connection.prepareCall("{ ? = call lower(?) }")) {
                assertThat(statement).isInstanceOf(PGStatement.class);
                statement.registerOutParameter(1, Types.VARCHAR);
                statement.setString(2, "POOLED");
                statement.execute();
                assertThat(statement.getString(1)).isEqualTo("pooled");
            }
        } finally {
            pooledConnection.close();
        }
    }

    private static PGConnectionPoolDataSource openDataSource() {
        PGConnectionPoolDataSource dataSource = new PGConnectionPoolDataSource();
        dataSource.setServerNames(new String[] {container.host()});
        dataSource.setPortNumbers(new int[] {container.port()});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }
}
