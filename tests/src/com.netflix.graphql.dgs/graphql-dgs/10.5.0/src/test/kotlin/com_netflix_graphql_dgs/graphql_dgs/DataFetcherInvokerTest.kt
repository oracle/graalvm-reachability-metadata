/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs

import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import graphql.GraphQL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.Optional

class DataFetcherInvokerTest {
    @Test
    fun executesJavaDataFetcherMethodsThroughSchema() {
        AnnotationConfigApplicationContext().use { context ->
            context.beanFactory.registerSingleton("dataFetcher", JavaDgsComponent())
            context.refresh()

            val methodFactory =
                MethodDataFetcherFactory(
                    listOf(InputArgumentResolver(DefaultInputObjectMapper())),
                )
            val schema =
                DgsSchemaProvider(
                    context,
                    Optional.empty(),
                    Optional.empty(),
                    methodDataFetcherFactory = methodFactory,
                ).schema(
                    """
                    type Query {
                        greeting: String!
                        echo(value: String!): String!
                    }
                    """.trimIndent(),
                ).graphQLSchema

            val result =
                GraphQL.newGraphQL(schema).build().execute(
                    """
                    query {
                        greeting
                        echo(value: "GraphQL")
                    }
                    """.trimIndent(),
                )

            assertThat(result.errors).isEmpty()
            assertThat(result.getData<Any>()).isEqualTo(
                mapOf("greeting" to "hello", "echo" to "GraphQL"),
            )
        }
    }
}
