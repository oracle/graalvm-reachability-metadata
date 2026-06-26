/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import java.net.URL;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.settings.DevToolsSettings;

import static org.assertj.core.api.Assertions.assertThat;

public class DevToolsSettingsTest {

    @Test
    void getLoadsSettingsFromSpringDevtoolsPropertiesResources() throws Exception {
        DevToolsSettings settings = DevToolsSettings.get();

        assertThat(settings.getPropertyDefaults()).containsEntry("spring.application.name", "devtools-settings-test");
        assertThat(settings.isRestartInclude(new URL("file:/workspace/sample-include.jar"))).isTrue();
        assertThat(settings.isRestartExclude(new URL("file:/workspace/generated-classes/example.class"))).isTrue();
    }

}
