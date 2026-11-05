/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.util.concurrent.MoreExecutors;
import io.trino.jdbc.$internal.guava.util.concurrent.SettableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractFutureInnerUnsafeAtomicHelperAnonymous1Test {
    @Test
    void completesFutureAndNotifiesListenerOnce() throws Exception {
        SettableFuture<String> future = SettableFuture.create();
        AtomicInteger notifications = new AtomicInteger();
        future.addListener(notifications::incrementAndGet, MoreExecutors.directExecutor());
        assertTrue(future.set("complete"));
        assertFalse(future.set("ignored"));
        assertEquals("complete", future.get(10, TimeUnit.SECONDS));
        assertEquals(1, notifications.get());
    }
}
