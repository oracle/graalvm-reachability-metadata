/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import org.apache.maven.plugin.surefire.log.api.ConsoleLogger;
import org.apache.maven.plugin.surefire.log.api.ConsoleLoggerDecorator;
import org.apache.maven.surefire.booter.SurefireReflector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SurefireReflectorTest {
    @Test
    void loadsProviderApiTypesAndCreatesConsoleLoggerDecorator() {
        ClassLoader classLoader = SurefireReflector.class.getClassLoader();
        SurefireReflector reflector = new SurefireReflector(classLoader);

        ConsoleLogger sourceLogger = new RecordingConsoleLogger();
        Object reflectedLogger = reflector.createConsoleLogger(sourceLogger);

        assertThat(reflectedLogger)
                .isInstanceOf(ConsoleLoggerDecorator.class)
                .isInstanceOf(ConsoleLogger.class);
    }

    private static final class RecordingConsoleLogger implements ConsoleLogger {
        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(String message) {
            throw new UnsupportedOperationException("The logger should only be decorated in this test.");
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(String message) {
            throw new UnsupportedOperationException("The logger should only be decorated in this test.");
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warning(String message) {
            throw new UnsupportedOperationException("The logger should only be decorated in this test.");
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(String message) {
            throw new UnsupportedOperationException("The logger should only be decorated in this test.");
        }

        @Override
        public void error(String message, Throwable throwable) {
            throw new UnsupportedOperationException("The logger should only be decorated in this test.");
        }

        @Override
        public void error(Throwable throwable) {
            throw new UnsupportedOperationException("The logger should only be decorated in this test.");
        }
    }
}
