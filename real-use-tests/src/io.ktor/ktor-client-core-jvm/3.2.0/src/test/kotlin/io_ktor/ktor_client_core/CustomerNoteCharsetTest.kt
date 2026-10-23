/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpPlainText
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.withCharset
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises plain-text charset handling when updating customer notes in a legacy Latin-1 CRM. */
class CustomerNoteCharsetTest {
    @Test
    @Timeout(60)
    fun exchangesLegacyTextWhileRespectingAnExplicitResponseCharset(): Unit = runBlocking {
        withTimeout(45_000) {
            val storedNote: AtomicReference<String> = AtomicReference("")
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    put("/customers/42/note") {
                        if (call.request.contentType().charset() != Charsets.ISO_8859_1) {
                            call.respondText("Latin-1 text required", status = HttpStatusCode.UnsupportedMediaType)
                        } else {
                            storedNote.set(call.receiveText())
                            // This older endpoint omits the charset from its response's Content-Type.
                            val acknowledgement: String = "Enregistr\u00e9: ${storedNote.get()}"
                            call.respondBytes(
                                acknowledgement.toByteArray(Charsets.ISO_8859_1),
                                ContentType.Text.Plain,
                                HttpStatusCode.Created
                            )
                        }
                    }
                    get("/customers/42/note") {
                        // The newer read endpoint explicitly declares UTF-8 instead.
                        val customerNote: String = "Zo\u00eb: ${storedNote.get()} \u2713"
                        call.respondBytes(
                            customerNote.toByteArray(Charsets.UTF_8),
                            ContentType.Text.Plain.withCharset(Charsets.UTF_8)
                        )
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val noteUrl: String = "http://127.0.0.1:$port/customers/42/note"
                HttpClient(CIO) {
                    install(HttpPlainText) {
                        register(Charsets.ISO_8859_1)
                        register(Charsets.UTF_8, quality = 0.8f)
                        sendCharset = Charsets.ISO_8859_1
                        responseCharsetFallback = Charsets.ISO_8859_1
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val note: String = "Livrer au caf\u00e9 de Montr\u00e9al"
                    val saved: HttpResponse = client.put(noteUrl) { setBody(note) }
                    assertEquals(HttpStatusCode.Created, saved.status)
                    assertEquals("Enregistr\u00e9: $note", saved.body<String>())

                    val retrieved: HttpResponse = client.get(noteUrl)
                    assertEquals(HttpStatusCode.OK, retrieved.status)
                    assertEquals("Zo\u00eb: $note \u2713", retrieved.body<String>())
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
