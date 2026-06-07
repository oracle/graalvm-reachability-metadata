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
    void createsRunWithRunnerUsingClassConstructor() {
        Result result = JUnitCore.runClasses(UsesClassConstructorRunner.class);

        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isZero();
    }

    @Test
    void createsRunWithRunnerUsingClassAndRunnerBuilderConstructor() {
        Result result = JUnitCore.runClasses(UsesClassAndBuilderConstructorRunner.class);

        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isZero();
    }

    @RunWith(ClassConstructorRunner.class)
    public static class UsesClassConstructorRunner {
    }

    @RunWith(ClassAndBuilderConstructorRunner.class)
    public static class UsesClassAndBuilderConstructorRunner {
    }

    public static class ClassConstructorRunner extends SingleSyntheticTestRunner {
        public ClassConstructorRunner(Class<?> testClass) {
            super(testClass);
        }
    }

    public static class ClassAndBuilderConstructorRunner extends SingleSyntheticTestRunner {
        public ClassAndBuilderConstructorRunner(Class<?> testClass, RunnerBuilder runnerBuilder) {
            super(testClass);
            assertThat(runnerBuilder).isNotNull();
        }
    }

    public abstract static class SingleSyntheticTestRunner extends Runner {
        private final Description description;

        protected SingleSyntheticTestRunner(Class<?> testClass) {
            this.description = Description.createTestDescription(testClass, "syntheticTest");
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
