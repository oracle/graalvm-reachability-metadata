/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.processor.ConfigurationProcessor;
import org.codehaus.plexus.configuration.processor.ConfigurationResourceHandler;
import org.codehaus.plexus.configuration.processor.ResourceBundleConfigurationResourceHandler;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceBundleConfigurationResourceHandlerTest {
    private static final String BUNDLE_NAME =
        "org_codehaus_plexus.plexus_container_default.ResourceBundleConfigurationResourceHandlerMessages";

    @Test
    void configurationProcessorInlinesPropertiesFromResourceBundle() throws Exception {
        ResourceBundleConfigurationResourceHandler handler = new ResourceBundleConfigurationResourceHandler();
        ConfigurationProcessor processor = new ConfigurationProcessor();
        processor.addConfigurationResourceHandler(handler);

        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        XmlPlexusConfiguration bundleReference = new XmlPlexusConfiguration(handler.getId());
        bundleReference.setAttribute(ConfigurationResourceHandler.SOURCE, BUNDLE_NAME);
        configuration.addChild(bundleReference);

        PlexusConfiguration processed = processor.process(configuration, Collections.emptyMap());
        PlexusConfiguration bundleConfiguration = processed.getChild(BUNDLE_NAME, false);

        assertThat(bundleConfiguration).isNotNull();
        assertThat(bundleConfiguration.getChild("componentRole", false).getValue())
            .isEqualTo("org.example.Component");
        assertThat(bundleConfiguration.getChild("implementation", false).getValue())
            .isEqualTo("org.example.DefaultComponent");
    }
}
