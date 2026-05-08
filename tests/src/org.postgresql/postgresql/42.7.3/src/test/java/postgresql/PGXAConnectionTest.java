/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.XAConnection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.xa.PGXADataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic proxy creation in {@code org.postgresql.xa.PGXAConnection}.
 */
public class PGXAConnectionTest {

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
    void getConnectionReturnsXaGuardedProxyImplementingJdbcAndPostgresqlInterfaces() throws Exception {
        XAConnection xaConnection = openDataSource().getXAConnection();
        try {
            try (Connection connection = xaConnection.getConnection()) {
                assertThat(connection).isInstanceOf(PGConnection.class);
                assertThat(connection.getAutoCommit()).isTrue();
                assertThat(((PGConnection) connection).getBackendPID()).isPositive();

                try (Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(1);
                }
            }
        } finally {
            xaConnection.close();
        }
    }

    private static PGXADataSource openDataSource() {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setServerNames(new String[] {container.host()});
        dataSource.setPortNumbers(new int[] {container.port()});
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }
}
