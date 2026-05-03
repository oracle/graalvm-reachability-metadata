/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JdbcUtilsTest {
    @Test
    void instantiatesExplicitJdbcDriverBeforeTryingConnection() {
        assertThatThrownBy(() -> JdbcUtils.getConnection("org.h2.Driver", "jdbc:not-h2:test", "sa", ""))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("not suitable");
    }
}
