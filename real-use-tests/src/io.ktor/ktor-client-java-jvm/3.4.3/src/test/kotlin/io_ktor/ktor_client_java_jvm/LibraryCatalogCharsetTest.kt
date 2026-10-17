/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.Charsets
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Text body charset negotiation and decoding through the client HttpPlainText (Charsets) plugin
 * over the Java engine, as a library cataloguing application exchanges bibliographic records
 * with a legacy catalogue server that speaks Latin-1.
 */
public class LibraryCatalogCharsetTest {

    /** The legacy catalogue server, which stores records in Latin-1 and reports what it receives. */
    class LegacyCatalogueServer : AutoCloseable {
        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/records/latin1") {
                    call.respondBytes(
                        RECORD.toByteArray(StandardCharsets.ISO_8859_1),
                        ContentType.Text.Plain.withCharset(StandardCharsets.ISO_8859_1),
                    )
                }
                get("/records/utf16") {
                    call.respondBytes(
                        RECORD.toByteArray(StandardCharsets.UTF_16),
                        ContentType.Text.Plain.withCharset(StandardCharsets.UTF_16),
                    )
                }
                get("/export/undeclared") {
                    // The legacy export job writes Latin-1 but never names a charset.
                    call.respondBytes(RECORD.toByteArray(StandardCharsets.ISO_8859_1), ContentType("text", "csv"))
                }
                get("/negotiation") {
                    call.respondText(call.request.headers[HttpHeaders.AcceptCharset] ?: "<none>")
                }
                post("/records") {
                    val declared: Charset = call.request.contentType().charset() ?: StandardCharsets.US_ASCII
                    val bytes: ByteArray = call.receive()
                    val title: String = String(bytes, declared)
                    call.respondText("${declared.name()}|${bytes.size}|$title", ContentType.Text.Plain)
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private fun catalogueClient(baseUrl: String): HttpClient = HttpClient(Java) {
        Charsets {
            register(StandardCharsets.UTF_8)
            register(StandardCharsets.ISO_8859_1, quality = 0.5f)
            sendCharset = StandardCharsets.ISO_8859_1
            responseCharsetFallback = StandardCharsets.ISO_8859_1
        }
        defaultRequest {
            url("$baseUrl/")
        }
    }

    @Test
    fun advertisesAcceptedCharsetsAndDecodesDeclaredResponseCharsets(): Unit = runBlocking {
        LegacyCatalogueServer().use { server: LegacyCatalogueServer ->
            catalogueClient(server.baseUrl).use { client: HttpClient ->
                val negotiation: String = client.get("negotiation").body()
                assertThat(negotiation).isEqualTo("UTF-8,ISO-8859-1;q=0.5")

                val latin1: HttpResponse = client.get("records/latin1")
                assertThat(latin1.contentType()?.charset()).isEqualTo(StandardCharsets.ISO_8859_1)
                assertThat(latin1.body<String>()).isEqualTo(RECORD)

                val utf16: HttpResponse = client.get("records/utf16")
                assertThat(utf16.contentType()?.charset()).isEqualTo(StandardCharsets.UTF_16)
                assertThat(utf16.body<String>()).isEqualTo(RECORD)
            }
        }
    }

    @Test
    fun fallsBackToTheConfiguredCharsetWhenTheServerNamesNone(): Unit = runBlocking {
        LegacyCatalogueServer().use { server: LegacyCatalogueServer ->
            catalogueClient(server.baseUrl).use { client: HttpClient ->
                val export: HttpResponse = client.get("export/undeclared")
                assertThat(export.contentType()?.charset()).isNull()
                assertThat(export.body<String>()).isEqualTo(RECORD)
            }

            HttpClient(Java) { defaultRequest { url("${server.baseUrl}/") } }.use { defaults: HttpClient ->
                val export: HttpResponse = defaults.get("export/undeclared")
                assertThat(export.body<String>()).contains("\ufffd").isNotEqualTo(RECORD)
                assertThat(export.bodyAsText(StandardCharsets.ISO_8859_1)).isEqualTo(RECORD)
            }
        }
    }

    @Test
    fun encodesOutgoingTextWithTheSendCharsetUnlessTheRequestNamesOne(): Unit = runBlocking {
        LegacyCatalogueServer().use { server: LegacyCatalogueServer ->
            catalogueClient(server.baseUrl).use { client: HttpClient ->
                val latin1: String = client.post("records") {
                    contentType(ContentType.Text.Plain)
                    setBody(TITLE)
                }.body()
                assertThat(latin1).isEqualTo("ISO-8859-1|${TITLE.length}|$TITLE")

                val utf8: String = client.post("records") {
                    contentType(ContentType.Text.Plain.withCharset(StandardCharsets.UTF_8))
                    setBody(TITLE)
                }.body()
                val utf8Length: Int = TITLE.toByteArray(StandardCharsets.UTF_8).size
                assertThat(utf8Length).isGreaterThan(TITLE.length)
                assertThat(utf8).isEqualTo("UTF-8|$utf8Length|$TITLE")
            }
        }
    }

    private companion object {
        const val TITLE: String = "M\u00fcller, J\u00fcrgen: Caf\u00e9 cr\u00e8me \u00e0 la fran\u00e7aise"
        const val RECORD: String = "id;title;shelf\n42;$TITLE;R\u00e9f\u00e9rence-\u00c9\n"
    }
}
