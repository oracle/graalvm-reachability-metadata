/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.monitor.event.AbstractSelectiveEventMonitor;
import org.apache.maven.monitor.event.DefaultEventDispatcher;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.monitor.event.EventMonitor;
import org.apache.maven.monitor.event.MavenEvents;
import org.junit.jupiter.api.Test;

public class Maven_monitorTest {
    @Test
    void mavenEventsExposeStableNamesAndGroups() {
        assertThat(MavenEvents.PHASE_EXECUTION).isEqualTo("phase-execute");
        assertThat(MavenEvents.MOJO_EXECUTION).isEqualTo("mojo-execute");
        assertThat(MavenEvents.PROJECT_EXECUTION).isEqualTo("project-execute");
        assertThat(MavenEvents.REACTOR_EXECUTION).isEqualTo("reactor-execute");

        assertThat(MavenEvents.ALL_EVENTS).containsExactly(
                MavenEvents.PHASE_EXECUTION,
                MavenEvents.MOJO_EXECUTION,
                MavenEvents.PROJECT_EXECUTION,
                MavenEvents.REACTOR_EXECUTION);
        assertThat(MavenEvents.NO_EVENTS).isEmpty();
    }

    @Test
    void defaultDispatcherForwardsStartEndAndErrorEventsToAllRegisteredMonitors() {
        EventDispatcher dispatcher = new DefaultEventDispatcher();
        RecordingEventMonitor firstMonitor = new RecordingEventMonitor("first");
        RecordingEventMonitor secondMonitor = new RecordingEventMonitor("second");
        RuntimeException failure = new RuntimeException("build failed");

        dispatcher.addEventMonitor(firstMonitor);
        dispatcher.addEventMonitor(secondMonitor);

        long beforeDispatch = System.currentTimeMillis();
        dispatcher.dispatchStart(MavenEvents.REACTOR_EXECUTION, "reactor");
        dispatcher.dispatchEnd(MavenEvents.PROJECT_EXECUTION, "demo-project");
        dispatcher.dispatchError(MavenEvents.MOJO_EXECUTION, "compiler:compile", failure);
        long afterDispatch = System.currentTimeMillis();

        assertThat(firstMonitor.events).extracting(event -> event.description()).containsExactly(
                "first:start:reactor-execute:reactor",
                "first:end:project-execute:demo-project",
                "first:error:mojo-execute:compiler:compile");
        assertThat(secondMonitor.events).extracting(event -> event.description()).containsExactly(
                "second:start:reactor-execute:reactor",
                "second:end:project-execute:demo-project",
                "second:error:mojo-execute:compiler:compile");

        assertThat(firstMonitor.events.get(2).cause).isSameAs(failure);
        assertThat(secondMonitor.events.get(2).cause).isSameAs(failure);
        assertThat(firstMonitor.events).allSatisfy(event -> assertThat(event.timestamp)
                .isBetween(beforeDispatch, afterDispatch));
        assertThat(secondMonitor.events).allSatisfy(event -> assertThat(event.timestamp)
                .isBetween(beforeDispatch, afterDispatch));
    }

    @Test
    void defaultDispatcherDoesNothingWhenNoMonitorIsRegistered() {
        EventDispatcher dispatcher = new DefaultEventDispatcher();
        IllegalStateException failure = new IllegalStateException("unused");

        assertThatCode(() -> {
            dispatcher.dispatchStart(MavenEvents.PHASE_EXECUTION, "validate");
            dispatcher.dispatchEnd(MavenEvents.PHASE_EXECUTION, "validate");
            dispatcher.dispatchError(MavenEvents.PHASE_EXECUTION, "validate", failure);
        }).doesNotThrowAnyException();
    }

    @Test
    void selectiveMonitorInvokesOnlyBoundStartEndAndErrorEvents() {
        SelectiveMonitor monitor = new SelectiveMonitor(
                new String[] {MavenEvents.PROJECT_EXECUTION},
                new String[] {MavenEvents.REACTOR_EXECUTION},
                new String[] {MavenEvents.MOJO_EXECUTION});
        RuntimeException failure = new RuntimeException("mojo failed");

        monitor.startEvent(MavenEvents.PROJECT_EXECUTION, "included-project", 100L);
        monitor.startEvent(MavenEvents.PHASE_EXECUTION, "ignored-phase", 101L);
        monitor.endEvent(MavenEvents.REACTOR_EXECUTION, "included-reactor", 200L);
        monitor.endEvent(MavenEvents.PROJECT_EXECUTION, "ignored-project", 201L);
        monitor.errorEvent(MavenEvents.MOJO_EXECUTION, "included-mojo", 300L, failure);
        monitor.errorEvent(MavenEvents.REACTOR_EXECUTION, "ignored-reactor", 301L, failure);

        assertThat(monitor.events).extracting(event -> event.description()).containsExactly(
                "selective:start:project-execute:included-project",
                "selective:end:reactor-execute:included-reactor",
                "selective:error:mojo-execute:included-mojo");
        assertThat(monitor.events).extracting(event -> event.timestamp).containsExactly(100L, 200L, 300L);
        assertThat(monitor.events.get(2).cause).isSameAs(failure);
    }

