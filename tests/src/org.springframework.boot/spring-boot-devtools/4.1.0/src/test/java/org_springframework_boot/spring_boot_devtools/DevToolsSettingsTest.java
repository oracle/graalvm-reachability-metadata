/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.settings.DevToolsSettings;

public class DevToolsSettingsTest {

    @Test
    void getLoadsSettingsFromClassLoaderResources() {
        final DevToolsSettings settings = DevToolsSettings.get();

        assertNotNull(settings.getPropertyDefaults());
    }

}
