/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.conf.ConnectionUrl;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionUrlTest {
    @Test
    void configurationTemplateSuppliesDefaultsWithoutOverridingExplicitSettings() {
        Properties properties = new Properties();
        properties.setProperty("useConfigs", "maxPerformance");
        properties.setProperty("cachePrepStmts", "false");
        ConnectionUrl url = ConnectionUrl.getConnectionUrlInstance("jdbc:mysql://localhost/catalog", properties);
        Properties resolved = url.getMainHost().exposeAsProperties();
        assertThat(resolved.getProperty("cachePrepStmts")).isEqualTo("false");
        assertThat(resolved.getProperty("cacheCallableStmts")).isEqualTo("true");
        assertThat(resolved.getProperty("useLocalSessionState")).isEqualTo("true");
        assertThat(url.getMainHost().getDatabase()).isEqualTo("catalog");
    }
}
