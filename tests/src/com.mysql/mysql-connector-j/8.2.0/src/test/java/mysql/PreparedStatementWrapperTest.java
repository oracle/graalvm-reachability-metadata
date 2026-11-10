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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class PreparedStatementWrapperTest {
    @Test
    void pooledPreparedStatementBindsValuesThroughUnwrappedVendorInterface() throws Exception {
        PooledConnection pool = MysqlTestSupport.pooledConnection();
        try (Connection connection = pool.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT ? AS value")) {
            JdbcPreparedStatement vendor = statement.unwrap(JdbcPreparedStatement.class);
            assertThat(statement.unwrap(JdbcPreparedStatement.class)).isSameAs(vendor);
            assertThat(vendor.getPreparedSql()).isEqualTo("SELECT ? AS value");
            vendor.setString(1, "pooled binding");
            try (ResultSet result = vendor.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("pooled binding");
                assertThat(result.next()).isFalse();
            }
        } finally {
            pool.close();
        }
    }
}
