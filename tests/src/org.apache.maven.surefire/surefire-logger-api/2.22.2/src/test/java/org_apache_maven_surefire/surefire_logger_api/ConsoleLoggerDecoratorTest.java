/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_logger_api;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.maven.plugin.surefire.log.api.ConsoleLogger;
import org.apache.maven.plugin.surefire.log.api.ConsoleLoggerDecorator;
import org.apache.maven.plugin.surefire.log.api.PrintStreamLogger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleLoggerDecoratorTest {
    @Test
    void delegatesEnabledChecksAndMessagesToPublicLoggerApi() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleLogger logger = new ConsoleLoggerDecorator(new PrintStreamLogger(new PrintStream(output)));

        assertThat(logger.isDebugEnabled()).isTrue();
        assertThat(logger.isInfoEnabled()).isTrue();
        assertThat(logger.isWarnEnabled()).isTrue();
        assertThat(logger.isErrorEnabled()).isTrue();

        RuntimeException messageFailure = new RuntimeException("message failure");
        IllegalStateException standaloneFailure = new IllegalStateException("standalone failure");

        logger.debug("debug message");
        logger.info("info message");
        logger.warning("warning message");
        logger.error("error message");
        logger.error("error message with throwable", messageFailure);
        logger.error(standaloneFailure);

        assertThat(output.toString())
                .contains("debug message")
                .contains("info message")
                .contains("warning message")
                .contains("error message")
                .contains("error message with throwable")
                .contains("java.lang.RuntimeException: message failure")
                .contains("java.lang.IllegalStateException: standalone failure");
    }
}
