/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseRedirectEvent
import io.ktor.client.plugins.RedirectResponseException
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
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP redirects that the OkHttp engine leaves to Ktor's `HttpRedirect` plugin instead of
 * following them inside OkHttp.
 *
 * A document portal client fetches documents through the OkHttp engine. The portal moved its
 * legacy URLs permanently, resolves share links through a chain of temporary redirects, sends
 * mirrored documents to another host, and answers uploads with a redirect to the stored copy.
 * The test checks that relative and chained redirects end at the document while the client
 * monitor reports each hop, that a bearer token is dropped when a redirect changes the
 * authority, that a POST redirect is handed back to the application, and that a client which
 * disables redirect following sees the raw redirect or a `RedirectResponseException`.
 */
class DocumentPortalRedirectTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/legacy") {
                    call.respondRedirect("docs/${call.request.queryParameters["doc"]}", permanent = true)
                }
                get("/docs/{id}") {
                    val auth: String = call.request.headers[HttpHeaders.Authorization] ?: "anonymous"
                    call.respondText("document ${call.parameters["id"]} for $auth")
                }
                get("/share/{token}/{hops}") {
                    val hops: Int = call.parameters["hops"]!!.toInt()
                    val token: String = call.parameters["token"]!!
                    if (hops == 0) {
                        call.respondRedirect("/docs/$token")
                    } else {
                        call.respondRedirect("/share/$token/${hops - 1}")
                    }
                }
                get("/mirror/{id}") {
                    call.response.header(
                        HttpHeaders.Location,
                        "http://localhost:$port/docs/${call.parameters["id"]}",
                    )
                    call.respond(HttpStatusCode.TemporaryRedirect)
                }
                post("/uploads") {
                    val name: String = call.receiveText()
                    call.response.header(HttpHeaders.Location, "/docs/$name")
                    call.respond(HttpStatusCode.Found)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun followsRedirectsThroughTheKtorPluginAndStripsCredentialsAcrossHosts() {
        val hops: MutableList<String> = CopyOnWriteArrayList()
        val client = HttpClient(OkHttp)
        client.monitor.subscribe(HttpResponseRedirectEvent) { response: HttpResponse ->
            hops += "${response.status.value} ${response.headers[HttpHeaders.Location]}"
        }
        client.use {
            runBlocking {
                val moved: HttpResponse = client.get("$baseUrl/legacy?doc=handbook")
                assertEquals(HttpStatusCode.OK, moved.status)
                assertEquals("/docs/handbook", moved.call.request.url.encodedPath)
                assertTrue(moved.call.request.url.parameters.isEmpty())
                assertEquals("document handbook for anonymous", moved.bodyAsText())
                assertEquals(listOf("301 docs/handbook"), hops)

                hops.clear()
                val shared: HttpResponse = client.get("$baseUrl/share/roadmap/2")
                assertEquals("document roadmap for anonymous", shared.bodyAsText())
                assertEquals(
                    listOf("302 /share/roadmap/1", "302 /share/roadmap/0", "302 /docs/roadmap"),
                    hops,
                )

                val sameHost: HttpResponse = client.get("$baseUrl/share/contract/0") {
                    bearerAuth(TOKEN)
                }
                assertEquals("document contract for Bearer $TOKEN", sameHost.bodyAsText())

                val mirrored: HttpResponse = client.get("$baseUrl/mirror/contract") {
                    bearerAuth(TOKEN)
                }
                assertEquals(HttpStatusCode.OK, mirrored.status)
                assertEquals("localhost", mirrored.call.request.url.host)
                assertEquals("document contract for anonymous", mirrored.bodyAsText())

                hops.clear()
                val upload: HttpResponse = client.post("$baseUrl/uploads") { setBody("minutes") }
                assertEquals(HttpStatusCode.Found, upload.status)
                assertEquals("/docs/minutes", upload.headers[HttpHeaders.Location])
                assertTrue(hops.isEmpty(), hops.toString())
            }
        }
    }

    @Test
    fun clientThatDisablesRedirectFollowingSeesTheRawRedirect() {
        HttpClient(OkHttp) { followRedirects = false }.use { client: HttpClient ->
            runBlocking {
                val raw: HttpResponse = client.get("$baseUrl/legacy?doc=handbook")
                assertEquals(HttpStatusCode.MovedPermanently, raw.status)
                assertEquals("docs/handbook", raw.headers[HttpHeaders.Location])
            }
        }

        HttpClient(OkHttp) {
            followRedirects = false
            expectSuccess = true
        }.use { client: HttpClient ->
            runBlocking {
                val failure: RedirectResponseException = assertThrows {
                    client.get("$baseUrl/share/roadmap/0")
                }
                assertEquals(HttpStatusCode.Found, failure.response.status)
                assertEquals("/docs/roadmap", failure.response.headers[HttpHeaders.Location])
            }
        }
    }

    private val baseUrl: String
        get() = "http://127.0.0.1:$port"

    private companion object {
        const val TOKEN: String = "portal-token-7"
    }
}
