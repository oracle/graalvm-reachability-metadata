/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.mortbay.component.AbstractLifeCycle;
import org.mortbay.component.LifeCycle;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractLifeCycleTest {
    @Test
    void listenerReceivesStartAndStopTransitions() throws Exception {
        RecordingLifeCycle component = new RecordingLifeCycle();
        RecordingListener listener = new RecordingListener();

        component.addLifeCycleListener(listener);

        component.start();
        component.stop();

        assertThat(component.isStopped()).isTrue();
        assertThat(component.events).containsExactly("doStart", "doStop");
        assertThat(listener.events).containsExactly("starting", "started", "stopping", "stopped");
    }

    @Test
    void removedListenerDoesNotReceiveLaterTransitions() throws Exception {
        RecordingLifeCycle component = new RecordingLifeCycle();
        RecordingListener listener = new RecordingListener();

        component.addLifeCycleListener(listener);
        component.removeLifeCycleListener(listener);

        component.start();
        component.stop();

        assertThat(listener.events).isEmpty();
    }

    @Test
    void failingStartMarksComponentFailedAndNotifiesListener() {
        RecordingLifeCycle component = new RecordingLifeCycle();
        component.failOnStart = true;
        RecordingListener listener = new RecordingListener();
        component.addLifeCycleListener(listener);

        assertThatThrownBy(component::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("start failed");

        assertThat(component.isFailed()).isTrue();
        assertThat(listener.events).containsExactly("starting", "failure:IllegalStateException");
    }

    private static final class RecordingLifeCycle extends AbstractLifeCycle {
        private final List<String> events = new ArrayList<String>();
        private boolean failOnStart;

        @Override
        protected void doStart() {
            events.add("doStart");
            if (failOnStart) {
                throw new IllegalStateException("start failed");
            }
        }

        @Override
        protected void doStop() {
            events.add("doStop");
        }
    }

    private static final class RecordingListener implements LifeCycle.Listener {
        private final List<String> events = new ArrayList<String>();

        @Override
        public void lifeCycleStarting(LifeCycle event) {
            assertThat(event.isStarting()).isTrue();
            events.add("starting");
        }

        @Override
        public void lifeCycleStarted(LifeCycle event) {
            assertThat(event.isStarted()).isTrue();
            events.add("started");
        }

        @Override
        public void lifeCycleFailure(LifeCycle event, Throwable cause) {
            assertThat(event.isFailed()).isTrue();
            events.add("failure:" + cause.getClass().getSimpleName());
        }

        @Override
        public void lifeCycleStopping(LifeCycle event) {
            assertThat(event.isStopping()).isTrue();
            events.add("stopping");
        }

        @Override
        public void lifeCycleStopped(LifeCycle event) {
            assertThat(event.isStopped()).isTrue();
            events.add("stopped");
        }
    }
}
