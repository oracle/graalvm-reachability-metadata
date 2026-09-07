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
import org.junit.runner.notification.Failure;

public class ThrowablesTest {

    @Test
    void includesSuppressedFailuresInTheTrimmedTrace() {
        Result result = JUnitCore.runClasses(FailingFixture.class);
        Failure failure = result.getFailures().get(0);

        String trace = failure.getTrimmedTrace();

        assertThat(trace).contains("primary", "Suppressed", "secondary");
    }

    public static class FailingFixture {
        public FailingFixture() {
        }

        @org.junit.Test
        public void failWithSuppressedException() {
            IllegalStateException exception = new IllegalStateException("primary");
            exception.addSuppressed(new IllegalArgumentException("secondary"));
            throw exception;
        }
    }
}
