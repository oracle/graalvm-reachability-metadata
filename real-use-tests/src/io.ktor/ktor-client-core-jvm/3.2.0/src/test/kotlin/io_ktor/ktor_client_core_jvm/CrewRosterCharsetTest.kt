/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.Charsets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The client `HttpPlainText` plugin negotiating, encoding and decoding text bodies by charset.
 *
 * A crew-scheduling client talks to a legacy roster service that stores names in
 * ISO-8859-1 and often omits the charset from its responses. The client registers UTF-8
 * and a lower-quality ISO-8859-1, sends notes in ISO-8859-1, and falls back to ISO-8859-1
 * when a response names no charset. The test checks the `Accept-Charset` header the plugin
 * builds, the bytes and content type the service receives for a plain and an explicitly
 * UTF-8 note, that a per-request header wins over the plugin, and that an unlabeled
 * response is decoded with the fallback.
 */
class CrewRosterCharsetTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/roster/negotiation") {
                    val accepted: String = call.request.headers[HttpHeaders.AcceptCharset] ?: "none"
                    call.respondText(accepted, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                }
                post("/roster/notes") {
                    val received: String = call.request.headers[HttpHeaders.ContentType] ?: "none"
                    val bytes: ByteArray = call.receiveChannel().toByteArray()
                    val sender: Charset = ContentType.parse(received).charset() ?: Charsets.UTF_8
                    val note: String = String(bytes, sender)
                    call.respondText(
                        "type=$received bytes=${bytes.size} note=$note",
                        ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                    )
                }
                get("/roster/legacy") {
                    call.respondBytes(LEGACY_CAPTAIN.toByteArray(Charsets.ISO_8859_1), ContentType.Text.Plain)
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
    fun rosterTextTravelsInTheNegotiatedCharsets() {
        val base: String = "http://127.0.0.1:$port"
        val client = HttpClient(CIO) {
            Charsets {
                register(Charsets.UTF_8)
                register(Charsets.ISO_8859_1, quality = 0.5f)
                sendCharset = Charsets.ISO_8859_1
                responseCharsetFallback = Charsets.ISO_8859_1
            }
        }

        client.use {
            runBlocking {
                val negotiated: String = it.get("$base/roster/negotiation").body()
                assertEquals("UTF-8,ISO-8859-1;q=0.5", negotiated)

                val pinned: String = it.get("$base/roster/negotiation") {
                    header(HttpHeaders.AcceptCharset, "UTF-8")
                }.body()
                assertEquals("UTF-8", pinned)

                val latinNote: String = it.post("$base/roster/notes") {
                    setBody(CREW_NOTE)
                }.body()
                assertEquals(
                    "type=text/plain; charset=ISO-8859-1 bytes=${CREW_NOTE.length} note=$CREW_NOTE",
                    latinNote,
                )

                val utf8Note: String = it.post("$base/roster/notes") {
                    contentType(ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                    setBody(CREW_NOTE)
                }.body()
                val utf8Size: Int = CREW_NOTE.toByteArray(Charsets.UTF_8).size
                assertEquals("type=text/plain; charset=UTF-8 bytes=$utf8Size note=$CREW_NOTE", utf8Note)

                val legacy: HttpResponse = it.get("$base/roster/legacy")
                assertNull(legacy.charset(), "the legacy service names no charset")
                assertEquals(LEGACY_CAPTAIN, legacy.body<String>())
            }
        }
    }

    private companion object {
        const val CREW_NOTE: String = "First officer Zo\u00eb M\u00fcller, purser Ren\u00e9e Brul\u00e9"
        const val LEGACY_CAPTAIN: String = "Captain \u00c5sa \u00d8rsted"
    }
}
