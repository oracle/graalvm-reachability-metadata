/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.internal.JUnitSystem;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.FilterFactory;
import org.junit.runner.FilterFactoryParams;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;

public class FilterFactoriesTest {
    private static final AtomicInteger FILTER_FACTORY_CONSTRUCTOR_CALLS = new AtomicInteger();
    private static final AtomicReference<String> FILTER_FACTORY_ARGUMENTS = new AtomicReference<>();
    private static final AtomicInteger FIRST_TEST_RUNS = new AtomicInteger();
    private static final AtomicInteger SECOND_TEST_RUNS = new AtomicInteger();

    @Test
    void commandLineFilterInstantiatesNamedFilterFactory() throws Throwable {
        resetObservations();

        final Result result = runJunitCommandLine(
                "--filter",
                RecordingFilterFactory.class.getName() + "=runsSelectedTest",
                FilteredCase.class.getName());

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(result.getRunCount()).isEqualTo(1);
        assertThat(FILTER_FACTORY_CONSTRUCTOR_CALLS).hasValue(1);
        assertThat(FILTER_FACTORY_ARGUMENTS).hasValue("runsSelectedTest");
        assertThat(FIRST_TEST_RUNS).hasValue(0);
        assertThat(SECOND_TEST_RUNS).hasValue(1);
    }

    private static Result runJunitCommandLine(final String... commandLineArguments) throws Throwable {
        final MethodHandle runMain = MethodHandles.privateLookupIn(JUnitCore.class, MethodHandles.lookup())
                .findVirtual(
                        JUnitCore.class,
                        "runMain",
                        MethodType.methodType(Result.class, JUnitSystem.class, String[].class));
        return (Result) runMain.invoke(new JUnitCore(), new CapturingJUnitSystem(), commandLineArguments);
    }

    private static void resetObservations() {
        FILTER_FACTORY_CONSTRUCTOR_CALLS.set(0);
        FILTER_FACTORY_ARGUMENTS.set(null);
        FIRST_TEST_RUNS.set(0);
        SECOND_TEST_RUNS.set(0);
    }

    public static final class RecordingFilterFactory implements FilterFactory {
        public RecordingFilterFactory() {
            FILTER_FACTORY_CONSTRUCTOR_CALLS.incrementAndGet();
        }

        @Override
        public Filter createFilter(final FilterFactoryParams params) {
            FILTER_FACTORY_ARGUMENTS.set(params.getArgs());
            return new MethodNameFilter(params.getArgs());
        }
    }

    public static final class MethodNameFilter extends Filter {
        private final String methodName;

        private MethodNameFilter(final String methodName) {
            this.methodName = methodName;
        }

        @Override
        public boolean shouldRun(final Description description) {
            if (description.isTest()) {
                return methodName.equals(description.getMethodName());
            }
            for (final Description child : description.getChildren()) {
                if (shouldRun(child)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String describe() {
            return "method named " + methodName;
        }
    }

    public static final class FilteredCase {
        @org.junit.Test
        public void skippedByCommandLineFilter() {
            FIRST_TEST_RUNS.incrementAndGet();
        }

        @org.junit.Test
        public void runsSelectedTest() {
            SECOND_TEST_RUNS.incrementAndGet();
        }
    }

    private static final class CapturingJUnitSystem implements JUnitSystem {
        private final PrintStream printStream = new PrintStream(new ByteArrayOutputStream());

        @Override
        public void exit(final int code) {
            throw new AssertionError("runMain should not exit with code " + code);
        }

        @Override
        public PrintStream out() {
            return printStream;
        }
    }
}
