/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

public class FuturesGetCheckedTest {
    @Test
    void getCheckedWrapsFailedFutureWithCheckedExceptionConstructor() {
        IOException cause = new IOException("network unavailable");
        Future<String> failedFuture = Futures.immediateFailedFuture(cause);

        WrappedCheckedException thrown =
                assertThrows(
                        WrappedCheckedException.class,
                        () -> Futures.getChecked(failedFuture, WrappedCheckedException.class));

        assertEquals(cause.toString(), thrown.getMessage());
        assertSame(cause, thrown.getCause());
    }

    public static class WrappedCheckedException extends Exception {
        public WrappedCheckedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
