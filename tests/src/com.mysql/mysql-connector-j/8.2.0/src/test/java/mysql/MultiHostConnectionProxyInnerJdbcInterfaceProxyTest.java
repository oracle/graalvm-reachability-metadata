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
import java.sql.ResultSetMetaData;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class MultiHostConnectionProxyInnerJdbcInterfaceProxyTest {
    @Test
    void nestedStatementResultAndMetadataCallsRemainUsable() throws Exception {
        try (Connection connection = MysqlTestSupport.connect("loadbalance:", Map.of());
                PreparedStatement statement = connection.prepareStatement("SELECT ? AS payload")) {
            statement.setString(1, "nested JDBC proxy");
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                assertThat(metadata.getColumnCount()).isEqualTo(1);
                assertThat(metadata.getColumnLabel(1)).isEqualTo("payload");
                assertThat(result.next()).isTrue();
                assertThat(result.getString("payload")).isEqualTo("nested JDBC proxy");
                assertThat(result.next()).isFalse();
            }
        }
    }
}
