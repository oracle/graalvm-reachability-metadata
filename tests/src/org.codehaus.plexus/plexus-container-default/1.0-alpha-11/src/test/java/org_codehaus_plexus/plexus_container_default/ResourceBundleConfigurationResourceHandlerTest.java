/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.processor.ConfigurationResourceHandler;
import org.codehaus.plexus.configuration.processor.ResourceBundleConfigurationResourceHandler;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceBundleConfigurationResourceHandlerTest {
    @Test
    public void loadsConfigurationEntriesFromResourceBundle() throws Exception {
        ResourceBundleConfigurationResourceHandler handler = new ResourceBundleConfigurationResourceHandler();
        String bundleName = "org_codehaus_plexus.plexus_container_default.ResourceBundleConfigurationResourceHandlerMessages";
        Map parameters = new HashMap();
        parameters.put(ConfigurationResourceHandler.SOURCE, bundleName);

        PlexusConfiguration[] configurations = handler.handleRequest(parameters);

        assertThat(handler.getId()).isEqualTo("resourcebundle-configuration-resource");
        assertThat(configurations).hasSize(1);
        PlexusConfiguration configuration = configurations[0];
        assertThat(configuration.getName()).isEqualTo(bundleName);
        assertThat(configuration.getChild("component.role").getValue()).isEqualTo("sample-role");
        assertThat(configuration.getChild("component.description").getValue()).isEqualTo("Loaded from a bundle");
    }
}
