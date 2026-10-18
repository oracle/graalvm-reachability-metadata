/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.DefaultXml
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Request-conversion failures surfaced to the application's error handling.
 *
 * A product-review service decodes reviews with the plugin, validates the decoded review
 * with `RequestValidation`, and maps every failure to a typed error document with
 * `StatusPages`, as public APIs do so clients always get a machine-readable reason. The test
 * checks that a body in a content type no converter handles is answered with
 * `415 Unsupported Media Type`, which a status handler turns into an error document, that a
 * body its converter cannot decode raises `BadRequestException`, that a decoded but invalid
 * review reaches validation and is rejected there, that none of them is stored, and that
 * each error document is itself negotiated into the format the caller's `Accept` header
 * asks for.
 */
class ProductReviewErrorHandlingTest {

    @Serializable
    data class Review(val productId: String, val author: String, val rating: Int, val text: String)

    @Serializable
    data class Receipt(val productId: String, val accepted: Int)

    @Serializable
    data class ApiError(val code: String, val reasons: List<String>)

    private val stored: ConcurrentLinkedQueue<Review> = ConcurrentLinkedQueue()
    private val handled: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    private fun runService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json()
                xml()
            }
            install(RequestValidation) {
                validate<Review> { review ->
                    val reasons: List<String> = buildList {
                        if (review.rating !in 1..5) {
                            add("rating must be between 1 and 5")
                        }
                        if (review.text.isBlank()) {
                            add("text must not be blank")
                        }
                    }
                    if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
                }
            }
            install(StatusPages) {
                exception<BadRequestException> { call, _ ->
                    handled.add("malformed")
                    call.respond(HttpStatusCode.BadRequest, ApiError("malformed_body", listOf("body is not a review")))
                }
                exception<RequestValidationException> { call, cause ->
                    handled.add("invalid")
                    call.respond(HttpStatusCode.UnprocessableEntity, ApiError("invalid_review", cause.reasons))
                }
                status(HttpStatusCode.UnsupportedMediaType) { call, status ->
                    handled.add("unsupported")
                    call.respond(status, ApiError("unsupported_media_type", SUPPORTED))
                }
                status(HttpStatusCode.NotFound) { call, status ->
                    handled.add("not_found")
                    call.respond(status, ApiError("not_found", listOf(call.request.local.uri)))
                }
            }
            routing {
                post("/reviews") {
                    val review: Review = call.receive()
                    stored.add(review)
                    val accepted: Int = stored.count { it.productId == review.productId }
                    call.respond(HttpStatusCode.Created, Receipt(review.productId, accepted))
                }
            }
        }
        block()
    }

    @Test
    fun validReviewIsDecodedAndStored() = runService {
        val response: HttpResponse = postReview(VALID_JSON, ContentType.Application.Json, ContentType.Application.Json)
        assertEquals(HttpStatusCode.Created, response.status)
        assertFormat(ContentType.Application.Json, response)
        assertEquals(Receipt("kettle-3", 1), Json.decodeFromString<Receipt>(response.bodyAsText()))
        assertEquals(listOf(Review("kettle-3", "ana", 4, "Boils fast")), stored.toList())
        assertEquals(emptyList<String>(), handled.toList())
    }

    @Test
    fun unsupportedContentTypeIsReportedAsATypedError() = runService {
        val response: HttpResponse = postReview(
            "kettle-3;ana;4;Boils fast",
            ContentType.Text.Plain,
            ContentType.Application.Json,
        )
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertFormat(ContentType.Application.Json, response)
        assertEquals(ApiError("unsupported_media_type", SUPPORTED), Json.decodeFromString<ApiError>(response.bodyAsText()))
        assertEquals(listOf("unsupported"), handled.toList())
        assertEquals(emptyList<Review>(), stored.toList())
    }

    @Test
    fun undecodableBodyIsReportedAsMalformed() = runService {
        val truncated: HttpResponse = postReview(
            """{"productId":"kettle-3","author":"ana",""",
            ContentType.Application.Json,
            ContentType.Application.Json,
        )
        assertEquals(HttpStatusCode.BadRequest, truncated.status)
        assertEquals(
            ApiError("malformed_body", listOf("body is not a review")),
            Json.decodeFromString<ApiError>(truncated.bodyAsText()),
        )

        val wrongShape: HttpResponse = postReview(
            """{"productId":"kettle-3","author":"ana","rating":"four","text":"Boils fast"}""",
            ContentType.Application.Json,
            ContentType.Application.Json,
        )
        assertEquals(HttpStatusCode.BadRequest, wrongShape.status)
        assertEquals(listOf("malformed", "malformed"), handled.toList())
        assertEquals(emptyList<Review>(), stored.toList())
    }

    @Test
    fun decodedButInvalidReviewIsRejectedByValidation() = runService {
        val response: HttpResponse = postReview(
            """{"productId":"kettle-3","author":"ana","rating":9,"text":"  "}""",
            ContentType.Application.Json,
            ContentType.Application.Json,
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertFormat(ContentType.Application.Json, response)
        assertEquals(
            ApiError("invalid_review", listOf("rating must be between 1 and 5", "text must not be blank")),
            Json.decodeFromString<ApiError>(response.bodyAsText()),
        )
        assertEquals(listOf("invalid"), handled.toList())
        assertEquals(emptyList<Review>(), stored.toList())
    }

    @Test
    fun errorDocumentsFollowTheCallersAcceptHeader() = runService {
        val xmlReview: String = DefaultXml.encodeToString(
            Review.serializer(),
            Review("kettle-3", "ana", 0, "Leaks"),
        )
        val invalid: HttpResponse = postReview(xmlReview, ContentType.Application.Xml, ContentType.Application.Xml)
        assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        assertFormat(ContentType.Application.Xml, invalid)
        assertEquals(
            ApiError("invalid_review", listOf("rating must be between 1 and 5")),
            DefaultXml.decodeFromString(ApiError.serializer(), invalid.bodyAsText()),
        )

        val missing: HttpResponse = client.get("/reviews/kettle-3") {
            header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertFormat(ContentType.Application.Xml, missing)
        assertEquals(
            ApiError("not_found", listOf("/reviews/kettle-3")),
            DefaultXml.decodeFromString(ApiError.serializer(), missing.bodyAsText()),
        )
        assertEquals(listOf("invalid", "not_found"), handled.toList())
        assertEquals(emptyList<Review>(), stored.toList())
    }

    private suspend fun ApplicationTestBuilder.postReview(
        body: String,
        contentType: ContentType,
        accept: ContentType,
    ): HttpResponse = client.post("/reviews") {
        header(HttpHeaders.Accept, accept.toString())
        setBody(TextContent(body, contentType))
    }

    private fun assertFormat(expected: ContentType, response: HttpResponse) {
        val actual: ContentType = response.contentType()!!
        assertTrue(actual.match(expected), "expected $expected but got $actual")
    }

    companion object {
        private val SUPPORTED: List<String> = listOf("application/json", "application/xml")

        private const val VALID_JSON: String =
            """{"productId":"kettle-3","author":"ana","rating":4,"text":"Boils fast"}"""
    }
}
