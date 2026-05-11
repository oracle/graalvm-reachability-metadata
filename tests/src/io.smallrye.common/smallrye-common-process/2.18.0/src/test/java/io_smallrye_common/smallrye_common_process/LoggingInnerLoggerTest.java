/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_process;

import java.nio.file.Path;

import io.smallrye.common.process.Logging_$logger;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingInnerLoggerTest {
    @Test
    void generatedMessageLoggerIsReachableForProcessThreads() {
        Class<? extends BasicLogger> loggerInterface = smallryeProcessLoggingInterface();
        BasicLogger logger = Logger.getMessageLogger(loggerInterface, loggerInterface.getPackageName());

        assertThat(logger).isInstanceOf(Logging_$logger.class);
        ((Logging_$logger) logger).logErrors(Path.of("smallrye-common-process-test"), 1L, new StringBuilder("stderr"));
    }

    private static Class<? extends BasicLogger> smallryeProcessLoggingInterface() {
        for (Class<?> loggerInterface : Logging_$logger.class.getInterfaces()) {
            if ("io.smallrye.common.process.Logging".equals(loggerInterface.getName())) {
                return loggerInterface.asSubclass(BasicLogger.class);
            }
        }
        throw new IllegalStateException("SmallRye process logging interface was not found");
    }
}
