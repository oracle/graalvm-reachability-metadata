/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

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
    void runsClassAnnotatedWithRunnerUsingClassConstructor() {
        ClassConstructorRunner.constructedFor.clear();
        ClassConstructorRunner.ranFor.clear();

        Result result = JUnitCore.runClasses(UsesClassConstructorRunner.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(ClassConstructorRunner.constructedFor).containsExactly(UsesClassConstructorRunner.class);
        assertThat(ClassConstructorRunner.ranFor).containsExactly(UsesClassConstructorRunner.class);
    }

    @Test
    void runsClassAnnotatedWithRunnerUsingClassAndRunnerBuilderConstructor() {
        ClassAndBuilderConstructorRunner.constructedFor.clear();
        ClassAndBuilderConstructorRunner.receivedBuilderTypes.clear();
        ClassAndBuilderConstructorRunner.ranFor.clear();

        Result result = JUnitCore.runClasses(UsesClassAndBuilderConstructorRunner.class);

        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(ClassAndBuilderConstructorRunner.constructedFor)
                .containsExactly(UsesClassAndBuilderConstructorRunner.class);
        assertThat(ClassAndBuilderConstructorRunner.receivedBuilderTypes).hasSize(1);
        assertThat(RunnerBuilder.class.isAssignableFrom(ClassAndBuilderConstructorRunner.receivedBuilderTypes.get(0)))
                .isTrue();
        assertThat(ClassAndBuilderConstructorRunner.ranFor)
                .containsExactly(UsesClassAndBuilderConstructorRunner.class);
    }

    @RunWith(ClassConstructorRunner.class)
    public static final class UsesClassConstructorRunner {
    }

    @RunWith(ClassAndBuilderConstructorRunner.class)
    public static final class UsesClassAndBuilderConstructorRunner {
    }

    public static final class ClassConstructorRunner extends RecordingRunner {
        private static final List<Class<?>> constructedFor = new ArrayList<>();
        private static final List<Class<?>> ranFor = new ArrayList<>();

        public ClassConstructorRunner(Class<?> testClass) {
            super(testClass);
            constructedFor.add(testClass);
        }

        @Override
        protected void recordRun(Class<?> testClass) {
            ranFor.add(testClass);
        }
    }

    public static final class ClassAndBuilderConstructorRunner extends RecordingRunner {
        private static final List<Class<?>> constructedFor = new ArrayList<>();
        private static final List<Class<?>> receivedBuilderTypes = new ArrayList<>();
        private static final List<Class<?>> ranFor = new ArrayList<>();

        public ClassAndBuilderConstructorRunner(Class<?> testClass, RunnerBuilder runnerBuilder) {
            super(testClass);
            constructedFor.add(testClass);
            receivedBuilderTypes.add(runnerBuilder.getClass());
        }

        @Override
        protected void recordRun(Class<?> testClass) {
            ranFor.add(testClass);
        }
    }

    public abstract static class RecordingRunner extends Runner {
        private final Class<?> testClass;
        private final Description description;

        protected RecordingRunner(Class<?> testClass) {
            this.testClass = testClass;
            this.description = Description.createTestDescription(testClass, "customRunnerTest");
        }

        @Override
        public Description getDescription() {
            return description;
        }

        @Override
        public void run(RunNotifier notifier) {
            notifier.fireTestStarted(description);
            try {
                recordRun(testClass);
            } finally {
                notifier.fireTestFinished(description);
            }
        }

        protected abstract void recordRun(Class<?> testClass);
    }
}
