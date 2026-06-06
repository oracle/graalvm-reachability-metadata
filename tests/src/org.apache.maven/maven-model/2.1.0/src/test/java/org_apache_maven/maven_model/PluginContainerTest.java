/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.PluginContainer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PluginContainerTest {
    @Test
    void rejectsNullPluginWithTypedErrorMessage() {
        PluginContainer pluginContainer = new PluginContainer();

        ClassCastException exception = assertThrows(ClassCastException.class,
                () -> pluginContainer.addPlugin(null));

        assertThat(exception).hasMessageContaining("org.apache.maven.model.Plugin");
        assertThat(pluginContainer.getPlugins()).isEmpty();
    }
}
