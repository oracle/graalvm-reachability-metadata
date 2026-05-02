/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jcl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogAdapterTest {
    private static final String LOGGER_NAME = "spring-jcl.dynamic-access";

    @Test
    void logFactoryCreatesNamedLogThroughAdapter() {
        Log log = LogFactory.getLog(LOGGER_NAME);

        assertThat(log).isNotNull();
        exerciseLog(log);
    }

    @Test
    void logFactoryCreatesClassLogThroughAdapter() {
        Log log = LogFactory.getLog(LogAdapterTest.class);

        assertThat(log).isNotNull();
        log.info("created via class-based lookup");
    }

    private static void exerciseLog(Log log) {
        RuntimeException exception = new RuntimeException("expected logging exception");

        log.trace("trace message");
        log.trace("trace message with exception", exception);
        log.debug("debug message");
        log.debug("debug message with exception", exception);
        log.info("info message");
        log.info("info message with exception", exception);
        log.warn("warn message");
        log.warn("warn message with exception", exception);
        log.error("error message");
        log.error("error message with exception", exception);
        log.fatal("fatal message");
        log.fatal("fatal message with exception", exception);

        log.isTraceEnabled();
        log.isDebugEnabled();
        log.isInfoEnabled();
        log.isWarnEnabled();
        log.isErrorEnabled();
        log.isFatalEnabled();
    }
}
