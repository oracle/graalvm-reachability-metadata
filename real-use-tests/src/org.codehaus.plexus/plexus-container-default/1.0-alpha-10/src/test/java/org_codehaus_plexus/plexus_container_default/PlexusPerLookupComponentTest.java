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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Plexus per-lookup component lifecycle for application requests that need isolated state.
public class PlexusPerLookupComponentTest {
    @Test
    public void createsAndReleasesAnIsolatedRequestComponent() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.addComponentDescriptor(descriptor());

            RequestProcessor first = (RequestProcessor) container.lookup(
                    RequestProcessor.class.getName(), "request");
            RequestProcessor second = (RequestProcessor) container.lookup(
                    RequestProcessor.class.getName(), "request");

            assertNotSame(first, second);
            assertEquals("request-1:alpha", first.process("alpha"));
            assertEquals("request-1:beta", second.process("beta"));
            assertFalse(first.isDisposed());
            assertFalse(second.isDisposed());

            container.release(first);
            assertTrue(first.isDisposed());
            assertFalse(second.isDisposed());

            container.release(second);
            assertTrue(second.isDisposed());
        } finally {
            container.dispose();
        }
    }

    private static ComponentDescriptor descriptor() {
        ComponentDescriptor descriptor = new ComponentDescriptor();
        descriptor.setRole(RequestProcessor.class.getName());
        descriptor.setRoleHint("request");
        descriptor.setImplementation(RequestProcessorImpl.class.getName());
        descriptor.setInstantiationStrategy("per-lookup");
        descriptor.setLifecycleHandler("plexus");
        return descriptor;
    }

    public interface RequestProcessor {
        String process(String request);

        boolean isDisposed();
    }

    public static final class RequestProcessorImpl implements RequestProcessor, Disposable {
        private int requestCount;
        private boolean disposed;

        @Override
        public String process(String request) {
            requestCount++;
            return "request-" + requestCount + ":" + request;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
