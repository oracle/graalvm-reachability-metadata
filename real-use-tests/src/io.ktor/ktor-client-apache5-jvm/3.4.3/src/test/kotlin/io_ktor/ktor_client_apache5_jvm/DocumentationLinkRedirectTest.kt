/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpResponseRedirectEvent
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP redirect following with the `HttpRedirect` plugin on the `Apache5` engine.
 *
 * A documentation portal client resolves short links that hop through permanent, temporary,
 * and relative redirects before landing on the current page, observing every hop through the
 * client's redirect event. Form submissions answered with `303 See Other` are left to the
 * application under the plugin's default method check, and a client with redirect following
 * disabled sees the raw redirect so it can audit where a link points.
 */
class DocumentationLinkRedirectTest {

    /** One redirect hop the client reported: the status it followed and where it was sent. */
    data class Hop(val status: HttpStatusCode, val location: String)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val submittedFeedback: MutableList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/go/install") {
                    call.respondRedirect("/docs/v2/install?lang=${call.request.queryParameters["lang"]}", permanent = false)
                }
                get("/docs/v2/install") {
                    call.respondRedirect("/docs/v3/setup/install?lang=${call.request.queryParameters["lang"]}", permanent = true)
                }
                get("/docs/v3/setup/install") {
                    call.response.header("X-Doc-Version", "3")
                    call.respondText("Install guide (v3) in ${call.request.queryParameters["lang"]}")
                }
                get("/docs/v3/setup/latest") {
                    call.response.header(HttpHeaders.Location, "changelog")
                    call.respondText("", status = HttpStatusCode.TemporaryRedirect)
                }
                get("/docs/v3/setup/changelog") {
                    call.respondText("Changelog")
                }
                post("/feedback") {
                    submittedFeedback.add(call.receiveText())
                    call.response.header(HttpHeaders.Location, "/feedback/thanks")
                    call.respondText("", status = HttpStatusCode.SeeOther)
                }
                get("/feedback/thanks") {
                    call.respondText("Thanks for ${submittedFeedback.size} note(s)")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun shortLinkFollowsChainOfRedirectsToCurrentPage() = runBlocking {
        val portal: String = "http://127.0.0.1:$port"
        val hops: MutableList<Hop> = CopyOnWriteArrayList()
        HttpClient(Apache5).use { client: HttpClient ->
            client.monitor.subscribe(HttpResponseRedirectEvent) { response: HttpResponse ->
                hops.add(Hop(response.status, response.headers[HttpHeaders.Location]!!))
            }

            val page: HttpResponse = client.get("$portal/go/install?lang=en")

            assertEquals(HttpStatusCode.OK, page.status)
            assertEquals("Install guide (v3) in en", page.bodyAsText())
            assertEquals("3", page.headers["X-Doc-Version"])
            assertEquals("/docs/v3/setup/install", page.call.request.url.encodedPath)
            assertEquals(
                listOf(
                    Hop(HttpStatusCode.Found, "/docs/v2/install?lang=en"),
                    Hop(HttpStatusCode.MovedPermanently, "/docs/v3/setup/install?lang=en"),
                ),
                hops,
            )
        }
    }

    @Test
    fun relativeTemporaryRedirectResolvesAgainstRequestUrl() = runBlocking {
        HttpClient(Apache5) {
            install(HttpRedirect)
        }.use { client: HttpClient ->
            val changelog: HttpResponse = client.get("http://127.0.0.1:$port/docs/v3/setup/latest")

            assertEquals(HttpStatusCode.OK, changelog.status)
            assertEquals("Changelog", changelog.bodyAsText())
            assertEquals("/docs/v3/setup/changelog", changelog.call.request.url.encodedPath)
        }
    }

    @Test
    fun postAnsweredWithSeeOtherIsFollowedByTheApplication() = runBlocking {
        val portal: String = "http://127.0.0.1:$port"
        HttpClient(Apache5).use { client: HttpClient ->
            val submitted: HttpResponse = client.post("$portal/feedback") {
                setBody("Install guide is clear")
            }

            assertEquals(HttpStatusCode.SeeOther, submitted.status)
            assertEquals(listOf("Install guide is clear"), submittedFeedback)
            val next: String = submitted.headers[HttpHeaders.Location]!!
            assertEquals("/feedback/thanks", next)

            assertEquals("Thanks for 1 note(s)", client.get("$portal$next").bodyAsText())
        }
    }

    @Test
    fun linkAuditorSeesRawRedirectWhenFollowingIsDisabled() = runBlocking {
        val portal: String = "http://127.0.0.1:$port"
        val hops: MutableList<Hop> = CopyOnWriteArrayList()
        HttpClient(Apache5) {
            followRedirects = false
        }.use { auditor: HttpClient ->
            auditor.monitor.subscribe(HttpResponseRedirectEvent) { response: HttpResponse ->
                hops.add(Hop(response.status, response.headers[HttpHeaders.Location]!!))
            }

            val shortLink: HttpResponse = auditor.get("$portal/go/install?lang=fr")
            assertEquals(HttpStatusCode.Found, shortLink.status)
            assertEquals("/docs/v2/install?lang=fr", shortLink.headers[HttpHeaders.Location])

            val moved: HttpResponse = auditor.get("$portal/docs/v2/install?lang=fr")
            assertEquals(HttpStatusCode.MovedPermanently, moved.status)
            assertEquals("/docs/v3/setup/install?lang=fr", moved.headers[HttpHeaders.Location])

            assertTrue(hops.isEmpty())
        }
    }
}
