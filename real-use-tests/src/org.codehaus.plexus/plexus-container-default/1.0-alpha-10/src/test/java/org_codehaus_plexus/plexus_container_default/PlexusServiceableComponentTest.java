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
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Serviceable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.ServiceLocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Plexus service lookup for an application component that obtains a container service during startup.
public class PlexusServiceableComponentTest {
    @Test
    public void servicesApplicationComponentFromContainerLocator() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addComponentDescriptor(descriptor(
                    DeploymentClock.class, DeploymentClockImpl.class, "system", null));
            container.addComponentDescriptor(descriptor(
                    DeploymentGreeting.class, DeploymentGreetingImpl.class, "console", "plexus"));

            DeploymentGreeting greeting = (DeploymentGreeting) container.lookup(
                    DeploymentGreeting.class.getName(), "console");

            assertEquals("2025-01-01: deployment ready", greeting.greet("deployment ready"));
        } finally {
            container.dispose();
        }
    }

    private static ComponentDescriptor descriptor(
            Class<?> role, Class<?> implementation, String roleHint, String lifecycleHandler) {
        ComponentDescriptor descriptor = new ComponentDescriptor();
        descriptor.setRole(role.getName());
        descriptor.setRoleHint(roleHint);
        descriptor.setImplementation(implementation.getName());
        descriptor.setInstantiationStrategy("singleton");
        descriptor.setLifecycleHandler(lifecycleHandler);
        return descriptor;
    }

    public interface DeploymentClock {
        String today();
    }

    public static final class DeploymentClockImpl implements DeploymentClock {
        @Override
        public String today() {
            return "2025-01-01";
        }
    }

    public interface DeploymentGreeting {
        String greet(String message);
    }

    public static final class DeploymentGreetingImpl implements DeploymentGreeting, Serviceable {
        private DeploymentClock clock;

        @Override
        public void service(ServiceLocator serviceLocator) {
            try {
                clock = (DeploymentClock) serviceLocator.lookup(
                        DeploymentClock.class.getName(), "system");
            } catch (ComponentLookupException exception) {
                throw new IllegalStateException("Deployment clock is unavailable", exception);
            }
        }

        @Override
        public String greet(String message) {
            return clock.today() + ": " + message;
        }
    }
}
