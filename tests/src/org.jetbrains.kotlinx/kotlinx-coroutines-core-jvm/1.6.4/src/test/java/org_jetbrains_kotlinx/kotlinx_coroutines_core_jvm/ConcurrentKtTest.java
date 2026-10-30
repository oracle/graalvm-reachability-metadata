/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.ExecutorCoroutineDispatcher;
import kotlinx.coroutines.ExecutorsKt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrentKtTest extends CoroutineTestSupport {
    @Test
    void executorDispatcherEnablesRemovalOfCancelledScheduledTasks() throws Exception {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        assertFalse(executor.getRemoveOnCancelPolicy());
        try (ExecutorCoroutineDispatcher dispatcher = ExecutorsKt.from(executor)) {
            assertTrue(executor.getRemoveOnCancelPolicy());
            CompletableFuture<String> result = new CompletableFuture<>();
            dispatcher.dispatch(EmptyCoroutineContext.INSTANCE, () -> result.complete("dispatched"));
            assertEquals("dispatched", result.get(10, TimeUnit.SECONDS));

            ScheduledFuture<?> future = executor.schedule(() -> result.complete("cancelled"), 1, TimeUnit.DAYS);
            assertEquals(1, executor.getQueue().size());
            assertTrue(future.cancel(false));
            assertTrue(executor.getQueue().isEmpty());
            assertTrue(future.isCancelled());
            assertEquals("dispatched", result.getNow(null));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
