/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.util.ArrayList;
import java.util.List;

import org.apache.aries.blueprint.container.BlueprintExtender;
import org.junit.jupiter.api.Test;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import static org.assertj.core.api.Assertions.assertThat;

public class BlueprintEventDispatcherTest {
    @Test
    void acquiresExistingEventAdminAndReleasesItWhenExtenderStops() {
        EventAdminContext context = new EventAdminContext();
        RecordingEventAdmin service = new RecordingEventAdmin();
        ServiceRegistration registration = context.registerService(EventAdmin.class.getName(), service, null);
        BlueprintExtender extender = new BlueprintExtender();
        extender.start(context);
        try {
            assertThat(context.acquired).containsExactly(service);
            assertThat(context.released).isEmpty();
        } finally {
            extender.stop(context);
        }
        assertThat(context.released).containsExactly(registration.getReference());
        registration.unregister();
    }

    @Test
    void tracksEventAdminRegisteredAfterStartupAndItsReplacement() {
        EventAdminContext context = new EventAdminContext();
        BlueprintExtender extender = new BlueprintExtender();
        extender.start(context);
        try {
            assertThat(context.acquired).isEmpty();
            RecordingEventAdmin first = new RecordingEventAdmin();
            ServiceRegistration registration = context.registerService(EventAdmin.class.getName(), first, null);
            assertThat(context.acquired).containsExactly(first);
            registration.unregister();
            assertThat(context.released).containsExactly(registration.getReference());

            RecordingEventAdmin replacement = new RecordingEventAdmin();
            ServiceRegistration replacementRegistration = context.registerService(
                    EventAdmin.class.getName(), replacement, null);
            assertThat(context.acquired).containsExactly(first, replacement);
            replacementRegistration.unregister();
            assertThat(context.released).containsExactly(
                    registration.getReference(), replacementRegistration.getReference());
        } finally {
            extender.stop(context);
        }
        assertThat(context.released).hasSize(2);
    }

    private static final class EventAdminContext extends TestBundleContext {
        private final List<Object> acquired = new ArrayList<>();
        private final List<ServiceReference> released = new ArrayList<>();

        @Override
        public Object getService(ServiceReference reference) {
            Object service = super.getService(reference);
            if (service instanceof EventAdmin) {
                acquired.add(service);
            }
            return service;
        }

        @Override
        public boolean ungetService(ServiceReference reference) {
            Object service = super.getService(reference);
            if (service instanceof EventAdmin) {
                released.add(reference);
            }
            return service != null;
        }
    }

    private static final class RecordingEventAdmin implements EventAdmin {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void postEvent(Event event) {
            events.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            events.add(event);
        }
    }
}
