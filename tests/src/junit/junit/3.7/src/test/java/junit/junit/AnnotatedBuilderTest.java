/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.RunnerBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedBuilderTest {
    @Test
    void runsCustomRunnersWithSupportedConstructorSignatures() {
        ClassOnlyRunner.testClass = null;
        BuilderAwareRunner.testClass = null;
        BuilderAwareRunner.suiteBuilder = null;

        Result classOnlyResult = JUnitCore.runClasses(ClassOnlyRunnerTest.class);
        Result builderAwareResult = JUnitCore.runClasses(BuilderAwareRunnerTest.class);

        assertThat(classOnlyResult.getFailureCount()).isZero();
        assertThat(classOnlyResult.getRunCount()).isEqualTo(1);
        assertThat(ClassOnlyRunner.testClass).isEqualTo(ClassOnlyRunnerTest.class);
        assertThat(builderAwareResult.getFailureCount()).isZero();
        assertThat(builderAwareResult.getRunCount()).isEqualTo(1);
        assertThat(BuilderAwareRunner.testClass).isEqualTo(BuilderAwareRunnerTest.class);
        assertThat(BuilderAwareRunner.suiteBuilder).isNotNull();
    }

    @RunWith(ClassOnlyRunner.class)
    public static class ClassOnlyRunnerTest {
    }

    @RunWith(BuilderAwareRunner.class)
    public static class BuilderAwareRunnerTest {
    }

    public static class ClassOnlyRunner extends Runner {
        private static Class<?> testClass;
        private final Description description;

        public ClassOnlyRunner(Class<?> testClass) {
            ClassOnlyRunner.testClass = testClass;
            description = Description.createTestDescription(testClass, "runs");
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

    public static class BuilderAwareRunner extends Runner {
        private static Class<?> testClass;
        private static RunnerBuilder suiteBuilder;
        private final Description description;

        public BuilderAwareRunner(Class<?> testClass, RunnerBuilder suiteBuilder) {
            BuilderAwareRunner.testClass = testClass;
            BuilderAwareRunner.suiteBuilder = suiteBuilder;
            description = Description.createTestDescription(testClass, "runs");
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
