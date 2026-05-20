/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagerBaseTest {

    @Test
    void createsConfiguredSessionIdGenerator() {
        ConfigurableManager manager = new ConfigurableManager();
        manager.useSessionIdGeneratorClass(StandardSessionIdGenerator.class);

        assertThat(manager.getSessionIdGenerator()).isInstanceOf(StandardSessionIdGenerator.class);
    }

    private static final class ConfigurableManager extends StandardManager {
        private void useSessionIdGeneratorClass(Class<? extends SessionIdGenerator> generatorClass) {
            sessionIdGeneratorClass = generatorClass;
        }
    }
}
