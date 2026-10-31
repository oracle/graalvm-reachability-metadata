/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FuturesGetCheckedTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertEquals("complete", Futures.getChecked(Futures.immediateFuture("complete"), LookupException.class, 10, TimeUnit.SECONDS));
        IOException cause = new IOException("not found");
        LookupException failure = assertThrows(LookupException.class,
                () -> Futures.getChecked(Futures.immediateFailedFuture(cause), LookupException.class, 10, TimeUnit.SECONDS));
        assertSame(cause, failure.getCause());
    }

    public static class LookupException extends Exception {
        public LookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
