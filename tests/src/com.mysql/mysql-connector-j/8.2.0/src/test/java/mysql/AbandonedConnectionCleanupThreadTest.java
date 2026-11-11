/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class AbandonedConnectionCleanupThreadTest {
    @Test
    void jdbcDriverInitializesConnectionCleanupAndOpensUsableConnection() throws Exception {
        Driver driver = new Driver();
        try (Connection connection = driver.connect(MysqlTestSupport.url(""), MysqlTestSupport.properties())) {
            assertThat(connection.isValid(10)).isTrue();
            MysqlTestSupport.assertQuery(connection, 101);
        }
    }
}
