/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLogTest {
    static {
        System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "info");
    }

    @Test
    void constructorUsesThreadContextClassLoaderWhileInitializingConfiguration() {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(SimpleLogTest.class.getClassLoader());

            SimpleLog log = new SimpleLog(uniqueLoggerName("initialization"));

            assertThat(log.getLevel()).isEqualTo(SimpleLog.LOG_LEVEL_INFO);
            assertThat(log.isTraceEnabled()).isFalse();
            assertThat(log.isDebugEnabled()).isFalse();
            assertThat(log.isInfoEnabled()).isTrue();
            assertThat(log.isWarnEnabled()).isTrue();
            assertThat(log.isErrorEnabled()).isTrue();
            assertThat(log.isFatalEnabled()).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    @Test
    void levelChangesControlWhichMessagesAreWritten() {
        SimpleLog log = new SimpleLog(uniqueLoggerName("levels"));
        log.setLevel(SimpleLog.LOG_LEVEL_WARN);

        String output = captureStandardError(() -> {
            log.debug("debug is disabled");
            log.info("info is disabled");
            log.warn("warn is enabled");
            log.error("error is enabled");
        });

        assertThat(output)
                .doesNotContain("debug is disabled", "info is disabled")
                .contains("[WARN]", "warn is enabled", "[ERROR]", "error is enabled");
    }

    @Test
    void loggingWritesEveryLevelAndThrowableDetailsToStandardError() {
        SimpleLog log = new SimpleLog(uniqueLoggerName("logging"));
        log.setLevel(SimpleLog.LOG_LEVEL_ALL);
        RuntimeException failure = new RuntimeException("boom");

        String output = captureStandardError(() -> {
            log.trace("trace message");
            log.debug("debug message");
            log.info("info message");
            log.warn("warn message");
            log.error("error message");
            log.fatal("fatal message", failure);
        });

        assertThat(output).contains(
                "[TRACE]",
                "trace message",
                "[DEBUG]",
                "debug message",
                "[INFO]",
                "info message",
                "[WARN]",
                "warn message",
                "[ERROR]",
                "error message",
                "[FATAL]",
                "fatal message",
                "java.lang.RuntimeException: boom");
    }

    private static String captureStandardError(Runnable action) {
        PrintStream previousErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (PrintStream capturedErr = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            System.setErr(capturedErr);
            action.run();
        } finally {
            System.setErr(previousErr);
        }
        return err.toString(StandardCharsets.UTF_8);
    }

    private static String uniqueLoggerName(String suffix) {
        return SimpleLogTest.class.getName() + "." + suffix + "." + System.nanoTime();
    }
}
