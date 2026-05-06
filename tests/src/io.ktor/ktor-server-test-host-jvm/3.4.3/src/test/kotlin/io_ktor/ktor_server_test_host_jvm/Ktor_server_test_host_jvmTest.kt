/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_test_host_jvm

import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ExpectedTestException
import io.ktor.server.testing.TestApplication
import io.ktor.server.testing.client.InvalidTestRequestException
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.it
import io.ktor.server.testing.on
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class KtorServerTestHostJvmTest {
    @Test
    fun builderDslInstallRegistersConfiguredApplicationPlugins() = testApplication {
        install(ConfiguredResponseHeaderPlugin) {
            headerValue = "installed by test builder"
        }
        routing {
            get("/plugin") {
                call.respondText("plugin route")
            }
        }

        val response = client.get("/plugin")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers["X-Test-Builder-Plugin"]).isEqualTo("installed by test builder")
        assertThat(response.bodyAsText()).isEqualTo("plugin route")
    }

    @Test
    fun builderDslInstallsApplicationModulesRoutesAndConfiguredClients() = testApplication {
        application {
            routing {
                get("/application-module") {
                    call.response.header("X-Module", "application")
                    call.respondText("from application module")
                }
            }
        }
        routing {
            get("/builder-route") {
                call.respondText("client=${call.request.headers["X-Client"] ?: "default"}")
            }
        }

        val defaultResponse = client.get("/application-module")
        assertThat(defaultResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(defaultResponse.headers["X-Module"]).isEqualTo("application")
        assertThat(defaultResponse.bodyAsText()).isEqualTo("from application module")

        val configuredClient = createClient {
            defaultRequest {
                header("X-Client", "configured")
            }
        }
        val configuredResponse = configuredClient.get("/builder-route")
        assertThat(configuredResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(configuredResponse.bodyAsText()).isEqualTo("client=configured")

        val missingResponse = client.get("/missing")
        assertThat(missingResponse.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun requestsExposeMethodUriQueryHeadersBodyAndConnectionDefaults() = testApplication {
        routing {
            post("/inspect") {
                val details: String = listOf(
                    "method=${call.request.local.method.value}",
                    "uri=${call.request.local.uri}",
                    "scheme=${call.request.local.scheme}",
                    "local=${call.request.local.localHost}:${call.request.local.localPort}",
                    "remote=${call.request.local.remoteHost}:${call.request.local.remotePort}",
                    "encoded=${call.request.queryParameters["encoded"]}",
                    "flag=${call.request.queryParameters["flag"]}",
                    "multi=${call.request.queryParameters.getAll("multi")?.joinToString("|")}",
                    "header=${call.request.headers["X-Test-Header"]}",
                    "body=${call.receiveText()}"
                ).joinToString(";")
                call.respondText(details)
            }
        }

        val response = client.post("/inspect?encoded=a%2Bb&flag&multi=one&multi=two") {
            header("X-Test-Header", "present")
            setBody(TextContent("request-body", ContentType.Text.Plain))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        assertThat(body).contains("method=POST")
        assertThat(body).contains("uri=/inspect")
        assertThat(body).contains("scheme=http")
        assertThat(body).contains("local=localhost:80")
        assertThat(body).contains("remote=localhost:0")
        assertThat(body).contains("encoded=a+b")
        assertThat(body).contains("flag=;")
        assertThat(body).contains("multi=one|two")
        assertThat(body).contains("header=present")
        assertThat(body).contains("body=request-body")
    }

    @Test
    fun externalServicesAreSelectedByAbsoluteAuthorityAndRejectUnknownHosts() = testApplication {
        externalServices {
            hosts("https://api.example.test", "http://mirror.example.test:8080") {
                routing {
                    get("/status") {
                        call.respondText("external:${call.request.queryParameters["from"]}")
                    }
                }
            }
        }
        routing {
            get("/status") {
                call.respondText("main")
            }
        }

        val mainResponse = client.get("/status")
        assertThat(mainResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(mainResponse.bodyAsText()).isEqualTo("main")

        val primaryExternalResponse = client.get("https://api.example.test/status?from=primary")
        assertThat(primaryExternalResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(primaryExternalResponse.bodyAsText()).isEqualTo("external:primary")

        val mirrorExternalResponse = client.get("http://mirror.example.test:8080/status?from=mirror")
        assertThat(mirrorExternalResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(mirrorExternalResponse.bodyAsText()).isEqualTo("external:mirror")

        val failure: InvalidTestRequestException = assertFailsWithType {
            client.get("https://unknown.example.test/status")
        }
        assertThat(failure).hasMessageContaining("Can not resolve request")
        assertThat(failure).hasMessageContaining("api.example.test")
    }

    @Test
    fun environmentOverridesAndDefaultConnectorsAreUsedByTheDelegatingClient() = testApplication {
        configure(overrides = {
            put("feature.message", "configured")
        })
        routing {
            get("/config") {
                val message: String = call.application.environment.config.property("feature.message").getString()
                val connection: String = "${call.request.local.scheme}:${call.request.local.localPort}"
                call.respondText("$message|$connection")
            }
        }

        val httpResponse = client.get("http://localhost/config")
        assertThat(httpResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(httpResponse.bodyAsText()).isEqualTo("configured|http:80")

        val httpsResponse = client.get("https://localhost/config")
        assertThat(httpsResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(httpsResponse.bodyAsText()).isEqualTo("configured|https:443")
    }

    @Test
    fun explicitStartBuildsApplicationAndPreventsLateConfigurationChanges() = testApplication {
        routing {
            get("/started") {
                call.respondText("started")
            }
        }

        startApplication()

        val response = client.get("/started")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("started")
        assertThat(application.developmentMode).isTrue()

        assertThatThrownBy {
            routing {
                get("/too-late") {
                    call.respondText("too late")
                }
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("already been built")
    }

    @Test
    fun explicitTestApplicationInstanceCanBeStartedStoppedAndInspected() = runBlocking {
        val testApplication = TestApplication {
            environment {
                config = MapApplicationConfig("manual.message" to "started manually")
            }
            routing {
                get("/manual") {
                    val message: String = call.application.environment.config.property("manual.message").getString()
                    call.respondText(message)
                }
            }
        }

        testApplication.start()
        try {
            val response = testApplication.client.get("/manual")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("started manually")
            assertThat(testApplication.application.environment.config.property("manual.message").getString())
                .isEqualTo("started manually")
        } finally {
            testApplication.stop()
        }
    }

    @Test
    fun exceptionHandlingCanReturnServerErrorsInsteadOfRethrowingApplicationFailures() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.test.throwOnException" to "false")
        }
        routing {
            get("/boom") {
                throw IllegalStateException("broken route")
            }
        }

        val response = client.get("/boom")
        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        assertThat(response.bodyAsText()).isEqualTo("broken route")
    }

    @Test
    fun testEnvironmentUtilityDslAndExpectedExceptionValueApiAreUsable() {
        val environment = createTestEnvironment {
            config = MapApplicationConfig(
                "ktor.deployment.environment" to "test",
                "custom.key" to "custom-value"
            )
        }
        assertThat(environment.config.property("ktor.deployment.environment").getString()).isEqualTo("test")
        assertThat(environment.config.property("custom.key").getString()).isEqualTo("custom-value")
        assertThat(environment.log).isNotNull

        var scenarioExecuted = false
        on("a scenario") {
            it("runs assertion bodies immediately") {
                scenarioExecuted = true
            }
        }
        assertThat(scenarioExecuted).isTrue()

        val expected = ExpectedTestException("expected failure")
        assertThat(expected).hasMessage("expected failure")

        val engineConfiguration = io.ktor.server.testing.TestApplicationEngine.Configuration().apply {
            shutdownGracePeriod = 123L
            shutdownTimeout = 456L
        }
        assertThat(engineConfiguration.dispatcher).isNotNull
        assertThat(engineConfiguration.shutdownGracePeriod).isEqualTo(123L)
        assertThat(engineConfiguration.shutdownTimeout).isEqualTo(456L)
    }

    private class ConfiguredResponseHeaderPluginConfig {
        var headerValue: String = "unset"
    }

    private companion object {
        val ConfiguredResponseHeaderPlugin = createApplicationPlugin(
            name = "ConfiguredResponseHeaderPlugin",
            createConfiguration = ::ConfiguredResponseHeaderPluginConfig
        ) {
            onCall { call ->
                call.response.header("X-Test-Builder-Plugin", pluginConfig.headerValue)
            }
        }
    }

    private suspend inline fun <reified T : Throwable> assertFailsWithType(
        crossinline block: suspend () -> Any?
    ): T {
        try {
            block()
        } catch (cause: Throwable) {
            assertThat(cause).isInstanceOf(T::class.java)
            return cause as T
        }
        throw AssertionError("Expected ${T::class.qualifiedName} to be thrown")
    }
}
