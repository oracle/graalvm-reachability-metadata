/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.protocol.a.authentication.CachingSha2PasswordPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class NativeAuthenticationProviderTest {
    @Test
    void nonReusableAuthenticationPluginIsInstantiatedForTheHandshake() throws Exception {
        NonReusablePasswordPlugin.INSTANCES.set(0);
        Map<String, String> properties = Map.of(
                "authenticationPlugins", NonReusablePasswordPlugin.class.getName(),
                "defaultAuthenticationPlugin", NonReusablePasswordPlugin.class.getName());
        try (Connection connection = MysqlTestSupport.connect("", properties)) {
            MysqlTestSupport.assertQuery(connection, 83);
            assertThat(NonReusablePasswordPlugin.INSTANCES.get()).isGreaterThanOrEqualTo(2);
        }
    }

    public static class NonReusablePasswordPlugin extends CachingSha2PasswordPlugin {
        private static final AtomicInteger INSTANCES = new AtomicInteger();

        public NonReusablePasswordPlugin() {
            INSTANCES.incrementAndGet();
        }

        @Override
        public boolean isReusable() {
            return false;
        }
    }
}