    @Test
    void selectiveMonitorCanSubscribeToEveryEventOrNoEvents() {
        SelectiveMonitor allEventsMonitor = new SelectiveMonitor(
                MavenEvents.ALL_EVENTS,
                MavenEvents.ALL_EVENTS,
                MavenEvents.ALL_EVENTS);
        SelectiveMonitor noEventsMonitor = new SelectiveMonitor(
                MavenEvents.NO_EVENTS,
                MavenEvents.NO_EVENTS,
                MavenEvents.NO_EVENTS);
        RuntimeException failure = new RuntimeException("ignored");

        allEventsMonitor.startEvent(MavenEvents.PHASE_EXECUTION, "phase", 1L);
        allEventsMonitor.endEvent(MavenEvents.MOJO_EXECUTION, "mojo", 2L);
        allEventsMonitor.errorEvent(MavenEvents.PROJECT_EXECUTION, "project", 3L, failure);
        noEventsMonitor.startEvent(MavenEvents.PHASE_EXECUTION, "phase", 1L);
        noEventsMonitor.endEvent(MavenEvents.MOJO_EXECUTION, "mojo", 2L);
        noEventsMonitor.errorEvent(MavenEvents.PROJECT_EXECUTION, "project", 3L, failure);

        assertThat(allEventsMonitor.events).extracting(event -> event.description()).containsExactly(
                "selective:start:phase-execute:phase",
                "selective:end:mojo-execute:mojo",
                "selective:error:project-execute:project");
        assertThat(noEventsMonitor.events).isEmpty();
    }

    @Test
    void defaultDispatcherUsesCurrentMonitorRegistrationsForEachDispatch() {
        EventDispatcher dispatcher = new DefaultEventDispatcher();
        RecordingEventMonitor earlyMonitor = new RecordingEventMonitor("early");
        RecordingEventMonitor lateMonitor = new RecordingEventMonitor("late");

        dispatcher.addEventMonitor(earlyMonitor);
        dispatcher.dispatchStart(MavenEvents.PHASE_EXECUTION, "validate");
        dispatcher.addEventMonitor(lateMonitor);
        dispatcher.dispatchStart(MavenEvents.PHASE_EXECUTION, "compile");

        assertThat(earlyMonitor.events).extracting(event -> event.description()).containsExactly(
                "early:start:phase-execute:validate",
                "early:start:phase-execute:compile");
        assertThat(lateMonitor.events).extracting(event -> event.description()).containsExactly(
                "late:start:phase-execute:compile");
    }

    @Test
    void selectiveMonitorSubclassCanHandleOnlySelectedCustomEvents() {
        String customEvent = "custom-build-step";
        ErrorOnlySelectiveMonitor monitor = new ErrorOnlySelectiveMonitor(customEvent);
        RuntimeException failure = new RuntimeException("custom step failed");

        assertThatCode(() -> {
            monitor.startEvent(customEvent, "prepare", 10L);
            monitor.endEvent(customEvent, "prepare", 11L);
        }).doesNotThrowAnyException();
        monitor.errorEvent(customEvent, "prepare", 12L, failure);
        monitor.errorEvent(MavenEvents.MOJO_EXECUTION, "compile", 13L, failure);

        assertThat(monitor.events).extracting(event -> event.description())
                .containsExactly("error-only:error:custom-build-step:prepare");
        assertThat(monitor.events).extracting(event -> event.timestamp).containsExactly(12L);
        assertThat(monitor.events.get(0).cause).isSameAs(failure);
    }

    private static final class RecordingEventMonitor implements EventMonitor {
        private final String name;
        private final List<ObservedEvent> events = new ArrayList<>();

        private RecordingEventMonitor(String name) {
            this.name = name;
        }

        @Override
        public void startEvent(String eventName, String target, long timestamp) {
            events.add(new ObservedEvent(name, "start", eventName, target, timestamp, null));
        }

        @Override
        public void endEvent(String eventName, String target, long timestamp) {
            events.add(new ObservedEvent(name, "end", eventName, target, timestamp, null));
        }

        @Override
        public void errorEvent(String eventName, String target, long timestamp, Throwable cause) {
            events.add(new ObservedEvent(name, "error", eventName, target, timestamp, cause));
        }
    }

    private static final class SelectiveMonitor extends AbstractSelectiveEventMonitor {
        private final List<ObservedEvent> events = new ArrayList<>();

        private SelectiveMonitor(String[] startEvents, String[] endEvents, String[] errorEvents) {
            super(startEvents, endEvents, errorEvents);
        }

        @Override
        protected void doStartEvent(String eventName, String target, long timestamp) {
            events.add(new ObservedEvent("selective", "start", eventName, target, timestamp, null));
        }

        @Override
        protected void doEndEvent(String eventName, String target, long timestamp) {
            events.add(new ObservedEvent("selective", "end", eventName, target, timestamp, null));
        }

        @Override
        protected void doErrorEvent(String eventName, String target, long timestamp, Throwable cause) {
            events.add(new ObservedEvent("selective", "error", eventName, target, timestamp, cause));
        }
    }

    private static final class ErrorOnlySelectiveMonitor extends AbstractSelectiveEventMonitor {
        private final List<ObservedEvent> events = new ArrayList<>();

        private ErrorOnlySelectiveMonitor(String eventName) {
            super(new String[] {eventName}, new String[] {eventName}, new String[] {eventName});
        }

        @Override
        protected void doErrorEvent(String eventName, String target, long timestamp, Throwable cause) {
            events.add(new ObservedEvent("error-only", "error", eventName, target, timestamp, cause));
        }
    }

    private static final class ObservedEvent {
        private final String monitorName;
        private final String kind;
        private final String eventName;
        private final String target;
        private final long timestamp;
        private final Throwable cause;

        private ObservedEvent(String monitorName, String kind, String eventName, String target, long timestamp,
                Throwable cause) {
            this.monitorName = monitorName;
            this.kind = kind;
            this.eventName = eventName;
            this.target = target;
            this.timestamp = timestamp;
            this.cause = cause;
        }

        private String description() {
            return monitorName + ":" + kind + ":" + eventName + ":" + target;
        }
    }
}
