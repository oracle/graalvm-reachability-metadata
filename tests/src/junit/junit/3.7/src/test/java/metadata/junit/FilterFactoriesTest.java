/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.internal.JUnitSystem;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.FilterFactory;
import org.junit.runner.FilterFactoryParams;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterFactoriesTest implements FilterFactory {
    private static boolean constructorCalled;
    private static String filterArgs;
    private static Description topLevelDescription;

    public FilterFactoriesTest() {
        constructorCalled = true;
    }

    @Test
    public void createsCommandLineFilterFactoryWithNoArgumentConstructor() throws Exception {
        resetFactoryState();

        Result result = runJUnitMain(
                "--filter",
                FilterFactoriesTest.class.getName() + "=selectedTest",
                FilterFactoriesTest.class.getName());

        assertTrue(result.wasSuccessful(), result.getFailures().toString());
        assertEquals(1, result.getRunCount());
        assertTrue(constructorCalled);
        assertEquals("selectedTest", filterArgs);
        assertNotNull(topLevelDescription);
        assertTrue(hasDescriptionForClass(topLevelDescription, FilterFactoriesTest.class), topLevelDescription.toString());
    }

    private static boolean hasDescriptionForClass(Description description, Class<?> testClass) {
        if (testClass.getName().equals(description.getClassName())) {
            return true;
        }
        for (Description child : description.getChildren()) {
            if (hasDescriptionForClass(child, testClass)) {
                return true;
            }
        }
        return false;
    }

    @org.junit.Test
    public void selectedTest() {
    }

    @org.junit.Test
    public void unselectedTest() {
    }

    @Override
    public Filter createFilter(FilterFactoryParams params) {
        filterArgs = params.getArgs();
        topLevelDescription = params.getTopLevelDescription();
        return new MethodNameFilter(filterArgs);
    }

    private static void resetFactoryState() {
        constructorCalled = false;
        filterArgs = null;
        topLevelDescription = null;
    }

    private static Result runJUnitMain(String... args) throws Exception {
        Method runMain = JUnitCore.class.getDeclaredMethod("runMain", JUnitSystem.class, String[].class);
        runMain.setAccessible(true);
        try {
            return (Result) runMain.invoke(new JUnitCore(), new CapturingJUnitSystem(), args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new AssertionError(cause);
        }
    }

    public static final class MethodNameFilter extends Filter {
        private final String methodName;

        private MethodNameFilter(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public boolean shouldRun(Description description) {
            if (description.isTest()) {
                return methodName.equals(description.getMethodName());
            }
            for (Description child : description.getChildren()) {
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

    private static final class CapturingJUnitSystem implements JUnitSystem {
        private final PrintStream out = new PrintStream(new ByteArrayOutputStream());

        @Override
        public void exit(int code) {
            throw new AssertionError("runMain must not exit");
        }

        @Override
        public PrintStream out() {
            return out;
        }
    }
}
