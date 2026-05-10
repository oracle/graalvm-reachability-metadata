/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_gpg_plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.plugin.gpg.HelpMojo;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

public class HelpMojoTest {
    @Test
    void executeLoadsPluginHelpResourceAndWritesGoalSummary() throws Exception {
        HelpMojo mojo = new HelpMojo();
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.info()).contains("This plugin has");
        assertThat(log.info()).contains("gpg:sign");
    }

    private static final class RecordingLog implements Log {
        private final StringBuilder debug = new StringBuilder();
        private final StringBuilder info = new StringBuilder();
        private final StringBuilder warn = new StringBuilder();
        private final StringBuilder error = new StringBuilder();

        String info() {
            return info.toString();
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
            append(debug, content);
        }

        @Override
        public void debug(CharSequence content, Throwable failure) {
            append(debug, content, failure);
        }

        @Override
        public void debug(Throwable failure) {
            append(debug, failure);
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            append(info, content);
        }

        @Override
        public void info(CharSequence content, Throwable failure) {
            append(info, content, failure);
        }

        @Override
        public void info(Throwable failure) {
            append(info, failure);
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            append(warn, content);
        }

        @Override
        public void warn(CharSequence content, Throwable failure) {
            append(warn, content, failure);
        }

        @Override
        public void warn(Throwable failure) {
            append(warn, failure);
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            append(error, content);
        }

        @Override
        public void error(CharSequence content, Throwable failure) {
            append(error, content, failure);
        }

        @Override
        public void error(Throwable failure) {
            append(error, failure);
        }

        private static void append(StringBuilder target, CharSequence content) {
            target.append(content).append('\n');
        }

        private static void append(StringBuilder target, CharSequence content, Throwable failure) {
            append(target, content);
            append(target, failure);
        }

        private static void append(StringBuilder target, Throwable failure) {
            target.append(failure.getClass().getName()).append(": ").append(failure.getMessage()).append('\n');
        }
    }
}
