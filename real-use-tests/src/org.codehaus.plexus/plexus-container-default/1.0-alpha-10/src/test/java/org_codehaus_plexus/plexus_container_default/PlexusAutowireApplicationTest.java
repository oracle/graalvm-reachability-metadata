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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises Plexus application-object autowiring for a service assembled outside the container.
public class PlexusAutowireApplicationTest {
    @Test
    public void autowiresApplicationServiceWithContainerComponent() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addComponentDescriptor(descriptor(
                    NotificationSink.class, ConsoleNotificationSink.class, "console"));

            DeploymentReporter reporter = new DeploymentReporter();
            Object autowired = container.autowire(reporter);

            assertSame(reporter, autowired);
            assertEquals("[deploy] started", reporter.report("started"));
        } finally {
            container.dispose();
        }
    }

    private static ComponentDescriptor descriptor(
            Class<?> role, Class<?> implementation, String roleHint) {
        ComponentDescriptor descriptor = new ComponentDescriptor();
        descriptor.setRole(role.getName());
        descriptor.setRoleHint(roleHint);
        descriptor.setImplementation(implementation.getName());
        descriptor.setInstantiationStrategy("singleton");
        return descriptor;
    }

    public interface NotificationSink {
        String prefix();
    }

    public static final class ConsoleNotificationSink implements NotificationSink {
        @Override
        public String prefix() {
            return "[deploy] ";
        }
    }

    public static final class DeploymentReporter {
        private NotificationSink notificationSink;

        public void setNotificationSink(NotificationSink notificationSink) {
            this.notificationSink = notificationSink;
        }

        public String report(String event) {
            return notificationSink.prefix() + event;
        }
    }
}
