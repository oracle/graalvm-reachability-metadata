/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs_spring_graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.RequestHeader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@SpringBootTest(
    classes = [ServletTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@AutoConfigureMockMvc
public class Graphql_dgs_spring_graphqlServletTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    public fun servletInterceptorExecutesDgsQueryWithVariables() {
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "query": "query Greeting(\u0024name: String!) { greeting(name: \u0024name) }",
                      "variables": {"name": "Ada"}
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.greeting").value("Hello, Ada"))
    }

    @Test
    public fun servletResolvesRequestHeaderArgument() {
        mockMvc.perform(
            post("/graphql")
                .header("X-Request-ID", "request-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "{ headerValue }"}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.headerValue").value("request-123"))
    }
}

@SpringBootTest(
    classes = [ReactiveTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["spring.main.web-application-type=reactive"],
)
@AutoConfigureWebTestClient
public class Graphql_dgs_spring_graphqlReactiveTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    public fun reactiveInterceptorExecutesDgsQueryWithVariables() {
        webTestClient.post()
            .uri("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "query": "query Greeting(\u0024name: String!) { greeting(name: \u0024name) }",
                  "variables": {"name": "Grace"}
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.data.greeting").isEqualTo("Hello, Grace")
    }
}

@SpringBootTest(
    classes = [PersistedQueryTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["dgs.graphql.apq.enabled=true"],
)
@AutoConfigureMockMvc
public class Graphql_dgs_spring_graphqlPersistedQueryTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    public fun persistedQueryCanBeReusedWithoutSendingQueryText() {
        val query: String = "{__typename}"
        val hash: String = sha256(query)
        val extensions: String =
            """
            "extensions": {
              "persistedQuery": {
                "version": 1,
                "sha256Hash": "$hash"
              }
            }
            """.trimIndent()

        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "query": "$query",
                      $extensions
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.__typename").value("Query"))

        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      $extensions
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.__typename").value("Query"))
    }

    private fun sha256(value: String): String {
        val digest: ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte: Byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
public class DgsSchemaTestConfiguration {
    @Bean
    public fun schemaRegistry(): TypeDefinitionRegistry =
        SchemaParser().parse(
            """
            type Query {
                greeting(name: String!): String!
                headerValue: String!
            }
            """.trimIndent(),
        )
}

@DgsComponent
public class GreetingDataFetcher {
    @DgsQuery
    public fun greeting(@InputArgument("name") name: String): String = "Hello, $name"

    @DgsQuery
    public fun headerValue(@RequestHeader("X-Request-ID") requestId: String): String = requestId
}

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(DgsSchemaTestConfiguration::class, GreetingDataFetcher::class)
public class ServletTestApplication

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(DgsSchemaTestConfiguration::class, GreetingDataFetcher::class)
public class ReactiveTestApplication

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(DgsSchemaTestConfiguration::class, GreetingDataFetcher::class)
public class PersistedQueryTestApplication
