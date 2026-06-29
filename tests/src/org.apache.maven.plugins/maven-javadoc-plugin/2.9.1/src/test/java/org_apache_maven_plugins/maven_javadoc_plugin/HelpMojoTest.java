/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.plugin.javadoc.HelpMojo;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

public class HelpMojoTest {
    @Test
    void executeLoadsBundledPluginHelpResource() throws Exception {
        HelpMojo mojo = new HelpMojo();
        CapturingLog log = new CapturingLog();
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.info()).contains("Apache Maven Javadoc Plugin");
        assertThat(log.info()).contains("This plugin has");
        assertThat(log.info()).contains("javadoc:help");
    }

    private static final class CapturingLog extends SystemStreamLog {
        private final StringBuilder info = new StringBuilder();

        String info() {
            return info.toString();
        }

        @Override
        public void info(CharSequence content) {
            info.append(content);
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            info(content);
        }

        @Override
        public void info(Throwable error) {
            info.append(error.getMessage());
        }
    }
}
