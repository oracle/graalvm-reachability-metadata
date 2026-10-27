/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.DataFetchingEnvironmentArgumentResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.ExecutionInput
import graphql.GraphQL
import org.assertj.core.api.Assertions.assertThat
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class DgsDataFetchingEnvironmentTest {
    @Test
    fun loadsAFieldAnnotatedDataLoaderFromAQueryEnvironment() {
        AnnotationConfigApplicationContext().use { context ->
            val dataLoader = FieldLookupDataLoader()
            context.beanFactory.registerSingleton("dataLoader", dataLoader)
            context.beanFactory.registerSingleton("query", DataLoaderQuery())
            context.refresh()

            val dataLoaderRegistry = DataLoaderRegistry()
            dataLoaderRegistry.register("fieldLookup", DataLoaderFactory.newDataLoader(dataLoader.loader))
            val schema =
                DgsSchemaProvider(
                    context,
                    Optional.empty(),
                    Optional.empty(),
                    methodDataFetcherFactory = MethodDataFetcherFactory(
                        listOf(
                            DataFetchingEnvironmentArgumentResolver(context),
                            InputArgumentResolver(DefaultInputObjectMapper()),
                        ),
                    ),
                ).schema(
                    """
                    type Query {
                        loadedValue: String!
                    }
                    """.trimIndent(),
                ).graphQLSchema

            val result = GraphQL.newGraphQL(schema).build().execute(
                ExecutionInput.newExecutionInput("{ loadedValue }")
                    .dataLoaderRegistry(dataLoaderRegistry)
                    .build(),
            )

            assertThat(result.errors).isEmpty()
            assertThat(result.getData<Any>()).isEqualTo(mapOf("loadedValue" to "value-for-key"))
        }
    }
}

@DgsComponent
class DataLoaderQuery {
    @DgsQuery(field = "loadedValue")
    fun loadedValue(dfe: DgsDataFetchingEnvironment): CompletionStage<String> =
        dfe.getDataLoader<String, String>(FieldLookupDataLoader::class.java).load("key")
}

@DgsComponent
class FieldLookupDataLoader {
    @JvmField
    @field:DgsDataLoader(name = "fieldLookup")
    val loader: BatchLoader<String, String> = BatchLoader { keys ->
        CompletableFuture.completedFuture(keys.map { "value-for-$it" })
    }
}
