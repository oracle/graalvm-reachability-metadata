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
public class ResultSetScannerInterceptorAnonymous1Test {
    @Test
    void scannerProxyDelegatesCursorAndColumnAccess() throws Exception {
        Map<String, String> properties = Map.of(
                "queryInterceptors", "com.mysql.cj.jdbc.interceptors.ResultSetScannerInterceptor",
                "resultSetScannerRegex", "forbidden");
        try (Connection connection = MysqlTestSupport.connect("", properties);
                PreparedStatement statement = connection.prepareStatement("SELECT ? AS payload")) {
            statement.setString(1, "permitted");
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.findColumn("payload")).isEqualTo(1);
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isEqualTo("permitted");
                assertThat(result.wasNull()).isFalse();
                assertThat(result.next()).isFalse();
            }
        }
    }
}
