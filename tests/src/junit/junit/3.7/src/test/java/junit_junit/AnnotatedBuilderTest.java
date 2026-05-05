/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.RunnerBuilder;

public class AnnotatedBuilderTest {

    @Test
    void createsRunnerUsingClassConstructor() {
        ClassOnlyRunner.createdFor = null;

        Result result = new JUnitCore().run(ClassConstructorCase.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(ClassOnlyRunner.createdFor).isEqualTo(ClassConstructorCase.class);
    }

    @Test
    void createsRunnerUsingClassAndRunnerBuilderConstructor() {
        BuilderAwareRunner.createdFor = null;
        BuilderAwareRunner.receivedSuiteBuilder = null;

        Result result = new JUnitCore().run(ClassAndRunnerBuilderConstructorCase.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(BuilderAwareRunner.createdFor).isEqualTo(ClassAndRunnerBuilderConstructorCase.class);
        assertThat(BuilderAwareRunner.receivedSuiteBuilder).isNotNull();
    }

    @RunWith(ClassOnlyRunner.class)
    public static class ClassConstructorCase {
    }

    @RunWith(BuilderAwareRunner.class)
    public static class ClassAndRunnerBuilderConstructorCase {
    }

    public abstract static class RecordingRunner extends Runner {
        private final Description description;

        protected RecordingRunner(Class<?> testClass) {
            description = Description.createTestDescription(testClass, "syntheticTest");
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

    public static class ClassOnlyRunner extends RecordingRunner {
        private static Class<?> createdFor;

        public ClassOnlyRunner(Class<?> testClass) {
            super(testClass);
            createdFor = testClass;
        }
    }

    public static class BuilderAwareRunner extends RecordingRunner {
        private static Class<?> createdFor;
        private static RunnerBuilder receivedSuiteBuilder;

        public BuilderAwareRunner(Class<?> testClass, RunnerBuilder suiteBuilder) {
            super(testClass);
            createdFor = testClass;
            receivedSuiteBuilder = suiteBuilder;
        }
    }
}
