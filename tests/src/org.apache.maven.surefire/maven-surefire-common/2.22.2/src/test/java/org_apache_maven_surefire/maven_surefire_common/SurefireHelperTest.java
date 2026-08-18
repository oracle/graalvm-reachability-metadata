/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import java.util.List;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.surefire.SurefireHelper;
import org.apache.maven.plugin.surefire.log.PluginConsoleLogger;
import org.apache.maven.surefire.cli.CommandLineOption;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.Test;
import org.sonatype.aether.RepositorySystemSession;

import static org.assertj.core.api.Assertions.assertThat;

public class SurefireHelperTest {
    @Test
    void commandLineOptionsReadMavenThreeSessionRequestAndFailureBehavior() {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest()
                .setShowErrors(true)
                .setReactorFailureBehavior("FAIL_FAST");
        MavenSession session = new MavenSession(
                null, (RepositorySystemSession) null, request, new DefaultMavenExecutionResult());
        PluginConsoleLogger logger = new PluginConsoleLogger(new EnabledLogger());

        List<CommandLineOption> options = SurefireHelper.commandLineOptions(session, logger);

        assertThat(options).containsExactly(
                CommandLineOption.LOGGING_LEVEL_ERROR,
                CommandLineOption.LOGGING_LEVEL_WARN,
                CommandLineOption.LOGGING_LEVEL_INFO,
                CommandLineOption.LOGGING_LEVEL_DEBUG,
                CommandLineOption.SHOW_ERRORS,
                CommandLineOption.REACTOR_FAIL_FAST);
    }

    private static final class EnabledLogger implements Logger {
        private int threshold = LEVEL_DEBUG;
        private int loggedMessages;

        @Override
        public void debug(String message) {
            loggedMessages++;
        }

        @Override
        public void debug(String message, Throwable throwable) {
            loggedMessages++;
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void info(String message) {
            loggedMessages++;
        }

        @Override
        public void info(String message, Throwable throwable) {
            loggedMessages++;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void warn(String message) {
            loggedMessages++;
        }

        @Override
        public void warn(String message, Throwable throwable) {
            loggedMessages++;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void error(String message) {
            loggedMessages++;
        }

        @Override
        public void error(String message, Throwable throwable) {
            loggedMessages++;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void fatalError(String message) {
            loggedMessages++;
        }

        @Override
        public void fatalError(String message, Throwable throwable) {
            loggedMessages++;
        }

        @Override
        public boolean isFatalErrorEnabled() {
            return true;
        }

        @Override
        public Logger getChildLogger(String name) {
            return this;
        }

        @Override
        public int getThreshold() {
            return threshold;
        }

        @Override
        public void setThreshold(int newThreshold) {
            threshold = newThreshold;
        }

        @Override
        public String getName() {
            return "enabled-" + loggedMessages;
        }
    }
}
