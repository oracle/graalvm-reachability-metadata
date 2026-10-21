/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseRedirectEvent
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP redirect following over the CIO client engine (the client `HttpRedirect` plugin).
 *
 * A documentation reader fetches the employee handbook from a document store that has been
 * reorganised several times: the old path answers 301, the next one 302 with an absolute
 * `Location`, then 307 and 308, before the content is served. The reader follows the whole chain
 * with its request headers intact and logs every hop through `HttpResponseRedirectEvent`. Filing a
 * ticket with POST is answered 303, which the default client hands back to the caller instead of
 * following; a client built with `followRedirects = false` sees the raw 301; and a misconfigured
 * redirect loop is cut off by the send limit.
 */
class DocumentStoreRedirectFollowingTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    /** Request URIs in the order the store received them. */
    private val hits: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    /** `X-Reader` header value of every request the store received. */
    private val readers: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/docs/handbook") {
                    record(call.request.uri, call.request.headers["X-Reader"])
                    call.respondRedirect("/v2/docs/handbook", permanent = true)
                }
                get("/v2/docs/handbook") {
                    record(call.request.uri, call.request.headers["X-Reader"])
                    call.respondRedirect("http://127.0.0.1:$port/archive/handbook?rev=7", permanent = false)
                }
                get("/archive/handbook") {
                    record(call.request.uri, call.request.headers["X-Reader"])
                    val rev: String = call.request.queryParameters["rev"]!!
                    call.response.header(HttpHeaders.Location, "/archive/current/handbook?rev=$rev")
                    call.respond(HttpStatusCode.TemporaryRedirect)
                }
                get("/archive/current/handbook") {
                    record(call.request.uri, call.request.headers["X-Reader"])
                    val rev: String = call.request.queryParameters["rev"]!!
                    call.response.header(HttpHeaders.Location, "/content/handbook?rev=$rev")
                    call.respond(HttpStatusCode.PermanentRedirect)
                }
                get("/content/handbook") {
                    record(call.request.uri, call.request.headers["X-Reader"])
                    val rev: String = call.request.queryParameters["rev"]!!
                    call.respondText("HANDBOOK rev $rev")
                }
                post("/tickets") {
                    val title: String = call.receiveText()
                    record("${call.request.uri} $title", call.request.headers["X-Reader"])
                    call.response.header(HttpHeaders.Location, "/tickets/42")
                    call.respond(HttpStatusCode.SeeOther)
                }
                get("/tickets/42") {
                    record(call.request.uri, call.request.headers["X-Reader"])
                    call.respondText("TICKET 42")
                }
                get("/loop/a") {
                    record(call.request.uri, call.request.headers["X-Reader"])
                    call.respondRedirect("/loop/b")
                }
                get("/loop/b") {
                    record(call.request.uri, call.request.headers["X-Reader"])
                    call.respondRedirect("/loop/a")
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
    fun readerFollowsTheWholeRedirectChainToTheHandbook() {
        val redirectStatuses: ConcurrentLinkedQueue<Int> = ConcurrentLinkedQueue()
        val client = HttpClient(ClientCIO)
        val subscription: DisposableHandle = client.monitor.subscribe(HttpResponseRedirectEvent) { response: HttpResponse ->
            redirectStatuses += response.status.value
        }

        client.use {
            runBlocking {
                val response: HttpResponse = it.get("http://127.0.0.1:$port/docs/handbook") {
                    header("X-Reader", READER)
                }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("HANDBOOK rev 7", response.bodyAsText())
                assertEquals("/content/handbook", response.request.url.encodedPath)
                assertEquals("7", response.request.url.parameters["rev"])
            }
        }
        subscription.dispose()

        assertEquals(
            listOf(
                "/docs/handbook",
                "/v2/docs/handbook",
                "/archive/handbook?rev=7",
                "/archive/current/handbook?rev=7",
                "/content/handbook?rev=7",
            ),
            hits.toList(),
        )
        assertEquals(List(5) { READER }, readers.toList())
        assertEquals(listOf(301, 302, 307, 308), redirectStatuses.toList())
    }

    @Test
    fun postAnsweredWithSeeOtherIsHandedBackToTheCaller() {
        val client = HttpClient(ClientCIO)

        client.use {
            runBlocking {
                val response: HttpResponse = it.post("http://127.0.0.1:$port/tickets") {
                    header("X-Reader", READER)
                    setBody("Broken link in chapter 3")
                }
                assertEquals(HttpStatusCode.SeeOther, response.status)
                assertEquals("/tickets/42", response.headers[HttpHeaders.Location])

                val ticket: HttpResponse = it.get("http://127.0.0.1:$port${response.headers[HttpHeaders.Location]}")
                assertEquals("TICKET 42", ticket.bodyAsText())
            }
        }

        assertEquals(listOf("/tickets Broken link in chapter 3", "/tickets/42"), hits.toList())
    }

    @Test
    fun clientWithoutRedirectFollowingSeesTheRawMovedResponse() {
        val client = HttpClient(ClientCIO) {
            followRedirects = false
        }

        client.use {
            runBlocking {
                val response: HttpResponse = it.get("http://127.0.0.1:$port/docs/handbook")
                assertEquals(HttpStatusCode.MovedPermanently, response.status)
                assertEquals("/v2/docs/handbook", response.headers[HttpHeaders.Location])
            }
        }

        assertEquals(listOf("/docs/handbook"), hits.toList())
    }

    @Test
    fun redirectLoopIsCutOffByTheSendLimit() {
        val client = HttpClient(ClientCIO) {
            install(HttpSend) {
                maxSendCount = MAX_SENDS
            }
        }

        val failure: SendCountExceedException = client.use {
            assertThrows<SendCountExceedException> {
                runBlocking { it.get("http://127.0.0.1:$port/loop/a") }
            }
        }

        assertTrue(failure.message!!.contains("$MAX_SENDS"), failure.message)
        assertEquals(List(MAX_SENDS) { index -> if (index % 2 == 0) "/loop/a" else "/loop/b" }, hits.toList())
    }

    private fun record(uri: String, reader: String?) {
        hits += uri
        if (reader != null) {
            readers += reader
        }
    }

    private companion object {
        const val READER: String = "handbook-reader/1.4"
        const val MAX_SENDS: Int = 5
    }
}
