/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.JdbcStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.PooledConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class StatementWrapperTest {
    @Test
    void pooledStatementUnwrapsToVendorInterfaceAndExecutesQuery() throws Exception {
        PooledConnection pool = MysqlTestSupport.pooledConnection();
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            JdbcStatement vendor = statement.unwrap(JdbcStatement.class);
            assertThat(statement.unwrap(JdbcStatement.class)).isSameAs(vendor);
            vendor.setMaxRows(1);
            assertThat(vendor.getMaxRows()).isEqualTo(1);
            try (ResultSet result = vendor.executeQuery("SELECT 31 UNION ALL SELECT 32")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(31);
                assertThat(result.next()).isFalse();
            }
        } finally {
            pool.close();
        }
    }
}
