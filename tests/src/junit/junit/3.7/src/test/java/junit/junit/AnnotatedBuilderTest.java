/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.RunnerBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotatedBuilderTest {
    @Test
    public void buildsAnnotatedRunnerUsingClassConstructor() {
        ClassConstructorRunner.lastTestClass = null;

        Result result = new JUnitCore().run(ClassConstructorFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(1, result.getRunCount());
        assertSame(ClassConstructorFixture.class, ClassConstructorRunner.lastTestClass);
    }

    @Test
    public void buildsAnnotatedRunnerUsingClassAndRunnerBuilderConstructor() {
        BuilderConstructorRunner.lastTestClass = null;
        BuilderConstructorRunner.lastBuilder = null;

        Result result = new JUnitCore().run(BuilderConstructorFixture.class);

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(1, result.getRunCount());
        assertSame(BuilderConstructorFixture.class, BuilderConstructorRunner.lastTestClass);
        assertNotNull(BuilderConstructorRunner.lastBuilder);
    }

    @RunWith(ClassConstructorRunner.class)
    public static class ClassConstructorFixture {
    }

    @RunWith(BuilderConstructorRunner.class)
    public static class BuilderConstructorFixture {
    }

    @Ignore
    public static class ClassConstructorRunner extends Runner {
        private static Class<?> lastTestClass;

        private final Description description;

        public ClassConstructorRunner(Class<?> testClass) {
            lastTestClass = testClass;
            description = Description.createTestDescription(testClass, "runsWithClassConstructor");
        }

        @Override
        public Description getDescription() {
            return description;
        }

        @Override
        public void run(RunNotifier notifier) {
            notifier.fireTestStarted(description);
            notifier.fireTestFinished(description);
        }
    }

    @Ignore
    public static class BuilderConstructorRunner extends Runner {
        private static Class<?> lastTestClass;
        private static RunnerBuilder lastBuilder;

        private final Description description;

        public BuilderConstructorRunner(Class<?> testClass, RunnerBuilder builder) {
            lastTestClass = testClass;
            lastBuilder = builder;
            description = Description.createTestDescription(testClass, "runsWithBuilderConstructor");
        }

        @Override
        public Description getDescription() {
            return description;
        }

        @Override
        public void run(RunNotifier notifier) {
            notifier.fireTestStarted(description);
            notifier.fireTestFinished(description);
        }
    }
}
