/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.junit_interface;

import java.util.ArrayList;
import java.util.List;

import munit.internal.junitinterface.JUnitFramework;
import org.junit.experimental.categories.Category;
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

public class JUnitTaskTest {
    @Test
    void executeLoadsTheTaskClassAndCategoryClasses() {
        CategorizedSuite.reset();
        JUnitFramework framework = new JUnitFramework();
        String includeCategoryArgument = "--include-categories=" + FastCategory.class.getName();
        Runner runner = framework.runner(
                new String[] { includeCategoryArgument, "-b" },
                new String[0],
                JUnitTaskTest.class.getClassLoader());
        TaskDef taskDef = new TaskDef(
                CategorizedSuite.class.getName(),
                junitFingerprint(framework.fingerprints()),
                false,
                new Selector[0]);
        RecordingEventHandler eventHandler = new RecordingEventHandler();

        Task[] tasks = runner.tasks(new TaskDef[] { taskDef });
        assertThat(tasks).hasSize(1);

        Task[] nestedTasks = tasks[0].execute(eventHandler, new Logger[0]);

        assertThat(nestedTasks).isEmpty();
        assertThat(CategorizedSuite.runCount).isOne();
        assertThat(eventHandler.statuses()).containsExactly(Status.Success);
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

    public interface FastCategory {
    }

    @Category(FastCategory.class)
    public static final class CategorizedSuite {
        private static int runCount;

        @org.junit.Test
        public void succeeds() {
            runCount++;
            assertThat("categorized").startsWith("categ");
        }

        private static void reset() {
            runCount = 0;
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
}
