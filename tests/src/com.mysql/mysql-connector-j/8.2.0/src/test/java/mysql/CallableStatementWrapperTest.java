/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.JdbcPreparedStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.PooledConnection;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class CallableStatementWrapperTest {
    @Test
    void pooledCallableStatementUnwrapsAndCallsStoredProcedure() throws Exception {
        PooledConnection pool = MysqlTestSupport.pooledConnection();
        try (Connection connection = pool.getConnection(); Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE PROCEDURE connector_echo(IN input_value INT) SELECT input_value AS value");
            try (CallableStatement statement = connection.prepareCall("{call connector_echo(?)}")) {
                JdbcPreparedStatement vendor = statement.unwrap(JdbcPreparedStatement.class);
                assertThat(statement.unwrap(JdbcPreparedStatement.class)).isSameAs(vendor);
                vendor.setInt(1, 67);
                try (ResultSet result = vendor.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("value")).isEqualTo(67);
                    assertThat(result.next()).isFalse();
                }
            } finally {
                ddl.execute("DROP PROCEDURE connector_echo");
            }
        } finally {
            pool.close();
        }
    }
}
