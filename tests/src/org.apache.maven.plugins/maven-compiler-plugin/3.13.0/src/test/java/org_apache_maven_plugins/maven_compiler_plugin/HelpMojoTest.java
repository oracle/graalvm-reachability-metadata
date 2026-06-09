/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_compiler_plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.plugins.maven_compiler_plugin.HelpMojo;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

public class HelpMojoTest {
    @Test
    public void executeLoadsBundledPluginHelpResource() throws Exception {
        CapturingLog log = new CapturingLog();
        HelpMojo mojo = new HelpMojo();
        mojo.setLog(log);

        mojo.execute();

        String output = log.info.toString();
        assertTrue(output.contains("Apache Maven Compiler Plugin"));
        assertTrue(output.contains("compiler:compile"));
        assertTrue(output.contains("compiler:testCompile"));
    }

    static final class CapturingLog implements Log {
        private final StringBuilder info = new StringBuilder();

        @Override
        public boolean isDebugEnabled() {
            return true;
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
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            info.append(content);
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            info.append(content);
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
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
        public boolean isErrorEnabled() {
            return true;
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
    }
}
