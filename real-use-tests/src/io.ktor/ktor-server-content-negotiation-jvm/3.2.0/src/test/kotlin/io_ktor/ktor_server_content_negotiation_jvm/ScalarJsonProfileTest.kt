/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises ignored-type customization for scalar JSON profile values alongside a plain-text export.
 */
@Timeout(60)
class ScalarJsonProfileTest {
    @Test
    fun returnsEscapedJsonStringsWithoutConvertingTheTextExport(): Unit = testApplication {
        val displayName: AtomicReference<String> = AtomicReference("Unconfigured")
        application {
            install(ContentNegotiation) {
                // This API returns JSON scalars, not the default unconverted String response.
                removeIgnoredType<String>()
                json()
            }
            routing {
                put("/profile/display-name") {
                    val update: DisplayNameUpdate = call.receive<DisplayNameUpdate>()
                    val normalized: String = update.displayName.trim()
                    displayName.set(normalized)
                    call.respond(normalized)
                }
                get("/profile/display-name") {
                    call.respond(displayName.get())
                }
                get("/profile/display-name.txt") {
                    // Explicit outgoing content must remain usable for the downloadable text version.
                    call.respondText(displayName.get(), ContentType.Text.Plain)
                }
            }
        }

        val profileClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        val expectedName: String = "Ada \"Ops\" \\ West\nDesk"
        val expectedJson: String = "\"Ada \\\"Ops\\\" \\\\ West\\nDesk\""
        val updateResponse = profileClient.put("/profile/display-name") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody("""{"displayName":"  Ada \"Ops\" \\ West\nDesk  "}""")
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        assertEquals(ContentType.Application.Json, updateResponse.contentType()?.withoutParameters())
        assertEquals(expectedJson, updateResponse.bodyAsText())

        val readResponse = profileClient.get("/profile/display-name") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, readResponse.status)
        assertEquals(ContentType.Application.Json, readResponse.contentType()?.withoutParameters())
        assertEquals(expectedJson, readResponse.bodyAsText())
        assertEquals(expectedName, Json.decodeFromString<String>(readResponse.bodyAsText()))

        val exportResponse = profileClient.get("/profile/display-name.txt") {
            accept(ContentType.Text.Plain)
        }
        assertEquals(HttpStatusCode.OK, exportResponse.status)
        assertEquals(ContentType.Text.Plain, exportResponse.contentType()?.withoutParameters())
        assertEquals(expectedName, exportResponse.bodyAsText())
    }

    @Serializable
    data class DisplayNameUpdate(val displayName: String)
}
