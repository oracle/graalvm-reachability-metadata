/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.surefire.SurefireHelper;
import org.apache.maven.plugin.surefire.log.PluginConsoleLogger;
import org.apache.maven.surefire.cli.CommandLineOption;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class SurefireHelperTest {
    @Test
    void commandLineOptionsReadsMavenRequestAndLegacyFailureBehavior() {
        MavenExecutionRequest request = new LegacyFailureBehaviorRequest();
        MavenSession session = new RequestBackedMavenSession(request);
        PluginConsoleLogger logger = new PluginConsoleLogger(new EnabledLogger());

        List<CommandLineOption> options = SurefireHelper.commandLineOptions(session, logger);

        assertThat(options)
                .contains(
                        CommandLineOption.LOGGING_LEVEL_ERROR,
                        CommandLineOption.LOGGING_LEVEL_WARN,
                        CommandLineOption.LOGGING_LEVEL_INFO,
                        CommandLineOption.LOGGING_LEVEL_DEBUG,
                        CommandLineOption.SHOW_ERRORS,
                        CommandLineOption.REACTOR_FAIL_AT_END);
    }

    public static final class RequestBackedMavenSession extends MavenSession {
        private final MavenExecutionRequest request;

        public RequestBackedMavenSession(MavenExecutionRequest request) {
            super(null, null, null, null, null, Collections.emptyList(), ".", new Properties(), new Date());
            this.request = request;
        }

        public MavenExecutionRequest getRequest() {
            return request;
        }
    }

    public static final class LegacyFailureBehaviorRequest extends DefaultMavenExecutionRequest {
        public LegacyFailureBehaviorRequest() {
            super(null, null, null, Collections.emptyList(), ".", null, new Properties(), new Properties(), true);
        }

        @Override
        public boolean isShowErrors() {
            return true;
        }

        @Override
        public String getFailureBehavior() {
            throw new NoSuchMethodError("Maven 3.0 exposes only getReactorFailureBehavior()");
        }

        public String getReactorFailureBehavior() {
            return "FAIL_AT_END";
        }
    }

    private static final class EnabledLogger implements Logger {
        @Override
        public void debug(String message) {
        }

        @Override
        public void debug(String message, Throwable throwable) {
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void info(String message, Throwable throwable) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable throwable) {
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void fatalError(String message) {
        }

        @Override
        public void fatalError(String message, Throwable throwable) {
        }

        @Override
        public boolean isFatalErrorEnabled() {
            return false;
        }

        @Override
        public int getThreshold() {
            return Logger.LEVEL_DEBUG;
        }

        @Override
        public void setThreshold(int threshold) {
        }

        @Override
        public String getName() {
            return "enabled";
        }

        @Override
        public Logger getChildLogger(String name) {
            return this;
        }
    }
}
