/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.Configuration
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

/**
 * Converter setup shared between server and clients through the serialization `Configuration` interface.
 *
 * A loyalty-points service defines its wire formats once, as an application extension on
 * `io.ktor.serialization.Configuration` that registers JSON and Protobuf, and applies that one
 * extension to the server's content-negotiation plugin and to the HTTP clients of its mobile
 * app and its web back office. The mobile app trades compact Protobuf messages, the back office
 * reads JSON, and a point-of-sale terminal without any Ktor client posts hand-encoded Protobuf
 * bytes. The test checks that the server answers each caller in the format it asks for with
 * bytes exactly as the shared encoders produce them, that it decodes Protobuf request bodies
 * from both kinds of client, and that a caller naming no preference gets the first format the
 * shared extension registers.
 */
@OptIn(ExperimentalSerializationApi::class)
class LoyaltySharedFormatsTest {

    @Serializable
    data class Member(
        @ProtoNumber(1) val id: String,
        @ProtoNumber(2) val tier: String,
        @ProtoNumber(3) val points: Int,
    )

    @Serializable
    data class Earning(
        @ProtoNumber(1) val memberId: String,
        @ProtoNumber(2) val points: Int,
        @ProtoNumber(3) val reason: String,
    )

    @Serializable
    data class Statement(
        @ProtoNumber(1) val member: Member,
        @ProtoNumber(2) val earnings: List<Earning>,
    )

    private val members: ConcurrentHashMap<String, Member> = ConcurrentHashMap(
        mapOf(
            "m-100" to Member("m-100", "silver", 1200),
            "m-200" to Member("m-200", "gold", 5400),
        ),
    )
    private val earnings: ConcurrentLinkedQueue<Earning> = ConcurrentLinkedQueue()

    private fun runLoyaltyService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                loyaltyFormats()
            }
            routing {
                get("/members/{id}") {
                    val member: Member? = members[call.parameters["id"]!!]
                    if (member == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(member)
                    }
                }
                get("/members/{id}/statement") {
                    val id: String = call.parameters["id"]!!
                    val history: List<Earning> = earnings.filter { it.memberId == id }
                    call.respond(Statement(members.getValue(id), history))
                }
                post("/earnings") {
                    val earning: Earning = call.receive()
                    earnings.add(earning)
                    val updated: Member = members.computeIfPresent(earning.memberId) { _, member ->
                        member.copy(points = member.points + earning.points)
                    }!!
                    call.respond(HttpStatusCode.Created, updated)
                }
            }
        }
        block()
    }

    private fun ApplicationTestBuilder.loyaltyClient(): HttpClient = createClient {
        install(ClientContentNegotiation) {
            loyaltyFormats()
        }
    }

    @Test
    fun mobileAppTradesProtobufWithTheSharedEncoder() = runLoyaltyService {
        val mobile: HttpClient = loyaltyClient()

        val profile: HttpResponse = mobile.get("/members/m-200") { accept(ContentType.Application.ProtoBuf) }
        assertEquals(HttpStatusCode.OK, profile.status)
        assertEquals(ContentType.Application.ProtoBuf, profile.contentType()?.withoutParameters())
        val expected = Member("m-200", "gold", 5400)
        assertArrayEquals(LOYALTY_PROTOBUF.encodeToByteArray(expected), profile.bodyAsBytes())
        assertEquals(expected, profile.body<Member>())

        val earned: HttpResponse = mobile.post("/earnings") {
            contentType(ContentType.Application.ProtoBuf)
            accept(ContentType.Application.ProtoBuf)
            setBody(Earning("m-200", 300, "coffee"))
        }
        assertEquals(HttpStatusCode.Created, earned.status)
        assertEquals(ContentType.Application.ProtoBuf, earned.contentType()?.withoutParameters())
        assertEquals(Member("m-200", "gold", 5700), earned.body<Member>())
        assertEquals(listOf(Earning("m-200", 300, "coffee")), earnings.toList())
    }

    @Test
    fun backOfficeReadsJsonFromTheSameRegistrations() = runLoyaltyService {
        val backOffice: HttpClient = loyaltyClient()
        val posted: HttpResponse = backOffice.post("/earnings") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(Earning("m-100", 50, "survey"))
        }
        assertEquals(HttpStatusCode.Created, posted.status)
        assertEquals(Member("m-100", "silver", 1250), posted.body<Member>())

        val statement: HttpResponse = backOffice.get("/members/m-100/statement") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, statement.status)
        assertEquals(ContentType.Application.Json, statement.contentType()?.withoutParameters())
        assertEquals(
            """{"member":{"id":"m-100","tier":"silver","points":1250},"earnings":[{"memberId":"m-100","points":50,"reason":"survey"}]}""",
            statement.bodyAsText(),
        )
        assertEquals(
            Statement(Member("m-100", "silver", 1250), listOf(Earning("m-100", 50, "survey"))),
            statement.body<Statement>(),
        )
    }

    @Test
    fun terminalPostsHandEncodedProtobufBytes() = runLoyaltyService {
        val request: ByteArray = LOYALTY_PROTOBUF.encodeToByteArray(Earning("m-100", 25, "pos-17"))
        val earned: HttpResponse = client.post("/earnings") {
            header(HttpHeaders.ContentType, ContentType.Application.ProtoBuf.toString())
            header(HttpHeaders.Accept, ContentType.Application.ProtoBuf.toString())
            setBody(request)
        }
        assertEquals(HttpStatusCode.Created, earned.status)
        assertEquals(ContentType.Application.ProtoBuf, earned.contentType()?.withoutParameters())
        val updated: Member = LOYALTY_PROTOBUF.decodeFromByteArray(earned.bodyAsBytes())
        assertEquals(Member("m-100", "silver", 1225), updated)

        val statement: HttpResponse = client.get("/members/m-100/statement") {
            header(HttpHeaders.Accept, ContentType.Application.ProtoBuf.toString())
        }
        assertEquals(ContentType.Application.ProtoBuf, statement.contentType()?.withoutParameters())
        assertEquals(
            Statement(Member("m-100", "silver", 1225), listOf(Earning("m-100", 25, "pos-17"))),
            LOYALTY_PROTOBUF.decodeFromByteArray<Statement>(statement.bodyAsBytes()),
        )
    }

    @Test
    fun callerWithoutPreferenceGetsTheFirstSharedFormat() = runLoyaltyService {
        val noPreference: HttpResponse = client.get("/members/m-100")
        assertEquals(HttpStatusCode.OK, noPreference.status)
        assertEquals(ContentType.Application.Json, noPreference.contentType()?.withoutParameters())
        assertEquals(Member("m-100", "silver", 1200), LOYALTY_JSON.decodeFromString<Member>(noPreference.bodyAsText()))

        val unknown: HttpResponse = client.get("/members/m-999") {
            header(HttpHeaders.Accept, ContentType.Application.ProtoBuf.toString())
        }
        assertEquals(HttpStatusCode.NotFound, unknown.status)
    }

    companion object {
        val LOYALTY_JSON: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        val LOYALTY_PROTOBUF: ProtoBuf = ProtoBuf {
            encodeDefaults = true
        }

        /** The service's wire formats, applied to the server plugin and to every client alike. */
        fun Configuration.loyaltyFormats() {
            json(LOYALTY_JSON)
            protobuf(LOYALTY_PROTOBUF)
        }
    }
}
