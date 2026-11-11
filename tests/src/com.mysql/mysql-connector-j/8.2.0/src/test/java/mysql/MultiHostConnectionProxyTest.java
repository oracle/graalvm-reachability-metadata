/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class MultiHostConnectionProxyTest {
    @Test
    void multiHostConnectionSupportsDiagnosticsAndJdbcReturnValues() throws Exception {
        try (Connection connection = MysqlTestSupport.connect("loadbalance:", Map.of());
                PreparedStatement statement = connection.prepareStatement("SELECT ? + 1")) {
            assertThat(connection.toString()).contains("LoadBalancedConnectionProxy");
            assertThat(connection.hashCode()).isEqualTo(connection.hashCode());
            statement.setInt(1, 17);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(18);
                assertThat(result.next()).isFalse();
            }
        }
    }
}
