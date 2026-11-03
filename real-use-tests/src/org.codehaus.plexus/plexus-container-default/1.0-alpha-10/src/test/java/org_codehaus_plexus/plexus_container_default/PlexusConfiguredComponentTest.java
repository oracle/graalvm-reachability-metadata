/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Configurable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises Plexus component configuration to render an application message from a container-managed service.
public class PlexusConfiguredComponentTest {
    @Test
    public void looksUpConfiguredApplicationComponent() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            ComponentDescriptor descriptor = new ComponentDescriptor();
            descriptor.setRole(MessageRenderer.class.getName());
            descriptor.setRoleHint("console");
            descriptor.setImplementation(ConfiguredMessageRenderer.class.getName());
            descriptor.setInstantiationStrategy("singleton");
            descriptor.setLifecycleHandler("plexus-configurable");
            descriptor.setConfiguration(configuration());
            container.addComponentDescriptor(descriptor);

            MessageRenderer renderer = (MessageRenderer) container.lookup(
                    MessageRenderer.class.getName(), "console");
            MessageRenderer sameRenderer = (MessageRenderer) container.lookup(
                    MessageRenderer.class.getName(), "console");

            assertSame(renderer, sameRenderer);
            assertEquals("[deployment] ready!", renderer.render("ready"));
        } finally {
            container.dispose();
        }
    }

    private static PlexusConfiguration configuration() {
        XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("configuration");
        configuration.addChild(value("prefix", "[deployment] "));
        configuration.addChild(value("suffix", "!"));
        return configuration;
    }

    private static PlexusConfiguration value(String name, String text) {
        XmlPlexusConfiguration value = new XmlPlexusConfiguration(name);
        value.setValue(text);
        return value;
    }

    public interface MessageRenderer {
        String render(String message);
    }

    public static final class ConfiguredMessageRenderer implements MessageRenderer, Configurable {
        private String prefix;
        private String suffix;

        @Override
        public void configure(PlexusConfiguration configuration) throws PlexusConfigurationException {
            prefix = configuration.getChild("prefix").getValue();
            suffix = configuration.getChild("suffix").getValue();
        }

        @Override
        public String render(String message) {
            return prefix + message + suffix;
        }
    }
}
