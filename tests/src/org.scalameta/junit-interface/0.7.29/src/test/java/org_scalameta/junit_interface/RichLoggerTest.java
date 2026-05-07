/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.junit_interface;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import munit.internal.junitinterface.JUnitFramework;
import org.junit.jupiter.api.Test;
import sbt.testing.AnnotatedFingerprint;
import sbt.testing.Event;
import sbt.testing.EventHandler;
import sbt.testing.Fingerprint;
import sbt.testing.Logger;
import sbt.testing.Runner;
import sbt.testing.Selector;
import sbt.testing.Status;
import sbt.testing.Task;
import sbt.testing.TaskDef;

import static org.assertj.core.api.Assertions.assertThat;

public class RichLoggerTest {
    @Test
    void failureStackTraceHighlightsNonTestFramesThroughClassLoaderResources() {
        JUnitFramework framework = new JUnitFramework();
        Runner runner = framework.runner(
                new String[] { "+l", "-Drich.logger.test.fail=true" },
                new String[0],
                RichLoggerTest.class.getClassLoader());
        TaskDef taskDef = new TaskDef(
                FailingSuite.class.getName(),
                junitFingerprint(framework.fingerprints()),
                false,
                new Selector[0]);
        RecordingEventHandler eventHandler = new RecordingEventHandler();
        RecordingLogger logger = new RecordingLogger();

        Task[] tasks = runner.tasks(new TaskDef[] { taskDef });
        assertThat(tasks).hasSize(1);

        Task[] nestedTasks = tasks[0].execute(eventHandler, new Logger[] { logger });

        assertThat(nestedTasks).isEmpty();
        assertThat(eventHandler.statuses()).containsExactly(Status.Failure);
        assertThat(logger.errors).anyMatch(message -> message.contains("java.util.Objects.requireNonNull"));
    }

    private static Fingerprint junitFingerprint(Fingerprint[] fingerprints) {
        for (Fingerprint fingerprint : fingerprints) {
            if (fingerprint instanceof AnnotatedFingerprint) {
                AnnotatedFingerprint annotated = (AnnotatedFingerprint) fingerprint;
                if ("org.junit.Test".equals(annotated.annotationName())) {
                    return fingerprint;
                }
            }
        }
        throw new AssertionError("JUnit test fingerprint was not exposed by the framework");
    }

    public static final class FailingSuite {
        @org.junit.Test
        public void failsFromJdkFrame() {
            if (Boolean.getBoolean("rich.logger.test.fail")) {
                Objects.requireNonNull(null, "trigger RichLogger stack trace rendering");
            }
        }
    }

    private static final class RecordingEventHandler implements EventHandler {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void handle(Event event) {
            events.add(event);
        }

        private List<Status> statuses() {
            List<Status> result = new ArrayList<>();
            for (Event event : events) {
                result.add(event.status());
            }
            return result;
        }
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> errors = new ArrayList<>();
        private final List<String> otherMessages = new ArrayList<>();

        @Override
        public boolean ansiCodesSupported() {
            return true;
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }

        @Override
        public void warn(String message) {
            otherMessages.add(message);
        }

        @Override
        public void info(String message) {
            otherMessages.add(message);
        }

        @Override
        public void debug(String message) {
            otherMessages.add(message);
        }

        @Override
        public void trace(Throwable throwable) {
            errors.add(String.valueOf(throwable));
        }
    }
}
