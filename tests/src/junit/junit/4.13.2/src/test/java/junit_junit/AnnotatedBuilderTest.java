/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class AnnotatedBuilderTest {

    @Test
    void instantiatesRunnersWithBothSupportedConstructorShapes() {
        Result result = JUnitCore.runClasses(ClassOnlyFixture.class, BuilderAwareFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(2);
    }

    @RunWith(ClassOnlyRunner.class)
    public static class ClassOnlyFixture {
        @org.junit.Test
        public void executes() {
        }
    }

    @RunWith(BuilderAwareRunner.class)
    public static class BuilderAwareFixture {
        @org.junit.Test
        public void executes() {
        }
    }

    public static class ClassOnlyRunner extends BlockJUnit4ClassRunner {
        public ClassOnlyRunner(Class<?> testClass) throws InitializationError {
            super(testClass);
        }
    }

    public static class BuilderAwareRunner extends BlockJUnit4ClassRunner {
        public BuilderAwareRunner(Class<?> testClass, RunnerBuilder runnerBuilder) throws InitializationError {
            super(testClass);
        }
    }
}
