/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.junit.jupiter.api.Test;

public class IvySettingsTest {
    @Test
    void defaultConstructionLoadsSettingsResourcesAndOptionalGlobMatcher() {
        URL defaultSettingsUrl = IvySettings.getDefaultSettingsURL();
        URL defaultPropertiesUrl = IvySettings.getDefaultPropertiesURL();

        IvySettings settings = new IvySettings();
        PatternMatcher globMatcher = settings.getMatcher(PatternMatcher.GLOB);

        assertThat(defaultSettingsUrl).isNotNull();
        assertThat(defaultSettingsUrl.toExternalForm()).endsWith("/ivysettings.xml");
        assertThat(defaultPropertiesUrl).isNotNull();
        assertThat(defaultPropertiesUrl.toExternalForm()).endsWith("/ivy.properties");
        assertThat(globMatcher).isNotNull();
        assertThat(globMatcher.getName()).isEqualTo(PatternMatcher.GLOB);
    }

    @Test
    void typeDefLoadsClassThroughSettingsClassLoader() {
        IvySettings settings = new IvySettings();

        Class<?> type = settings.typeDef("test-string", String.class.getName());

        assertThat(type).isEqualTo(String.class);
        assertThat(settings.getTypeDef("test-string")).isEqualTo(String.class);
    }
}
