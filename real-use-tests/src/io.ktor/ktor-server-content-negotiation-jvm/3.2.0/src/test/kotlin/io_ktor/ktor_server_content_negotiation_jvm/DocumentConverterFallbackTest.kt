/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises type-selective converter fallback for a document API with a legacy JSON representation.
 */
@Timeout(60)
class DocumentConverterFallbackTest {
    @Test
    fun publishesLegacyDocumentsWhileStandardJsonHandlesCommandsAndStatus(): Unit = testApplication {
        val documents: ConcurrentHashMap<String, PublishedDocument> = ConcurrentHashMap()
        application {
            install(ContentNegotiation) {
                // Only published documents use the legacy envelope; all other JSON uses the standard converter.
                register(ContentType.Application.Json, PublishedDocumentConverter())
                json()
            }
            routing {
                post("/documents") {
                    val command: PublishCommand = call.receive<PublishCommand>()
                    val document: PublishedDocument = PublishedDocument(command.id, command.title.trim())
                    documents[command.id] = document
                    call.respond(HttpStatusCode.Created, document)
                }
                get("/documents/{id}") {
                    call.respond(requireNotNull(documents[call.parameters["id"]]))
                }
                get("/documents/{id}/status") {
                    val document: PublishedDocument = requireNotNull(documents[call.parameters["id"]])
                    call.respond(PublicationStatus(document.id, "published"))
                }
            }
        }

        val publishingClient: HttpClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        val expectedDocument: String = """
            {"document":{"key":"guide-7","heading":"A \"quoted\" guide"},"schema":1}
        """.trimIndent()
        val created: HttpResponse = publishingClient.post("/documents") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody("""{"id":"guide-7","title":"  A \"quoted\" guide  "}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(ContentType.Application.Json, created.contentType()?.withoutParameters())
        assertEquals(Json.parseToJsonElement(expectedDocument), Json.parseToJsonElement(created.bodyAsText()))

        // The first converter declines this response type, so the next JSON converter must serialize it.
        val status: HttpResponse = publishingClient.get("/documents/guide-7/status") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, status.status)
        assertEquals(ContentType.Application.Json, status.contentType()?.withoutParameters())
        assertEquals(
            Json.parseToJsonElement("""{"id":"guide-7","state":"published"}"""),
            Json.parseToJsonElement(status.bodyAsText())
        )

        // A fallback response must not affect converter selection for the next document response.
        val fetched: HttpResponse = publishingClient.get("/documents/guide-7") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, fetched.status)
        assertEquals(ContentType.Application.Json, fetched.contentType()?.withoutParameters())
        assertEquals(Json.parseToJsonElement(expectedDocument), Json.parseToJsonElement(fetched.bodyAsText()))
    }

    @Serializable
    data class PublishCommand(val id: String, val title: String)

    data class PublishedDocument(val id: String, val title: String)

    @Serializable
    data class PublicationStatus(val id: String, val state: String)

    // This output-only domain representation preserves an older client's envelope and field names.
    class PublishedDocumentConverter : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            if (value !is PublishedDocument) return null
            val document: String = buildJsonObject {
                put("document", buildJsonObject {
                    put("key", value.id)
                    put("heading", value.title)
                })
                put("schema", 1)
            }.toString()
            return TextContent(document, contentType.withCharset(charset))
        }

        override suspend fun deserialize(
            charset: Charset,
            typeInfo: TypeInfo,
            content: ByteReadChannel
        ): Any? = null
    }
}
