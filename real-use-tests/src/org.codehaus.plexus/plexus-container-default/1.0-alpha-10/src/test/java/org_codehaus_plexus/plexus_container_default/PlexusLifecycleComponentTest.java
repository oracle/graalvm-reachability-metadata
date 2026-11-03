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
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Startable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the Plexus lifecycle subsystem for a container-managed application component.
public class PlexusLifecycleComponentTest {
    @Test
    public void startsAndDisposesApplicationComponent() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        LifecycleService service;
        try {
            ComponentDescriptor descriptor = new ComponentDescriptor();
            descriptor.setRole(LifecycleService.class.getName());
            descriptor.setRoleHint("deployment");
            descriptor.setImplementation(LifecycleServiceImpl.class.getName());
            descriptor.setInstantiationStrategy("singleton");
            descriptor.setLifecycleHandler("plexus");
            container.addComponentDescriptor(descriptor);

            service = (LifecycleService) container.lookup(
                    LifecycleService.class.getName(), "deployment");

            assertTrue(service.isStarted());
            assertEquals("deployment is ready", service.status());
        } finally {
            container.dispose();
        }

        assertFalse(service.isStarted());
        assertTrue(service.isDisposed());
    }

    public interface LifecycleService {
        boolean isStarted();

        boolean isDisposed();

        String status();
    }

    public static final class LifecycleServiceImpl implements LifecycleService, Startable, Disposable {
        private boolean started;
        private boolean disposed;

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            started = false;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public String status() {
            return started ? "deployment is ready" : "deployment is stopped";
        }
    }
}
