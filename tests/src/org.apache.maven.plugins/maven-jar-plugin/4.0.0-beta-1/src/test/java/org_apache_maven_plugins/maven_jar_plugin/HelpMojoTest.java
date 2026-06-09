/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_jar_plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.maven.api.plugin.Log;
import org.apache.maven.plugins.maven_jar_plugin.HelpMojo;
import org.junit.jupiter.api.Test;

public class HelpMojoTest {
    @Test
    void executeLoadsPluginHelpResource() throws ReflectiveOperationException {
        CapturingLog log = new CapturingLog();
        HelpMojo mojo = configuredHelpMojo(log);

        mojo.execute();

        assertThat(log.infoMessages())
                .singleElement()
                .satisfies(message -> assertThat(message).contains("jar:jar"));
    }

    private static HelpMojo configuredHelpMojo(Log log) throws ReflectiveOperationException {
        HelpMojo mojo = new HelpMojo();
        Field loggerField = HelpMojo.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(mojo, log);
        return mojo;
    }

    private static final class CapturingLog implements Log {
        private final List<String> infoMessages = new ArrayList<>();

        private List<String> infoMessages() {
            return infoMessages;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(CharSequence content) {
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public void debug(Supplier<String> content) {
        }

        @Override
        public void debug(Supplier<String> content, Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            infoMessages.add(content.toString());
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            info(content);
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public void info(Supplier<String> content) {
            info(content.get());
        }

        @Override
        public void info(Supplier<String> content, Throwable error) {
            info(content);
        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(CharSequence content) {
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public void warn(Supplier<String> content) {
        }

        @Override
        public void warn(Supplier<String> content, Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(CharSequence content) {
        }

        @Override
        public void error(CharSequence content, Throwable error) {
        }

        @Override
        public void error(Throwable error) {
        }

        @Override
        public void error(Supplier<String> content) {
        }

        @Override
        public void error(Supplier<String> content, Throwable error) {
        }
    }
}
