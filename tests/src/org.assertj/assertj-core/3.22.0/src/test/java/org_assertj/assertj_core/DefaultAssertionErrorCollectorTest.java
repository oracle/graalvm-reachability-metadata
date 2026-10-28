/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.DefaultAssertionErrorCollector;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class DefaultAssertionErrorCollectorTest {
    @Test
    void decoratesComparisonFailuresWithoutLosingExpectedAndActual() {
        DefaultAssertionErrorCollector collector = new DefaultAssertionErrorCollector();
        AssertionFailedError original = new AssertionFailedError("delivery status", "sent", "queued");
        collector.collectAssertionError(original);

        List<AssertionError> errors = collector.assertionErrorsCollected();
        assertThat(errors).hasSize(1);
        AssertionFailedError decorated = (AssertionFailedError) errors.get(0);
        assertNotSame(original, decorated);
        assertEquals(original.getExpected().getValue(), decorated.getExpected().getValue());
        assertEquals(original.getActual().getValue(), decorated.getActual().getValue());
        assertThat(decorated).hasMessageContaining("DefaultAssertionErrorCollectorTest.");
        assertArrayEquals(original.getStackTrace(), decorated.getStackTrace());
        assertThat(collector.wasSuccess()).isFalse();
    }

    @Test
    void decoratesFailuresWithoutComparisonValues() {
        DefaultAssertionErrorCollector collector = new DefaultAssertionErrorCollector();
        IllegalArgumentException cause = new IllegalArgumentException("invalid state");
        AssertionFailedError original = new AssertionFailedError("validation", cause);
        collector.collectAssertionError(original);

        AssertionFailedError decorated = (AssertionFailedError) collector.assertionErrorsCollected().get(0);
        assertNotSame(original, decorated);
        assertSame(cause, decorated.getCause());
        assertThat(decorated).hasMessageContaining("validation")
                .hasMessageContaining("DefaultAssertionErrorCollectorTest.");
        assertThat(decorated.isExpectedDefined()).isFalse();
        assertThat(decorated.isActualDefined()).isFalse();
    }

    @Test
    void preservesCustomErrorTypeCauseAndSuppressedFailures() {
        DefaultAssertionErrorCollector collector = new DefaultAssertionErrorCollector();
        List<AssertionError> notified = new ArrayList<>();
        collector.setAfterAssertionErrorCollected(notified::add);
        IllegalStateException cause = new IllegalStateException("unavailable");
        ValidationFailure original = new ValidationFailure("stock is negative", cause);
        IllegalArgumentException suppressed = new IllegalArgumentException("quantity");
        original.addSuppressed(suppressed);
        collector.collectAssertionError(original);

        AssertionError decorated = collector.assertionErrorsCollected().get(0);
        assertNotSame(original, decorated);
        assertThat(decorated).isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("stock is negative")
                .hasMessageContaining("DefaultAssertionErrorCollectorTest.");
        assertSame(cause, decorated.getCause());
        assertArrayEquals(new Throwable[]{suppressed}, decorated.getSuppressed());
        assertThat(notified).containsExactly(original);
        collector.succeeded();
        assertThat(collector.wasSuccess()).isTrue();
    }

    public static class ValidationFailure extends AssertionError {
        public ValidationFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
