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
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Negotiated request and response bodies behind the `Authentication` plugin.
 *
 * An expense-claim service lets staff submit claims with a bearer token and lets partner
 * systems read statements with an API key, as internal back-office APIs do. The test checks
 * that an authenticated claim is decoded by the JSON converter and answered with a receipt
 * carrying the principal, that the principal object itself is negotiated into JSON or XML by
 * `Accept`, that an unauthenticated request is rejected with the bearer provider's
 * `401` challenge before its body is ever decoded, so even a malformed body yields `401`
 * rather than `400`, that the challenge itself bypasses the converters, and that a custom
 * provider's challenge responds with a typed error document negotiated like any other body.
 */
class ExpenseClaimAuthenticatedBodyTest {

    @Serializable
    data class Claim(val reference: String, val category: String, val amountCents: Long)

    @Serializable
    data class ClaimReceipt(val reference: String, val submittedBy: String, val amountCents: Long)

    @Serializable
    data class StaffPrincipal(val name: String, val department: String)

    @Serializable
    data class PartnerPrincipal(val partnerId: String)

    @Serializable
    data class Statement(val partnerId: String, val claims: List<ClaimReceipt>)

    @Serializable
    data class AuthError(val code: String, val realm: String)

    private val stored: ConcurrentLinkedQueue<ClaimReceipt> = ConcurrentLinkedQueue()

