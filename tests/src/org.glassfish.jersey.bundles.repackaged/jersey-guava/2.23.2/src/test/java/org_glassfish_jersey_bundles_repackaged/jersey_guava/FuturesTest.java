/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jersey.repackaged.com.google.common.util.concurrent.Futures;

import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

public class FuturesTest {
    @Test
    void getWrapsCheckedFailureInRequestedExceptionType() {
        final Exception cause = new Exception("backend failure");
        final Future<Object> failedFuture = Futures.immediateFailedFuture(cause);

        final FuturesCheckedException thrown = assertThrows(FuturesCheckedException.class,
                () -> Futures.get(failedFuture, FuturesCheckedException.class));

        assertThat(thrown).hasMessage(cause.toString());
        assertThat(thrown.getCause()).isSameAs(cause);
    }

    public static final class FuturesCheckedException extends Exception {
        private static final long serialVersionUID = 1L;

        public FuturesCheckedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
