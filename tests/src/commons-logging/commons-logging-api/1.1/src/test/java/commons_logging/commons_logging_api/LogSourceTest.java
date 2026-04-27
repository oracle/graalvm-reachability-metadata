/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_logging.commons_logging_api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogSource;
import org.apache.commons.logging.impl.NoOpLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogSourceTest {
    @Test
    void createsLogUsingImplementationConfiguredByClassName() throws Exception {
        LogSource.setLogImplementation(RecordingLog.class.getName());

        Log log = LogSource.getInstance("log.source.class.name");

        assertThat(log).isInstanceOf(RecordingLog.class);
        assertThat(((RecordingLog) log).name).isEqualTo("log.source.class.name");
    }

    @Test
    void createsLogUsingImplementationConfiguredByClass() throws Exception {
        LogSource.setLogImplementation(RecordingLog.class);

        Log log = LogSource.makeNewLogInstance("log.source.class");

        assertThat(log).isInstanceOf(RecordingLog.class);
        assertThat(((RecordingLog) log).name).isEqualTo("log.source.class");
    }

    public static final class RecordingLog extends NoOpLog {
        private final String name;

        public RecordingLog(String name) {
            super(name);
            this.name = name;
        }
    }
}
