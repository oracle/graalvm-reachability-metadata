/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class IvySettingsTest {

    @Test
    void initializesDefaultResourcesAndOptionalGlobMatcher() {
        IvySettings settings = new IvySettings();

        URL defaultSettings = IvySettings.getDefaultSettingsURL();
        URL defaultProperties = IvySettings.getDefaultPropertiesURL();

        assertThat(defaultSettings).isNotNull();
        assertThat(defaultSettings.toExternalForm()).endsWith("/ivysettings.xml");
        assertThat(defaultProperties).isNotNull();
        assertThat(defaultProperties.toExternalForm()).endsWith("/ivy.properties");
        assertThat(settings.getVariable("ivy.default.settings.dir")).isNotBlank();
        assertThat(settings.getMatcher(PatternMatcher.GLOB))
                .as("IvySettings should register the optional glob matcher")
                .isNotNull();
    }

    @Test
    void typeDefLoadsClassThroughSettingsClassLoader() {
        IvySettings settings = new IvySettings();

        Class<?> type = settings.typeDef("chainResolver", ChainResolver.class.getName());

        assertThat(type).isSameAs(ChainResolver.class);
        assertThat(settings.getTypeDef("chainResolver")).isSameAs(ChainResolver.class);
    }
}
