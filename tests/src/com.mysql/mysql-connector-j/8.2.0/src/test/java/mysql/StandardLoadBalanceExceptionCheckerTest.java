/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.jdbc.ha.StandardLoadBalanceExceptionChecker;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardLoadBalanceExceptionCheckerTest {
    @Test
    void configuredExceptionSubclassSelectsFailover() {
        Properties properties = new Properties();
        properties.setProperty("loadBalanceSQLExceptionSubclassFailover", "java.sql.SQLTransientException");
        StandardLoadBalanceExceptionChecker checker = new StandardLoadBalanceExceptionChecker();
        checker.init(properties);
        assertThat(checker.shouldExceptionTriggerFailover(new SQLTransientException("retry", "HY000"))).isTrue();
        assertThat(checker.shouldExceptionTriggerFailover(new SQLException("syntax", "42000"))).isFalse();
        assertThat(checker.shouldExceptionTriggerFailover(new SQLException("disconnected", "08001"))).isTrue();
        checker.destroy();
    }
}
