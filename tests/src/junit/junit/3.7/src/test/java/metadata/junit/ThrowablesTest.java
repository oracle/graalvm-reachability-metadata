/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import org.junit.internal.Throwables;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThrowablesTest {
    @Test
    public void trimmedStackTraceIncludesSuppressedException() {
        String testClassName = ThrowablesTest.class.getName();
        IllegalStateException exception = new IllegalStateException("primary failure");
        IllegalArgumentException suppressed = new IllegalArgumentException("secondary failure");
        suppressed.setStackTrace(new StackTraceElement[] {
                stackTraceElement(testClassName, "suppressedFailure")
        });
        exception.addSuppressed(suppressed);
        exception.setStackTrace(new StackTraceElement[] {
                stackTraceElement(testClassName, "failsInUserCode"),
                stackTraceElement("java.lang.reflect.Method", "invoke"),
                stackTraceElement("org.junit.runner.JUnitCore", "run"),
                stackTraceElement("example.NativeImageLauncher", "main")
        });

        String trimmedStackTrace = Throwables.getTrimmedStackTrace(exception);

        assertTrue(trimmedStackTrace.contains(testClassName + ".failsInUserCode"));
        assertTrue(trimmedStackTrace.contains("Suppressed: java.lang.IllegalArgumentException: secondary failure"));
        assertTrue(trimmedStackTrace.contains(testClassName + ".suppressedFailure"));
        assertFalse(trimmedStackTrace.contains("org.junit.runner.JUnitCore.run"));
    }

    private static StackTraceElement stackTraceElement(String className, String methodName) {
        return new StackTraceElement(className, methodName, "ThrowablesTest.java", 1);
    }
}
