/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;

public class ThrowablesTest {
    @Test
    void trimmedTraceIncludesSuppressedExceptionsFromJUnitFailures() {
        IllegalStateException exception = new IllegalStateException("primary failure");
        exception.addSuppressed(new IllegalArgumentException("suppressed detail"));
        String frameworkMethod = "org.junit.runners.model.FrameworkMethod";
        exception.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.example.CalculatorTest", "addsNumbers", "CalculatorTest.java", 21),
                new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 580),
                new StackTraceElement(frameworkMethod, "invokeExplosively", "FrameworkMethod.java", 59),
                new StackTraceElement("org.junit.runner.JUnitCore", "run", "JUnitCore.java", 137)
        });
        Failure failure = new Failure(
                Description.createTestDescription(ThrowablesTest.class, "addsNumbers"),
                exception);

        String trimmedTrace = failure.getTrimmedTrace();

        assertThat(trimmedTrace)
                .contains("primary failure")
                .contains("com.example.CalculatorTest.addsNumbers")
                .contains("Suppressed:")
                .contains("suppressed detail");
    }
}
