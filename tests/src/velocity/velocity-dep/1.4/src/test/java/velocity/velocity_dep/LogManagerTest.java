/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.log.LogManager;
import org.apache.velocity.runtime.log.LogSystem;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogManagerTest {
    @Test
    void createsConfiguredLogSystemClassByName() throws Exception {
        RuntimeInstance runtimeServices = new ConfiguredRuntimeServices(NullLogSystem.class.getName());

        LogSystem logSystem = LogManager.createLogSystem(runtimeServices);

        assertTrue(logSystem instanceof NullLogSystem);
    }

    private static final class ConfiguredRuntimeServices extends RuntimeInstance {
        private final String logSystemClassName;

        private ConfiguredRuntimeServices(String logSystemClassName) {
            this.logSystemClassName = logSystemClassName;
        }

        @Override
        public Object getProperty(String key) {
            if (RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS.equals(key)) {
                return logSystemClassName;
            }
            return null;
        }

        @Override
        public void info(Object message) {
        }

        @Override
        public void debug(Object message) {
        }

        @Override
        public void error(Object message) {
        }
    }
}
