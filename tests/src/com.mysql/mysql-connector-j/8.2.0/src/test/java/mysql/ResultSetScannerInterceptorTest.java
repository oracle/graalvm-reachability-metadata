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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MysqlTestSupport.class)
public class ResultSetScannerInterceptorTest {
    @Test
    void configuredScannerAllowsOrdinaryValuesAndRejectsMatchingValues() throws Exception {
        Map<String, String> properties = Map.of(
                "queryInterceptors", "com.mysql.cj.jdbc.interceptors.ResultSetScannerInterceptor",
                "resultSetScannerRegex", "blocked-.*");
        try (Connection connection = MysqlTestSupport.connect("", properties);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 'allowed' AS payload UNION ALL SELECT 'blocked-secret'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("allowed");
            assertThat(result.next()).isTrue();
            SQLException exception = assertThrows(SQLException.class, () -> result.getString(1));
            assertThat(exception.getMessage()).isEqualTo("value disallowed by filter");
        }
    }
}
