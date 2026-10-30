/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_plexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.junit.jupiter.api.Test;

public class DefaultPlexusContainerTest {
    @Test
    void loadsClasspathConfigurationAndRegistersComponentByRoleName() throws Exception {
        ContainerConfiguration configuration = new DefaultContainerConfiguration()
                .setContainerConfiguration("/plexus-test.xml");
        DefaultPlexusContainer container = new DefaultPlexusContainer(configuration);
        ClassRealm realm = container.getContainerRealm();
        try {
            Greeting configured = container.lookup(Greeting.class);
            assertEquals("from resource", configured.message());

            Greeting replacement = new FixedGreeting("registered instance");
            container.addComponent(replacement, Greeting.class.getName());

            assertTrue(container.hasComponent(Greeting.class));
            assertSame(replacement, container.lookup(Greeting.class));
            assertEquals("registered instance", container.lookup(Greeting.class).message());
        } finally {
            container.dispose();
            realm.close();
            realm.getWorld().disposeRealm(realm.getId());
        }
    }

    public interface Greeting {
        String message();
    }

    public static class ConfiguredGreeting implements Greeting {
        private String message;

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    public static class FixedGreeting implements Greeting {
        private final String message;

        public FixedGreeting(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }
}
