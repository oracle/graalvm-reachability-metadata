/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.util.concurrent.ClosingFuture;
import com.google.common.util.concurrent.FluentFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class ClosingFutureTest {
    @Test
    void finishToFutureClosesCapturedResource() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();

        FluentFuture<String> result =
                ClosingFuture.submit(
                                closer -> {
                                    AutoCloseable closeable = () -> closed.set(true);
                                    closer.eventuallyClose(closeable, directExecutor());
                                    return "computed";
                                },
                                directExecutor())
                        .finishToFuture();

        assertEquals("computed", result.get(1, TimeUnit.SECONDS));
        assertTrue(closed.get());
    }
}
