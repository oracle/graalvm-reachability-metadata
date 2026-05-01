/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.commons.logging.Log;
import org.apache.htrace.commons.logging.LogSource;
import org.apache.htrace.commons.logging.impl.NoOpLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogSourceTest {
    @Test
    void configuresLogImplementationByClassNameAndCreatesNamedLog() throws Exception {
        String logName = uniqueLogName("configured-by-name");

        LogSource.setLogImplementation(NoOpLog.class.getName());
        Log log = LogSource.getInstance(logName);

        assertThat(log).isExactlyInstanceOf(NoOpLog.class);
        assertThat(LogSource.getLogNames()).contains(logName);
    }

    @Test
    void configuresLogImplementationByClassAndCreatesNamedLog() throws Exception {
        String logName = uniqueLogName("configured-by-class");

        LogSource.setLogImplementation(NoOpLog.class);
        Log log = LogSource.getInstance(logName);

        assertThat(log).isExactlyInstanceOf(NoOpLog.class);
        assertThat(LogSource.getLogNames()).contains(logName);
    }

    private static String uniqueLogName(String prefix) {
        return LogSourceTest.class.getName() + "." + prefix + "." + System.nanoTime();
    }
}
