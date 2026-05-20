/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_surefire_plugin;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugin.surefire.HelpMojo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HelpMojoTest {

    @Test
    void executeLoadsPluginHelpResource() throws MojoExecutionException {
        HelpMojo mojo = new HelpMojo();
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.infoMessages)
                .anySatisfy(message -> assertThat(message)
                        .contains("Surefire")
                        .contains("surefire:test"));
    }

    private static final class RecordingLog extends SystemStreamLog {

        private final List<String> infoMessages = new ArrayList<>();

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
    }
}
