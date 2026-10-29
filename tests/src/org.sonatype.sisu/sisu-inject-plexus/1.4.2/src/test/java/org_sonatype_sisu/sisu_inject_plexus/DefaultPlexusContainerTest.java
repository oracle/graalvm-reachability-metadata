/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultPlexusContainerTest {
    @Test
    void loadsClasspathConfigurationAndLooksUpConfiguredComponent() throws Exception {
        ClassWorld world = new ClassWorld("plexus.core", getClass().getClassLoader());
        try {
            ContainerConfiguration configuration = new DefaultContainerConfiguration()
                    .setClassWorld(world)
                    .setContainerConfiguration("org_sonatype_sisu/sisu_inject_plexus/container.xml");
            DefaultPlexusContainer container = new DefaultPlexusContainer(configuration);
            try {
                Greeting greeting = container.lookup(Greeting.class);
                assertEquals("Welcome east", greeting.greet("east"));
                assertSame(greeting, container.lookup(Greeting.class));
                assertTrue(container.hasComponent(Greeting.class));
            } finally {
                container.dispose();
            }
        } finally {
            world.disposeRealm("plexus.core");
        }
    }

    @Test
    void registersExistingComponentByRoleName() throws Exception {
        ClassWorld world = new ClassWorld("plexus.core", getClass().getClassLoader());
        try {
            DefaultPlexusContainer container = new DefaultPlexusContainer(
                    new DefaultContainerConfiguration().setClassWorld(world));
            try {
                Greeting greeting = name -> "Hello " + name;
                container.addComponent(greeting, Greeting.class.getName());

                assertSame(greeting, container.lookup(Greeting.class));
                assertEquals("Hello west", container.lookup(Greeting.class).greet("west"));
            } finally {
                container.dispose();
            }
        } finally {
            world.disposeRealm("plexus.core");
        }
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class ConfiguredGreeting implements Greeting {
        private String salutation;

        public void setSalutation(String salutation) {
            this.salutation = salutation;
        }

        @Override
        public String greet(String name) {
            return salutation + " " + name;
        }
    }
}
