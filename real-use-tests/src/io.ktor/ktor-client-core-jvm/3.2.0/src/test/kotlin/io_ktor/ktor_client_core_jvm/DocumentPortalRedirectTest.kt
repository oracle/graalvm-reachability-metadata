/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpResponseRedirectEvent
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The client `HttpRedirect` plugin following, refusing and bounding HTTP redirects.
 *
 * A document portal client opens short links that bounce through a permanent and a
 * temporary redirect to the current copy of a report, keeps its bearer token on same-site
 * redirects but drops it when a shared file is handed off to a separate download host,
 * does not replay a form `POST` that the server answers with `302 Found`, reports every
 * hop it followed through the client's redirect event, stops on a redirect loop once the
 * send limit is reached, and sees the raw `301` when redirects are switched off.
 */
class DocumentPortalRedirectTest {

    private lateinit var portal: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var downloads: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var portalPort: Int = 0
    private var downloadsPort: Int = 0

    @BeforeEach
    fun start() {
        downloads = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/files/{name}") {
                    val caller: String = call.request.headers[HttpHeaders.Authorization] ?: "anonymous"
                    call.respondText("file=${call.parameters["name"]} caller=$caller")
                }
            }
        }.start(wait = false)
        downloadsPort = runBlocking { downloads.engine.resolvedConnectors().first().port }

        portal = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/go/q3-report") {
                    call.respondRedirect("/docs/legacy/q3?lang=en", permanent = true)
                }
                get("/docs/legacy/q3") {
                    call.response.header(HttpHeaders.Location, "/docs/2026/q3?lang=${call.parameters["lang"]}")
                    call.respondText("moved for now", status = HttpStatusCode.TemporaryRedirect)
                }
                get("/docs/2026/q3") {
                    val ref: String = call.parameters["ref"] ?: "none"
                    call.respondText("Q3 report lang=${call.parameters["lang"]} ref=$ref")
                }
                get("/profile/old") {
                    call.respondRedirect("/profile")
                }
                get("/profile") {
                    call.respondText("profile caller=${call.request.headers[HttpHeaders.Authorization]}")
                }
                get("/share/{name}") {
                    call.respondRedirect("http://127.0.0.1:$downloadsPort/files/${call.parameters["name"]}")
                }
                post("/drafts") {
                    val title: String = call.receiveText()
                    call.respondRedirect("/drafts/17?title=$title")
                }
                get("/loop/{step}") {
                    val next: String = if (call.parameters["step"] == "a") "b" else "a"
                    call.respondRedirect("/loop/$next")
                }
            }
        }.start(wait = false)
        portalPort = runBlocking { portal.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        portal.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
        downloads.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun redirectsAreFollowedWithinTheirLimits() {
        val hops: MutableList<String> = CopyOnWriteArrayList()
        val client = HttpClient(CIO) {
            defaultRequest { url("http://127.0.0.1:$portalPort/") }
            install(HttpSend) { maxSendCount = MAX_SENDS }
        }
        client.monitor.subscribe(HttpResponseRedirectEvent) { response: HttpResponse ->
            hops.add("${response.status.value} ${response.headers[HttpHeaders.Location]}")
        }

        client.use {
            runBlocking {
                val report: HttpResponse = it.get("go/q3-report?ref=newsletter")
                assertEquals(HttpStatusCode.OK, report.status)
                assertEquals("Q3 report lang=en ref=none", report.bodyAsText())
                assertEquals("/docs/2026/q3", report.call.request.url.encodedPath)
                assertEquals(
                    listOf("301 /docs/legacy/q3?lang=en", "307 /docs/2026/q3?lang=en"),
                    hops.toList(),
                )

                val profile: HttpResponse = it.get("profile/old") { bearerAuth(TOKEN) }
                assertEquals("profile caller=Bearer $TOKEN", profile.bodyAsText())

                val shared: HttpResponse = it.get("share/handbook.pdf") { bearerAuth(TOKEN) }
                assertEquals("file=handbook.pdf caller=anonymous", shared.bodyAsText())
                assertEquals(downloadsPort, shared.call.request.url.port)

                val draft: HttpResponse = it.post("drafts") { setBody("budget") }
                assertEquals(HttpStatusCode.Found, draft.status)
                assertEquals("/drafts/17?title=budget", draft.headers[HttpHeaders.Location])

                val loop: SendCountExceedException = assertThrows(SendCountExceedException::class.java) {
                    runBlocking { it.get("loop/a") }
                }
                assertEquals("Max send count $MAX_SENDS exceeded. Consider increasing the property maxSendCount if more is required.", loop.message)
            }
        }
    }

    @Test
    fun disabledRedirectsExposeTheRawResponse() {
        val client = HttpClient(CIO) {
            followRedirects = false
            defaultRequest { url("http://127.0.0.1:$portalPort/") }
        }

        client.use {
            runBlocking {
                val shortLink: HttpResponse = it.get("go/q3-report")
                assertEquals(HttpStatusCode.MovedPermanently, shortLink.status)
                assertEquals("/docs/legacy/q3?lang=en", shortLink.headers[HttpHeaders.Location])
                assertEquals("/go/q3-report", shortLink.call.request.url.encodedPath)
            }
        }
    }

    private companion object {
        const val TOKEN: String = "portal-7f3a"
        const val MAX_SENDS: Int = 6
    }
}
