/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.util.concurrent.Futures;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FuturesGetCheckedTest {
    @Test
    void convertsAFutureFailureToTheRequestedCheckedException() {
        assertThatThrownBy(
            () -> Futures.getChecked(
                Futures.immediateFailedFuture(new IllegalStateException("failure")),
                CheckedFailure.class
            )
        )
            .isInstanceOf(CheckedFailure.class)
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    public static class CheckedFailure extends Exception {
        public CheckedFailure(String message, Throwable cause) {
            super(message, cause);
        }

        public CheckedFailure(Throwable cause) {
            super(cause);
        }
    }
}
