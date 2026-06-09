/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.Future;
import org.apache.hadoop.thirdparty.com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.Test;

public class FuturesGetCheckedTest {
    @Test
    void getCheckedWrapsFailedFutureCauseInRequestedCheckedException() {
        IOException cause = new IOException("network lookup failed");
        Future<String> failedFuture = Futures.immediateFailedFuture(cause);

        ApplicationCheckedException exception =
                assertThrows(
                        ApplicationCheckedException.class,
                        () -> Futures.getChecked(failedFuture, ApplicationCheckedException.class));

        assertEquals(cause.toString(), exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    public static class ApplicationCheckedException extends Exception {
        public ApplicationCheckedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
