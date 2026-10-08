/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs_spring_graphql

import com.netflix.graphql.dgs.apq.AutomatedPersistedQueryCaffeineCache
import com.netflix.graphql.dgs.apq.DgsAPQSupportAutoConfiguration
import com.netflix.graphql.dgs.apq.DgsAPQSupportProperties
import com.netflix.graphql.dgs.springgraphql.autoconfig.DgsSpringGraphQLAutoConfiguration
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.parser.Parser
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.graphql.execution.RuntimeWiringConfigurer

public class Graphql_dgs_spring_graphqlServletTest {
    @Test
    public fun runtimeWiringBridgeExecutesGraphQlQuery() {
        val configurer = RuntimeWiringConfigurer { builder: RuntimeWiring.Builder ->
            builder.type("Query") { type ->
                type.dataFetcher("greeting") { environment ->
                    "Hello, ${environment.getArgument<String>("name")}"
                }
            }
        }
        val bridge =
            DgsSpringGraphQLAutoConfiguration.DgsRuntimeWiringConfigurerBridge(listOf(configurer))
        val typeDefinitions = SchemaParser().parse("type Query { greeting(name: String!): String! }")
        val schema = SchemaGenerator().makeExecutableSchema(
            typeDefinitions,
            bridge.runtimeWiring(RuntimeWiring.newRuntimeWiring()).build(),
        )

        val result = GraphQL.newGraphQL(schema).build().execute(
            ExecutionInput.newExecutionInput()
                .query("query Greeting(\u0024name: String!) { greeting(name: \u0024name) }")
                .variables(mapOf("name" to "Ada"))
                .build(),
        )

        assertEquals(emptyList<Any>(), result.errors)
        assertEquals("Hello, Ada", result.getData<Map<String, String>>()["greeting"])
    }

    @Test
    public fun persistedQueryCacheReusesParsedDocument() {
        val caffeineCache = DgsAPQSupportAutoConfiguration.APQCaffeineCacheConfiguration()
            .apqCaffeineCache(DgsAPQSupportProperties())
        val persistedQueryCache = AutomatedPersistedQueryCaffeineCache(caffeineCache)
        val executionInput = ExecutionInput.newExecutionInput().query("{ __typename }").build()
        val parsedDocument = PreparsedDocumentEntry(Parser().parseDocument("{ __typename }"))

        val first = persistedQueryCache.getPersistedQueryDocumentAsync("hash", executionInput) {
            parsedDocument
        }.join()
        val second = persistedQueryCache.getPersistedQueryDocumentAsync("hash", executionInput) {
            fail("The cached persisted query should be reused")
        }.join()

        assertSame(parsedDocument, first)
        assertSame(parsedDocument, second)
    }
}
