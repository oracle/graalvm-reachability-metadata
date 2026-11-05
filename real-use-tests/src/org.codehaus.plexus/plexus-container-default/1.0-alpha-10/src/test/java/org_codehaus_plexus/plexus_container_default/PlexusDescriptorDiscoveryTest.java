/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import java.io.StringReader;
import java.util.List;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.discovery.DefaultComponentDiscoverer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.ComponentSetDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Plexus component descriptor discovery for an application plugin registered from XML.
public class PlexusDescriptorDiscoveryTest {
    @Test
    public void discoversAndRegistersApplicationPluginDescriptor() throws Exception {
        DefaultComponentDiscoverer discoverer = new DefaultComponentDiscoverer();
        ComponentSetDescriptor descriptors = discoverer.createComponentDescriptors(
                new StringReader(pluginDescriptor()), "release-plugin.xml");
        List<?> components = descriptors.getComponents();

        PlexusContainer container = new DefaultPlexusContainer();
        try {
            assertEquals(1, components.size());
            ComponentDescriptor descriptor = (ComponentDescriptor) components.get(0);
            container.addComponentDescriptor(descriptor);

            ReleasePlugin plugin = (ReleasePlugin) container.lookup(
                    ReleasePlugin.class.getName(), "publish");

            assertNotNull(plugin);
            assertEquals("published release-42", plugin.publish("release-42"));
        } finally {
            container.dispose();
        }
    }

    private static String pluginDescriptor() {
        return """
                <component-set>
                  <components>
                    <component>
                      <role>%s</role>
                      <role-hint>publish</role-hint>
                      <implementation>%s</implementation>
                      <instantiation-strategy>singleton</instantiation-strategy>
                    </component>
                  </components>
                </component-set>
                """.formatted(ReleasePlugin.class.getName(), ReleasePluginImpl.class.getName());
    }

    public interface ReleasePlugin {
        String publish(String release);
    }

    public static final class ReleasePluginImpl implements ReleasePlugin {
        @Override
        public String publish(String release) {
            return "published " + release;
        }
    }
}
