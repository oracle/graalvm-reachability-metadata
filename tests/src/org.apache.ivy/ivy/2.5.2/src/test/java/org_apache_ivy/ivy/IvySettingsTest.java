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
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.junit.jupiter.api.Test;

public class IvySettingsTest {
    @Test
    public void initializesBundledResourcesAndDefaultMatchers() {
        IvySettings settings = new IvySettings();

        assertBundledResource(IvySettings.getDefaultPropertiesURL(), "ivy.properties");
        assertBundledResource(IvySettings.getDefaultSettingsURL(), "ivysettings.xml");
        assertBundledResource(IvySettings.getDefault14SettingsURL(), "ivysettings-1.4.xml");
        assertThat(settings.getVariable("ivy.default.settings.dir")).isNotBlank();
        assertThat(settings.getMatcher(PatternMatcher.EXACT)).isNotNull();
        assertThat(settings.getMatcher(PatternMatcher.REGEXP)).isNotNull();
        assertThat(settings.getMatcher(PatternMatcher.EXACT_OR_REGEXP)).isNotNull();
        assertThat(settings.getMatcher(PatternMatcher.GLOB)).isNotNull();
    }

    @Test
    public void typeDefLoadsClassesThroughSettingsClassLoader() {
        IvySettings settings = new IvySettings();

        Class<?> resolverClass = settings.typeDef("chain-resolver", ChainResolver.class.getName());

        assertThat(resolverClass).isEqualTo(ChainResolver.class);
        assertThat(settings.getTypeDef("chain-resolver")).isEqualTo(ChainResolver.class);
    }

    private static void assertBundledResource(URL resourceUrl, String fileName) {
        assertThat(resourceUrl).isNotNull();
        assertThat(resourceUrl.toExternalForm()).endsWith(fileName);
    }
}
