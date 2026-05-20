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

import static org.assertj.core.api.Assertions.assertThatCode;

public class HelpMojoTest {
    @Test
    void executesHelpGoalUsingBundledPluginHelpResource() {
        HelpMojo mojo = new HelpMojo();
        mojo.setLog(new SystemStreamLog());

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }
}
