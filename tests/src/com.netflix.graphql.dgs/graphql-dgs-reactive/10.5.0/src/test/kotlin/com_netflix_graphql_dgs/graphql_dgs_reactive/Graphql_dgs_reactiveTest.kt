/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs_reactive

import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.TypeRef
import com.netflix.graphql.dgs.reactive.DgsReactiveQueryExecutor
import graphql.ExecutionResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono

public class Graphql_dgs_reactiveTest {
    @Test
    public fun documentContextConvenienceMethodExtractsNestedValues() {
        val executor: RecordingExecutor = RecordingExecutor()
        val query: String = "{ books { title pages } }"

        val document: DocumentContext? = executor.executeAndGetDocumentContext(query).block()

        assertThat(document?.read<String>("$.books[0].title")).isEqualTo("Dune")
        assertThat(document?.read<Int>("$.books[1].pages")).isEqualTo(255)
        assertThat(executor.lastQuery).isEqualTo(query)
    }

    private class RecordingExecutor : DgsReactiveQueryExecutor {
        var lastQuery: String? = null
        var lastVariables: Map<String, Any>? = null
        var lastExtensions: Map<String, Any>? = null
        var lastHeaders: HttpHeaders? = null
        var lastOperationName: String? = null

        override fun execute(
            query: String,
            variables: MutableMap<String, Any>,
            extensions: MutableMap<String, Any>,
            headers: HttpHeaders,
            operationName: String,
            serverRequest: ServerRequest,
        ): Mono<ExecutionResult> {
            lastQuery = query
            lastVariables = variables
            lastExtensions = extensions
            lastHeaders = headers
            lastOperationName = operationName
            return Mono.just(
                ExecutionResult.newExecutionResult()
                    .data(
                        mapOf(
                            "book" to mapOf("title" to "Dune"),
                            "books" to listOf(
                                mapOf("title" to "Dune", "pages" to 412),
                                mapOf("title" to "Foundation", "pages" to 255),
                            ),
                        ),
                    ).build(),
            )
        }

        override fun <T> executeAndExtractJsonPath(
            query: String,
            jsonPath: String,
            variables: MutableMap<String, Any>,
            serverRequest: ServerRequest,
        ): Mono<T> {
            lastQuery = query
            return Mono.just(JsonPath.parse(resultData()).read<T>(jsonPath))
        }

        override fun executeAndGetDocumentContext(
            query: String,
            variables: MutableMap<String, Any>,
        ): Mono<DocumentContext> {
            lastQuery = query
            return Mono.just(JsonPath.parse(resultData()))
        }

        override fun <T> executeAndExtractJsonPathAsObject(
            query: String,
            jsonPath: String,
            variables: MutableMap<String, Any>,
            clazz: Class<T>,
        ): Mono<T> = Mono.just(JsonPath.parse(resultData()).read(jsonPath, clazz))

        override fun <T> executeAndExtractJsonPathAsObject(
            query: String,
            jsonPath: String,
            variables: MutableMap<String, Any>,
            typeRef: TypeRef<T>,
        ): Mono<T> = Mono.just(JsonPath.parse(resultData()).read(jsonPath, typeRef))

        private fun resultData(): Map<String, Any> = mapOf(
            "books" to listOf(
                mapOf("title" to "Dune", "pages" to 412),
                mapOf("title" to "Foundation", "pages" to 255),
            ),
        )
    }
}
