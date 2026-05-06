/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.statusFile
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

public class KtorServerStatusPagesJvmTest {
    @Test
    fun exceptionHandlersSelectTheNearestRegisteredExceptionType(): Unit = testApplication {
        application {
            install(StatusPages) {
                exception<RuntimeException> { call, cause ->
                    call.respondText(
                        text = "runtime:${cause.message}",
                        status = HttpStatusCode.BadRequest,
                    )
                }
                exception<IllegalArgumentException> { call, cause ->
                    call.respondText(
                        text = "argument:${cause.message}",
                        status = HttpStatusCode.Conflict,
                    )
                }
            }
            routing {
                get("/specific-exception") {
                    throw SpecificArgumentException("bad input")
                }
                get("/runtime-exception") {
                    throw IllegalStateException("bad state")
                }
            }
        }

        val specificResponse = client.get("/specific-exception")
        assertThat(specificResponse.status).isEqualTo(HttpStatusCode.Conflict)
        assertThat(specificResponse.bodyAsText()).isEqualTo("argument:bad input")

        val runtimeResponse = client.get("/runtime-exception")
        assertThat(runtimeResponse.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(runtimeResponse.bodyAsText()).isEqualTo("runtime:bad state")
    }

    @Test
    fun statusHandlersMapMultipleStatusCodesToCustomResponses(): Unit = testApplication {
        application {
            install(StatusPages) {
                status(HttpStatusCode.NotFound, HttpStatusCode.Gone) { call, status ->
                    call.response.headers.append("X-Mapped-Status", status.value.toString())
                    call.respondText(
                        text = "mapped:${status.value}:${status.description}",
                        status = HttpStatusCode.OK,
                    )
                }
            }
            routing {
                get("/not-found") {
                    call.respond(HttpStatusCode.NotFound)
                }
                get("/gone") {
                    call.respond(HttpStatusCode.Gone)
                }
            }
        }

        val notFoundResponse = client.get("/not-found")
        assertThat(notFoundResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(notFoundResponse.headers["X-Mapped-Status"]).isEqualTo("404")
        assertThat(notFoundResponse.bodyAsText()).isEqualTo("mapped:404:Not Found")

        val goneResponse = client.get("/gone")
        assertThat(goneResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(goneResponse.headers["X-Mapped-Status"]).isEqualTo("410")
        assertThat(goneResponse.bodyAsText()).isEqualTo("mapped:410:Gone")
    }

    @Test
    fun statusHandlerResponseDoesNotTriggerAnotherStatusHandler(): Unit = testApplication {
        val okHandlerCalls: AtomicInteger = AtomicInteger()

        application {
            install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, status ->
                    call.response.headers.append("X-Original-Status", status.value.toString())
                    call.respondText(
                        text = "handled without recursion",
                        status = HttpStatusCode.OK,
                    )
                }
                status(HttpStatusCode.OK) { call, _ ->
                    okHandlerCalls.incrementAndGet()
                    call.respondText(
                        text = "recursive handler should not run",
                        status = HttpStatusCode.Accepted,
                    )
                }
            }
            routing {
                get("/recursion-guarded-status") {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        val response = client.get("/recursion-guarded-status")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers["X-Original-Status"]).isEqualTo("404")
        assertThat(response.bodyAsText()).isEqualTo("handled without recursion")
        assertThat(okHandlerCalls.get()).isZero()
    }

    @Test
    fun statusContextExposesOriginalContentAndPreservesOutgoingHeaders(): Unit = testApplication {
        val observedContentType: AtomicReference<ContentType?> = AtomicReference()
        val observedContentLength: AtomicReference<Long?> = AtomicReference()

        application {
            install(StatusPages) {
                status(HttpStatusCode.Forbidden) { status ->
                    observedContentType.set(content.contentType)
                    observedContentLength.set(content.contentLength)
                    call.respondText(
                        text = "context:${status.value}:${content.status?.value}:${content.contentLength}",
                        contentType = ContentType.Text.Plain,
                        status = status,
                    )
                }
            }
            routing {
                get("/forbidden") {
                    val body = "secret".encodeToByteArray()
                    call.respond(
                        object : OutgoingContent.ByteArrayContent() {
                            override val contentType: ContentType = ContentType.Text.Plain
                            override val contentLength: Long = body.size.toLong()
                            override val status: HttpStatusCode = HttpStatusCode.Forbidden
                            override val headers = headersOf("X-Original-Header", "preserved")

                            override fun bytes(): ByteArray = body
                        },
                    )
                }
            }
        }

        val response = client.get("/forbidden")
        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(response.headers["X-Original-Header"]).isEqualTo("preserved")
        assertThat(response.headers[HttpHeaders.ContentType]).startsWith(ContentType.Text.Plain.toString())
        assertThat(response.bodyAsText()).isEqualTo("context:403:403:6")
        assertThat(observedContentType.get()).isEqualTo(ContentType.Text.Plain)
        assertThat(observedContentLength.get()).isEqualTo(6L)
    }

    @Test
    fun statusHandlersUseStatusSetOnResponseWhenOutgoingContentHasNoStatus(): Unit = testApplication {
        application {
            install(StatusPages) {
                status(HttpStatusCode.PaymentRequired) { call, status ->
                    call.response.headers.append("X-Detected-Status", status.value.toString())
                    call.respondText(
                        text = "handled response status:${status.description}",
                        status = HttpStatusCode.OK,
                    )
                }
            }
            routing {
                get("/response-status-only") {
                    call.response.status(HttpStatusCode.PaymentRequired)
                    call.respondText("original response body")
                }
            }
        }

        val response = client.get("/response-status-only")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers["X-Detected-Status"]).isEqualTo(HttpStatusCode.PaymentRequired.value.toString())
        assertThat(response.bodyAsText()).isEqualTo(
            "handled response status:${HttpStatusCode.PaymentRequired.description}",
        )
    }

    @Test
    fun unhandledHandlerRespondsBeforeApplicationFallback(): Unit = testApplication {
        application {
            install(StatusPages) {
                unhandled { call ->
                    call.respondText(
                        text = "unhandled:${call.request.path()}",
                        status = HttpStatusCode.NotFound,
                    )
                }
            }
            routing {
                get("/handled") {
                    call.respondText("handled")
                }
            }
        }

        val handledResponse = client.get("/handled")
        assertThat(handledResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(handledResponse.bodyAsText()).isEqualTo("handled")

        val missingResponse = client.get("/missing/path")
        assertThat(missingResponse.status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(missingResponse.bodyAsText()).isEqualTo("unhandled:/missing/path")
    }

    @Test
    fun statusFileUsesStatusCodePatternAndFailsClosedForMissingResources(): Unit = testApplication {
        application {
            install(StatusPages) {
                statusFile(HttpStatusCode.NotFound, filePattern = "missing-error-#.html")
            }
            routing {
                get("/resource-backed-status") {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        val response = client.get("/resource-backed-status")
        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        assertThat(response.bodyAsText()).isEmpty()
    }

    @Test
    fun exceptionsThrownByStatusHandlersAreProcessedByExceptionHandlers(): Unit = testApplication {
        application {
            install(StatusPages) {
                status(HttpStatusCode.BadRequest) { _, status ->
                    throw IllegalArgumentException("status handler failed for ${status.value}")
                }
                exception<IllegalArgumentException> { call, cause ->
                    call.respondText(
                        text = "recovered:${cause.message}",
                        status = HttpStatusCode.UnprocessableEntity,
                    )
                }
            }
            routing {
                get("/bad-request") {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }

        val response = client.get("/bad-request")
        assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
        assertThat(response.bodyAsText()).isEqualTo("recovered:status handler failed for 400")
    }

    private class SpecificArgumentException(message: String) : IllegalArgumentException(message)
}