    private fun runService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json()
                xml()
            }
            install(Authentication) {
                bearer("staff") {
                    realm = STAFF_REALM
                    authenticate { credential -> STAFF_TOKENS[credential.token] }
                }
                provider("partner") {
                    authenticate { context ->
                        val key: String? = context.call.request.headers[PARTNER_KEY_HEADER]
                        val partner: PartnerPrincipal? = key?.let { PARTNER_KEYS[it] }
                        if (partner != null) {
                            context.principal(partner)
                        } else {
                            val cause: AuthenticationFailedCause = if (key == null) {
                                AuthenticationFailedCause.NoCredentials
                            } else {
                                AuthenticationFailedCause.InvalidCredentials
                            }
                            val code: String = if (key == null) "missing_key" else "invalid_key"
                            context.challenge(PARTNER_KEY_HEADER, cause) { challenge, call ->
                                call.respond(HttpStatusCode.Unauthorized, AuthError(code, PARTNER_REALM))
                                challenge.complete()
                            }
                        }
                    }
                }
            }
            routing {
                authenticate("staff") {
                    post("/claims") {
                        val claim: Claim = call.receive()
                        val staff: StaffPrincipal = call.principal<StaffPrincipal>()!!
                        val receipt = ClaimReceipt(claim.reference, staff.name, claim.amountCents)
                        stored.add(receipt)
                        call.respond(HttpStatusCode.Created, receipt)
                    }
                    get("/me") {
                        call.respond(call.principal<StaffPrincipal>()!!)
                    }
                }
                authenticate("partner") {
                    get("/statement") {
                        val partner: PartnerPrincipal = call.principal<PartnerPrincipal>()!!
                        call.respond(Statement(partner.partnerId, stored.toList()))
                    }
                }
            }
        }
        block()
    }

    @Test
    fun authenticatedClaimIsDecodedAndAnsweredWithThePrincipal() = runService {
        val response: HttpResponse = postClaim(TAXI_JSON, token = "t-ana")
        assertEquals(HttpStatusCode.Created, response.status)
        assertFormat(ContentType.Application.Json, response)
        val receipt = ClaimReceipt("exp-17", "ana", 4250)
        assertEquals(receipt, Json.decodeFromString<ClaimReceipt>(response.bodyAsText()))
        assertEquals(listOf(receipt), stored.toList())
    }

    @Test
    fun principalIsNegotiatedLikeAnyResponseBody() = runService {
        val asJson: HttpResponse = client.get("/me") {
            header(HttpHeaders.Authorization, "Bearer t-ana")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        assertEquals(HttpStatusCode.OK, asJson.status)
        assertFormat(ContentType.Application.Json, asJson)
        assertEquals(StaffPrincipal("ana", "finance"), Json.decodeFromString<StaffPrincipal>(asJson.bodyAsText()))

        val asXml: HttpResponse = client.get("/me") {
            header(HttpHeaders.Authorization, "Bearer t-bo")
            header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
        }
        assertEquals(HttpStatusCode.OK, asXml.status)
        assertFormat(ContentType.Application.Xml, asXml)
        assertEquals(
            StaffPrincipal("bo", "travel"),
            DefaultXml.decodeFromString(StaffPrincipal.serializer(), asXml.bodyAsText()),
        )
    }

    @Test
    fun unauthenticatedClaimIsChallengedBeforeItsBodyIsDecoded() = runService {
        val missingToken: HttpResponse = postClaim(TAXI_JSON, token = null)
        assertBearerChallenge(missingToken)

        val wrongToken: HttpResponse = postClaim(TAXI_JSON, token = "t-unknown")
        assertBearerChallenge(wrongToken)

        val malformedWithoutToken: HttpResponse = postClaim(MALFORMED_JSON, token = null)
        assertBearerChallenge(malformedWithoutToken)
        assertEquals(emptyList<ClaimReceipt>(), stored.toList())

        val malformedWithToken: HttpResponse = postClaim(MALFORMED_JSON, token = "t-ana")
        assertEquals(HttpStatusCode.BadRequest, malformedWithToken.status)
        assertEquals(emptyList<ClaimReceipt>(), stored.toList())
    }

    @Test
    fun customProviderChallengeIsATypedErrorDocumentFollowingAccept() = runService {
        val missingKey: HttpResponse = client.get("/statement") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        assertEquals(HttpStatusCode.Unauthorized, missingKey.status)
        assertFormat(ContentType.Application.Json, missingKey)
        assertEquals(
            AuthError("missing_key", PARTNER_REALM),
            Json.decodeFromString<AuthError>(missingKey.bodyAsText()),
        )

        val wrongKey: HttpResponse = client.get("/statement") {
            header(PARTNER_KEY_HEADER, "k-revoked")
            header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongKey.status)
        assertFormat(ContentType.Application.Xml, wrongKey)
        assertEquals(
            AuthError("invalid_key", PARTNER_REALM),
            DefaultXml.decodeFromString(AuthError.serializer(), wrongKey.bodyAsText()),
        )
    }

    @Test
    fun partnerStatementListsClaimsSubmittedByStaff() = runService {
        assertEquals(HttpStatusCode.Created, postClaim(TAXI_JSON, token = "t-ana").status)
        val hotel: String = """{"reference":"exp-18","category":"hotel","amountCents":18900}"""
        assertEquals(HttpStatusCode.Created, postClaim(hotel, token = "t-bo").status)

        val response: HttpResponse = client.get("/statement") {
            header(PARTNER_KEY_HEADER, "k-acme")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertFormat(ContentType.Application.Json, response)
        assertEquals(
            Statement(
                "acme-travel",
                listOf(ClaimReceipt("exp-17", "ana", 4250), ClaimReceipt("exp-18", "bo", 18900)),
            ),
            Json.decodeFromString<Statement>(response.bodyAsText()),
        )
    }

    private suspend fun ApplicationTestBuilder.postClaim(body: String, token: String?): HttpResponse =
        client.post("/claims") {
            if (token != null) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody(TextContent(body, ContentType.Application.Json))
        }

    private suspend fun assertBearerChallenge(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer realm=$STAFF_REALM", response.headers[HttpHeaders.WWWAuthenticate])
        assertNull(response.contentType(), "the challenge must not be converted into a JSON body")
        assertEquals("", response.bodyAsText())
    }

    private fun assertFormat(expected: ContentType, response: HttpResponse) {
        val actual: ContentType = response.contentType()!!
        assertTrue(actual.match(expected), "expected $expected but got $actual")
    }

    companion object {
        private const val STAFF_REALM: String = "expenses"
        private const val PARTNER_REALM: String = "partners"
        private const val PARTNER_KEY_HEADER: String = "X-Partner-Key"

        private val STAFF_TOKENS: Map<String, StaffPrincipal> = mapOf(
            "t-ana" to StaffPrincipal("ana", "finance"),
            "t-bo" to StaffPrincipal("bo", "travel"),
        )

        private val PARTNER_KEYS: Map<String, PartnerPrincipal> = mapOf(
            "k-acme" to PartnerPrincipal("acme-travel"),
        )

        private const val TAXI_JSON: String = """{"reference":"exp-17","category":"taxi","amountCents":4250}"""

        private const val MALFORMED_JSON: String = """{"reference":"exp-17","category":"""
    }
}
