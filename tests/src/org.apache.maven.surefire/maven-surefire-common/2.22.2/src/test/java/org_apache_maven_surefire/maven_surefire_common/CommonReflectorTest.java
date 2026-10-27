/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.surefire.CommonReflector;
import org.apache.maven.plugin.surefire.StartupReportConfiguration;
import org.apache.maven.plugin.surefire.log.api.ConsoleLogger;
import org.apache.maven.plugin.surefire.report.DefaultReporterFactory;
import org.apache.maven.surefire.suite.RunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.maven.plugin.surefire.report.ConsoleReporter.PLAIN;
import static org.assertj.core.api.Assertions.assertThat;

public class CommonReflectorTest {
    @TempDir
    File reportsDirectory;

    @Test
    void createsReporterFactoryThroughSurefireClassLoader() {
        ClassLoader surefireClassLoader = CommonReflectorTest.class.getClassLoader();
        CommonReflector reflector = new CommonReflector(surefireClassLoader);
        StartupReportConfiguration reportConfiguration = new StartupReportConfiguration(
                true,
                true,
                PLAIN,
                false,
                true,
                reportsDirectory,
                true,
                "",
                new File(reportsDirectory, "run-history"),
                false,
                0,
                null,
                StandardCharsets.UTF_8.name(),
                false);
        RecordingConsoleLogger logger = new RecordingConsoleLogger();

        Object factory = reflector.createReportingReporterFactory(reportConfiguration, logger);

        assertThat(factory).isInstanceOf(DefaultReporterFactory.class);
        DefaultReporterFactory reporterFactory = (DefaultReporterFactory) factory;
        assertThat(reporterFactory.getReportsDirectory()).isEqualTo(reportsDirectory);

        reporterFactory.runStarting();
        RunResult result = reporterFactory.close();

        assertThat(result.getCompletedCount()).isZero();
        assertThat(result.getErrors()).isZero();
        assertThat(result.getFailures()).isZero();
        assertThat(logger.messages).contains(" T E S T S");
    }

    public static final class RecordingConsoleLogger implements ConsoleLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(String message) {
            messages.add(message);
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(String message) {
            messages.add(message);
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warning(String message) {
            messages.add(message);
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(String message) {
            messages.add(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            messages.add(message);
        }

        @Override
        public void error(Throwable throwable) {
            messages.add(throwable.getMessage());
        }
    }
}
