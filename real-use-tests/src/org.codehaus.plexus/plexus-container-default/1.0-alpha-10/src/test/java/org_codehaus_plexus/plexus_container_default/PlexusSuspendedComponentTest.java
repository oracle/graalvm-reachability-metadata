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
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Suspendable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Plexus component suspension and resumption for an application service that pauses work during maintenance.
public class PlexusSuspendedComponentTest {
    @Test
    public void suspendsAndResumesApplicationService() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addComponentDescriptor(descriptor(
                    MaintenanceQueue.class, MaintenanceQueueImpl.class, "deployments"));

            MaintenanceQueue queue = (MaintenanceQueue) container.lookup(
                    MaintenanceQueue.class.getName(), "deployments");

            assertEquals("deployment queued", queue.submit("deployment"));

            container.suspend(queue);
            assertEquals("maintenance: deployment rejected", queue.submit("deployment"));

            container.resume(queue);
            assertEquals("deployment queued", queue.submit("deployment"));
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
        descriptor.setLifecycleHandler("plexus");
        return descriptor;
    }

    public interface MaintenanceQueue {
        String submit(String job);
    }

    public static final class MaintenanceQueueImpl implements MaintenanceQueue, Suspendable {
        private boolean suspended;

        @Override
        public String submit(String job) {
            return suspended ? "maintenance: " + job + " rejected" : job + " queued";
        }

        @Override
        public void suspend() {
            suspended = true;
        }

        @Override
        public void resume() {
            suspended = false;
        }
    }
}
