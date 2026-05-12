/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.log.LogManager;
import org.apache.velocity.runtime.log.LogSystem;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.junit.jupiter.api.Test;

public class LogManagerTest {
    @Test
    void createsConfiguredLogSystemFromClassName() throws Exception {
        final RuntimeInstance runtime = new ConfiguredRuntimeInstance(NullLogSystem.class.getName());

        final LogSystem logSystem = LogManager.createLogSystem(runtime);

        assertThat(logSystem).isInstanceOf(NullLogSystem.class);
    }

    private static final class ConfiguredRuntimeInstance extends RuntimeInstance {
        private final String logSystemClass;

        private ConfiguredRuntimeInstance(final String logSystemClass) {
            this.logSystemClass = logSystemClass;
        }

        @Override
        public Object getProperty(final String key) {
            if (RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS.equals(key)) {
                return logSystemClass;
            }
            return null;
        }

        @Override
        public void info(final Object message) {
        }

        @Override
        public void error(final Object message) {
        }

        @Override
        public void debug(final Object message) {
        }
    }
}
