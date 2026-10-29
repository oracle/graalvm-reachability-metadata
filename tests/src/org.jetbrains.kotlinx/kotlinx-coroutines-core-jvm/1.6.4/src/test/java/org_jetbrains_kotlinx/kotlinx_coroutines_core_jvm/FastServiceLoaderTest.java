/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorCoroutineDispatcher;
import kotlinx.coroutines.ExecutorsKt;
import kotlinx.coroutines.test.TestDispatchers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FastServiceLoaderTest extends CoroutineTestSupport {
    @Test
    void discoversMainDispatcherProviderAndDispatchesToConfiguredExecutor() throws Exception {
        try (ExecutorCoroutineDispatcher dispatcher = ExecutorsKt.from(Executors.newSingleThreadExecutor())) {
            TestDispatchers.setMain(Dispatchers.INSTANCE, dispatcher);
            try {
                CompletableFuture<String> result = new CompletableFuture<>();
                Dispatchers.getMain().dispatch(EmptyCoroutineContext.INSTANCE, () -> result.complete("delivered"));
                assertEquals("delivered", result.get(10, TimeUnit.SECONDS));
            } finally {
                TestDispatchers.resetMain(Dispatchers.INSTANCE);
            }
        }
    }
}
