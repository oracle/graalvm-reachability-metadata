/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mortbay.component.AbstractLifeCycle;
import org.mortbay.component.LifeCycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractLifeCycleTest {
    @Test
    public void notifiesListenersAndSupportsIdempotentStartAndStop() throws Exception {
        ManagedComponent component = new ManagedComponent();
        RecordingListener listener = new RecordingListener();
        component.addLifeCycleListener(listener);
        assertTrue(component.isStopped());
        try {
            component.start();
            component.start();
            assertTrue(component.isStarted());
            assertTrue(component.isRunning());
            assertEquals(1, component.starts);
        } finally {
            component.stop();
        }
        component.stop();
        assertTrue(component.isStopped());
        assertEquals(1, component.stops);
        assertEquals(List.of("starting", "started", "stopping", "stopped"), listener.events);
        component.removeLifeCycleListener(listener);
        try {
            component.start();
        } finally {
            component.stop();
        }
        assertEquals(4, listener.events.size());
        assertEquals(2, component.starts);
        assertEquals(2, component.stops);
    }

    public static class ManagedComponent extends AbstractLifeCycle {
        private int starts;
        private int stops;

        @Override
        protected void doStart() {
            starts++;
        }

        @Override
        protected void doStop() {
            stops++;
        }
    }

    public static class RecordingListener implements LifeCycle.Listener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void lifeCycleStarting(LifeCycle event) {
            events.add("starting");
        }

        @Override
        public void lifeCycleStarted(LifeCycle event) {
            events.add("started");
        }

        @Override
        public void lifeCycleFailure(LifeCycle event, Throwable cause) {
            events.add("failed");
        }

        @Override
        public void lifeCycleStopping(LifeCycle event) {
            events.add("stopping");
        }

        @Override
        public void lifeCycleStopped(LifeCycle event) {
            events.add("stopped");
        }
    }
}
