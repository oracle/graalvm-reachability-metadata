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
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.LogChute;
import org.apache.velocity.runtime.log.LogManager;
import org.junit.jupiter.api.Test;

public class LogManagerTest {
    @Test
    void installsConfiguredLogChuteFromClassName() throws Exception {
        RecordingLogChute.reset();
        final RuntimeInstance runtime = new ConfiguredRuntimeInstance(RecordingLogChute.class.getName());
        final Log log = runtime.getLog();

        LogManager.updateLog(log, runtime);
        log.info("configured");

        assertThat(RecordingLogChute.isInitialized()).isTrue();
        assertThat(RecordingLogChute.getLastLevel()).isEqualTo(LogChute.INFO_ID);
        assertThat(RecordingLogChute.getLastMessage()).isEqualTo("configured");
    }

    private static final class ConfiguredRuntimeInstance extends RuntimeInstance {
        private final String logChuteClass;

        private ConfiguredRuntimeInstance(final String logChuteClass) {
            this.logChuteClass = logChuteClass;
        }

        @Override
        public Object getProperty(final String key) {
            if (RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS.equals(key)) {
                return logChuteClass;
            }
            return null;
        }
    }

    public static final class RecordingLogChute implements LogChute {
        private static boolean initialized;
        private static int lastLevel;
        private static String lastMessage;

        public static void reset() {
            initialized = false;
            lastLevel = -1;
            lastMessage = null;
        }

        public static boolean isInitialized() {
            return initialized;
        }

        public static int getLastLevel() {
            return lastLevel;
        }

        public static String getLastMessage() {
            return lastMessage;
        }

        @Override
        public void init(final RuntimeServices runtimeServices) {
            initialized = true;
        }

        @Override
        public void log(final int level, final String message) {
            lastLevel = level;
            lastMessage = message;
        }

        @Override
        public void log(final int level, final String message, final Throwable throwable) {
            log(level, message);
        }

        @Override
        public boolean isLevelEnabled(final int level) {
            return true;
        }
    }
}
