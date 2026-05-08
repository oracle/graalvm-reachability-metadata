/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_truth.truth;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.ComparisonFailure;
import org.junit.jupiter.api.Test;

public class PlatformTest {
    @Test
    public void stringEqualityFailureCreatesComparisonFailureWithInferredDescription() {
        String actual = "native image actual value";
        AssertionError error = null;

        try {
            assertThat(actual).isEqualTo("expected value");
        } catch (AssertionError caught) {
            error = caught;
        }

        assertNotNull(error);
        assertTrue(error instanceof ComparisonFailure);
        assertTrue(error.getMessage().contains("expected value"));
        assertTrue(error.getMessage().contains("native image actual value"));
    }

    @Test
    public void throwableFailureCleansStackTraceAndSuppressedExceptions() {
        RuntimeException actual = new RuntimeException("primary failure");
        actual.addSuppressed(new IllegalStateException("suppressed failure"));

        AssertionError error = assertThrows(
                AssertionError.class,
                () -> assertThat(actual).hasMessageThat().isEqualTo("different failure"));

        assertNotNull(error.getCause());
        assertTrue(error.getCause().getSuppressed().length > 0);
        assertTrue(error.getMessage().contains("different failure"));
        assertTrue(error.getMessage().contains("primary failure"));
    }
}
