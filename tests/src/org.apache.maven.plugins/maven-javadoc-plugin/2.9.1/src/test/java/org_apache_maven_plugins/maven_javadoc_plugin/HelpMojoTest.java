/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import org.apache.maven.plugin.javadoc.HelpMojo;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HelpMojoTest {
    @Test
    void describesAvailableJavadocGoals() throws Exception {
        HelpMojo mojo = new HelpMojo();
        CapturingLog log = new CapturingLog();
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.output.toString()).contains("Maven Javadoc Plugin", "javadoc:javadoc",
                "javadoc:test-javadoc", "javadoc:fix", "javadoc:help");
    }

    private static final class CapturingLog extends SystemStreamLog {
        private final StringBuilder output = new StringBuilder();

        @Override
        public void info(CharSequence message) {
            output.append(message).append('\n');
        }
    }
}
