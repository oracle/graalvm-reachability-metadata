/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.JdbcConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.PooledConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class WrapperBaseInnerConnectionErrorFiringInvocationHandlerTest {
    @Test
    void unwrappedPooledConnectionWrapsReturnedJdbcInterfaces() throws Exception {
        PooledConnection pool = MysqlTestSupport.pooledConnection();
        try (Connection connection = pool.getConnection()) {
            JdbcConnection vendor = connection.unwrap(JdbcConnection.class);
            try (Statement statement = vendor.createStatement();
                    ResultSet result = statement.executeQuery("SELECT 'wrapped result' AS payload")) {
                assertThat(result.getMetaData().getColumnLabel(1)).isEqualTo("payload");
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("wrapped result");
                assertThat(result.next()).isFalse();
            }
        } finally {
            pool.close();
        }
    }
}
