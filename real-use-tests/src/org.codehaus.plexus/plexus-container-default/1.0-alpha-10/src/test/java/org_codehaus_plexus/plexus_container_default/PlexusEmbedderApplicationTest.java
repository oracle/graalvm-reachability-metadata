/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import java.io.StringReader;
import java.util.Properties;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.embed.Embedder;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Configurable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the Plexus embedder subsystem to bootstrap an application component from configuration.
public class PlexusEmbedderApplicationTest {
    @Test
    public void bootstrapsConfiguredApplicationService() throws Exception {
        Embedder embedder = new Embedder();
        Properties properties = new Properties();
        properties.setProperty("application.name", "release-tool");
        embedder.setProperties(properties);
        embedder.setConfiguration(new StringReader(configuration()));
        embedder.start();
        try {
            assertTrue(embedder.hasComponent(GreetingService.class.getName(), "console"));
            GreetingService service = (GreetingService) embedder.lookup(
                    GreetingService.class.getName(), "console");

            assertEquals("release-tool:ready", service.greet("ready"));
        } finally {
            embedder.stop();
        }
    }

    private static String configuration() {
        return """
                <plexus>
                  <components>
                    <component>
                      <role>%s</role>
                      <role-hint>console</role-hint>
                      <implementation>%s</implementation>
                      <lifecycle-handler>plexus-configurable</lifecycle-handler>
                      <configuration>
                        <prefix>${application.name}:</prefix>
                      </configuration>
                    </component>
                  </components>
                </plexus>
                """.formatted(GreetingService.class.getName(), GreetingServiceImpl.class.getName());
    }

    public interface GreetingService {
        String greet(String message);
    }

    public static final class GreetingServiceImpl implements GreetingService, Configurable {
        private String prefix;

        @Override
        public void configure(PlexusConfiguration configuration) throws PlexusConfigurationException {
            prefix = configuration.getChild("prefix").getValue();
        }

        @Override
        public String greet(String message) {
            return prefix + message;
        }
    }
}
