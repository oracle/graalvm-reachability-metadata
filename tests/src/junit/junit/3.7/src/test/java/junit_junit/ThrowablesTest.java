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
import org.junit.runner.notification.Failure;

public class ThrowablesTest {

    @Test
    void formatsTrimmedFailureTraceWithSuppressedException() {
        RuntimeException exception = new RuntimeException("primary failure");
        exception.addSuppressed(new IllegalArgumentException("extra context"));
        exception.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.example.ApplicationTest", "exercise", "ApplicationTest.java", 42),
                new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", -1),
                new StackTraceElement("org.junit.runners.model.FrameworkMethod", "invokeExplosively",
                        "FrameworkMethod.java", 59),
                new StackTraceElement("org.junit.runner.JUnitCore", "run", "JUnitCore.java", 137)
        });

        Failure failure = new Failure(Description.createTestDescription(ThrowablesTest.class,
                "formatsTrimmedFailureTraceWithSuppressedException"), exception);

        String trimmedTrace = failure.getTrimmedTrace();

        assertThat(trimmedTrace)
                .contains("java.lang.RuntimeException: primary failure")
                .contains("com.example.ApplicationTest.exercise")
                .contains("Suppressed: java.lang.IllegalArgumentException: extra context");
    }
}
