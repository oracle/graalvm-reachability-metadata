/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises request and response charset negotiation for a legacy partner's JSON contact API.
 */
@Timeout(60)
class PartnerCharsetNegotiationTest {
    @Test
    fun decodesLatin1ContactsAndEncodesRepliesForEachConsumer(): Unit = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("/partner/welcome") {
                    val contact: Contact = call.receive<Contact>()
                    call.respond(Welcome("Bonjour, ${contact.name}", contact.city))
                }
            }
        }

        val partnerClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        val contactJson: String = "{\"name\":\"Andr\u00e9\",\"city\":\"Montr\u00e9al\"}"
        val expected: Welcome = Welcome("Bonjour, Andr\u00e9", "Montr\u00e9al")
        val expectedJson: String = Json.encodeToString(expected)

        // The older partner exchanges actual Latin-1 bytes, not ASCII Unicode escapes.
        val legacyResponse = partnerClient.post("/partner/welcome") {
            contentType(ContentType.Application.Json.withCharset(Charsets.ISO_8859_1))
            accept(ContentType.Application.Json)
            header(HttpHeaders.AcceptCharset, "iso-8859-1")
            setBody(contactJson.toByteArray(Charsets.ISO_8859_1))
        }
        assertEquals(HttpStatusCode.OK, legacyResponse.status)
        assertEquals(ContentType.Application.Json, legacyResponse.contentType()?.withoutParameters())
        assertEquals(Charsets.ISO_8859_1, legacyResponse.contentType()?.charset())
        val legacyBytes: ByteArray = legacyResponse.bodyAsBytes()
        assertArrayEquals(expectedJson.toByteArray(Charsets.ISO_8859_1), legacyBytes)
        assertEquals(expected, Json.decodeFromString<Welcome>(legacyBytes.toString(Charsets.ISO_8859_1)))

        // A subsequent modern consumer gets UTF-8 without inheriting the partner's response charset.
        val modernResponse = partnerClient.post("/partner/welcome") {
            contentType(ContentType.Application.Json.withCharset(Charsets.UTF_8))
            accept(ContentType.Application.Json)
            setBody(contactJson.toByteArray(Charsets.UTF_8))
        }
        assertEquals(HttpStatusCode.OK, modernResponse.status)
        assertEquals(ContentType.Application.Json, modernResponse.contentType()?.withoutParameters())
        assertEquals(Charsets.UTF_8, modernResponse.contentType()?.charset())
        val modernBytes: ByteArray = modernResponse.bodyAsBytes()
        assertArrayEquals(expectedJson.toByteArray(Charsets.UTF_8), modernBytes)
        assertEquals(expected, Json.decodeFromString<Welcome>(modernBytes.toString(Charsets.UTF_8)))
    }

    @Serializable
    data class Contact(val name: String, val city: String)

    @Serializable
    data class Welcome(val greeting: String, val city: String)
}
