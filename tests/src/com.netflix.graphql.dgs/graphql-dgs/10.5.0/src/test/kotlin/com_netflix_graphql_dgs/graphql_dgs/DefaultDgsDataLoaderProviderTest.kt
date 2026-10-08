/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com_netflix_graphql_dgs.graphql_dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.DgsDispatchPredicate
import com.netflix.graphql.dgs.internal.DefaultDgsDataLoaderProvider
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderRegistry
import org.dataloader.registries.DispatchPredicate
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class DefaultDgsDataLoaderProviderTest {
    @Test
    fun discoversDataLoaderFieldsAndComponentsThroughSpring() {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, "graphql-dgs-test").apply { isDaemon = true }
        }
        val executor = Executors.newSingleThreadScheduledExecutor(threadFactory)
        try {
            AnnotationConfigApplicationContext().use { context ->
                context.beanFactory.registerSingleton("fieldDataLoader", FieldDataLoaderComponent())
                context.beanFactory.registerSingleton("componentDataLoader", ComponentDataLoader())
                context.refresh()

                val provider = DefaultDgsDataLoaderProvider(context, scheduledExecutorService = executor)
                provider.findDataLoaders()
                val registry = provider.buildRegistry()

                assertThat(registry.keys).containsExactlyInAnyOrder("fieldLoader", "componentLoader")
                assertLoadedValue(registry, "fieldLoader", "field-value")
                assertLoadedValue(registry, "componentLoader", "component-value")
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertLoadedValue(registry: DataLoaderRegistry, name: String, expected: String) {
        val result = registry.getDataLoader<String, String>(name).load("key")
        registry.dispatchAll()
        assertThat(result.join()).isEqualTo(expected)
    }
}

@DgsComponent
class FieldDataLoaderComponent {
    @JvmField
    @field:DgsDataLoader(name = "fieldLoader")
    val loader: BatchLoader<String, String> = BatchLoader { keys ->
        CompletableFuture.completedFuture(keys.map { "field-value" })
    }
}

@DgsDataLoader(name = "componentLoader")
class ComponentDataLoader : BatchLoader<String, String> {
    @JvmField
    @field:DgsDispatchPredicate
    val dispatchPredicate: DispatchPredicate = DispatchPredicate.DISPATCH_ALWAYS

    override fun load(keys: List<String>): CompletionStage<List<String>> =
        CompletableFuture.completedFuture(keys.map { "component-value" })
}
