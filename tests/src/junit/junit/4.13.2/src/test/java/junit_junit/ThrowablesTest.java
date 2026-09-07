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
        FailingFixture.failureEnabled = true;
        Result result;
        try {
            result = JUnitCore.runClasses(FailingFixture.class);
        } finally {
            FailingFixture.failureEnabled = false;
        }
        Failure failure = result.getFailures().get(0);

        String trace = failure.getTrimmedTrace();

        assertThat(trace).contains("primary", "Suppressed", "secondary");
    }

    public static class FailingFixture {
        private static boolean failureEnabled;

        public FailingFixture() {
        }

        @org.junit.Test
        public void failWithSuppressedException() {
            if (failureEnabled) {
                IllegalStateException exception = new IllegalStateException("primary");
                exception.addSuppressed(new IllegalArgumentException("secondary"));
                throw exception;
            }
        }
    }
}
