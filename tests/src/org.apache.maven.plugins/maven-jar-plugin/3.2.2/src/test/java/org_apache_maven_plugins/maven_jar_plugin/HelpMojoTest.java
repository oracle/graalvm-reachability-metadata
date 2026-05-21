/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_jar_plugin;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.maven.plugins.jar.HelpMojo;
import org.junit.jupiter.api.Test;

public class HelpMojoTest {
    @Test
    void executeLoadsPluginHelpResource() {
        HelpMojo mojo = new HelpMojo();

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }
}
