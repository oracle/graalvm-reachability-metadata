/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_sbt.junit_interface;

import com.novocode.junit.JUnitFramework;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Test;
import sbt.testing.Event;
import sbt.testing.EventHandler;
import sbt.testing.Logger;
import sbt.testing.Runner;
import sbt.testing.Selector;
import sbt.testing.Status;
import sbt.testing.SuiteSelector;
import sbt.testing.Task;
import sbt.testing.TaskDef;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JUnitTaskTest {
    @Test
    void executesTaskThroughFrameworkRunnerWithCategoryFilters() {
        JUnitFramework framework = new JUnitFramework();
        String[] args = {
                "--include-categories=" + Serializable.class.getName(),
                "--exclude-categories=" + Cloneable.class.getName()
        };
        Runner runner = framework.runner(args, new String[0], JUnitTaskTest.class.getClassLoader());
        TaskDef taskDef = new TaskDef(
                CategorizedJUnit4Fixture.class.getName(),
                framework.fingerprints()[1],
                true,
                new Selector[] {new SuiteSelector()});
        RecordingEventHandler eventHandler = new RecordingEventHandler();

        Task[] tasks = runner.tasks(new TaskDef[] {taskDef});
        Task[] nestedTasks = tasks[0].execute(eventHandler, new Logger[] {new NoOpLogger()});

        assertThat(nestedTasks).isEmpty();
        assertThat(eventHandler.events)
                .extracting(Event::status)
                .containsExactly(Status.Success);
        runner.done();
    }

    private static final class RecordingEventHandler implements EventHandler {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void handle(Event event) {
            events.add(event);
        }
    }

    private static final class NoOpLogger implements Logger {
        @Override
        public boolean ansiCodesSupported() {
            return false;
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void trace(Throwable throwable) {
        }
    }

    public static class CategorizedJUnit4Fixture {
        @org.junit.Test
        @Category(Serializable.class)
        public void categorizedJUnit4Fixture() {
            assertThat(System.getProperty("java.version")).isNotBlank();
        }
    }
}
