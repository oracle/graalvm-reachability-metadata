/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_source_plugin;

import java.util.function.Supplier;

import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.plugins.maven_source_plugin.HelpMojo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HelpMojoTest {
    @Test
    public void executeReadsBundledPluginHelpResource() throws Exception {
        HelpMojo mojo = new HelpMojo();
        CapturingLog log = new CapturingLog();
        MojoExtension.setVariableValueToObject(mojo, "logger", log);

        mojo.execute();

        assertThat(log.debugMessages()).contains("plugin-help.xml");
        assertThat(log.infoMessages())
                .contains("Maven Source Plugin")
                .contains("source:jar")
                .contains("source:test-jar");
    }

    private static final class CapturingLog implements Log {
        private final StringBuilder debugMessages = new StringBuilder();
        private final StringBuilder infoMessages = new StringBuilder();
        private final StringBuilder warnMessages = new StringBuilder();
        private final StringBuilder errorMessages = new StringBuilder();

        String debugMessages() {
            return debugMessages.toString();
        }

        String infoMessages() {
            return infoMessages.toString();
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
            append(debugMessages, content);
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
            append(debugMessages, content);
            append(debugMessages, error);
        }

        @Override
        public void debug(Throwable error) {
            append(debugMessages, error);
        }

        @Override
        public void debug(Supplier<String> content) {
            append(debugMessages, content.get());
        }

        @Override
        public void debug(Supplier<String> content, Throwable error) {
            append(debugMessages, content.get());
            append(debugMessages, error);
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            append(infoMessages, content);
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            append(infoMessages, content);
            append(infoMessages, error);
        }

        @Override
        public void info(Throwable error) {
            append(infoMessages, error);
        }

        @Override
        public void info(Supplier<String> content) {
            append(infoMessages, content.get());
        }

        @Override
        public void info(Supplier<String> content, Throwable error) {
            append(infoMessages, content.get());
            append(infoMessages, error);
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            append(warnMessages, content);
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
            append(warnMessages, content);
            append(warnMessages, error);
        }

        @Override
        public void warn(Throwable error) {
            append(warnMessages, error);
        }

        @Override
        public void warn(Supplier<String> content) {
            append(warnMessages, content.get());
        }

        @Override
        public void warn(Supplier<String> content, Throwable error) {
            append(warnMessages, content.get());
            append(warnMessages, error);
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            append(errorMessages, content);
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            append(errorMessages, content);
            append(errorMessages, error);
        }

        @Override
        public void error(Throwable error) {
            append(errorMessages, error);
        }

        @Override
        public void error(Supplier<String> content) {
            append(errorMessages, content.get());
        }

        @Override
        public void error(Supplier<String> content, Throwable error) {
            append(errorMessages, content.get());
            append(errorMessages, error);
        }

        private static void append(StringBuilder messages, CharSequence content) {
            messages.append(content).append('\n');
        }

        private static void append(StringBuilder messages, Throwable error) {
            messages.append(error.getClass().getName()).append(": ").append(error.getMessage()).append('\n');
        }
    }
}
