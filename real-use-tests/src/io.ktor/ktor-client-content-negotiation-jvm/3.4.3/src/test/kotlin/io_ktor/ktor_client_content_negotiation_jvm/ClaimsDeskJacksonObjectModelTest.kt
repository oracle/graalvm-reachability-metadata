/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.serialization.jackson.jackson
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Jackson's annotation-driven object model through the client ContentNegotiation plugin
 * (polymorphic sealed hierarchies, renamed and omitted properties, JsonNode trees, and streamed
 * versus buffered request bodies), as an insurance claims desk files and tracks claims.
 */
public class ClaimsDeskJacksonObjectModelTest {

    enum class Severity {
        @JsonProperty("minor")
        MINOR,

        @JsonProperty("major")
        MAJOR,

        @JsonProperty("total-loss")
        TOTAL_LOSS,
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes(
        JsonSubTypes.Type(value = ClaimEvent.Filed::class, name = "filed"),
        JsonSubTypes.Type(value = ClaimEvent.Assessed::class, name = "assessed"),
        JsonSubTypes.Type(value = ClaimEvent.Paid::class, name = "paid"),
    )
    sealed class ClaimEvent {
        data class Filed(val severity: Severity, val claimedCents: Long) : ClaimEvent()

        data class Assessed(val assessor: String, val approvedCents: Long) : ClaimEvent()

        data class Paid(val paymentReference: String) : ClaimEvent()
    }

    data class ClaimFile(
        @param:JsonProperty("claim_id") val claimId: String,
        @param:JsonProperty("policy_number") val policyNumber: String,
        val events: List<ClaimEvent> = emptyList(),
    )

    /** A claim as the desk submits it; the draft id stays local and an absent note is left out. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class ClaimSubmission(
        @param:JsonProperty("policy_number") val policyNumber: String,
        val severity: Severity,
        val claimedCents: Long,
        val adjusterNote: String? = null,
        @get:JsonIgnore val draftId: String,
    )

    data class AuditNote(val claimId: String, val recordedBy: String, val remark: String)

    /** What the claims service saw of one incoming request. */
    data class ReceivedRequest(
        val path: String,
        val contentType: String?,
        val contentLength: String?,
        val transferEncoding: String?,
        val body: String,
    )

    /** The claims service; it answers with fixed bodies so the client's decoding is what is checked. */
    class ClaimsService : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/claims/CL-7") {
                    call.respondText(
                        """
                        {"claim_id":"CL-7","policy_number":"POL-4410","events":[
                          {"kind":"filed","severity":"total-loss","claimedCents":1850000},
                          {"kind":"assessed","assessor":"J. Moreau","approvedCents":1620000},
                          {"kind":"paid","paymentReference":"PAY-88213"}
                        ]}
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                get("/claims/CL-7/summary") {
                    call.respondText(
                        """{"claim":"CL-7","open":false,"totals":{"claimed":1850000,"approved":1620000},"flags":["fast-track"]}""",
                        ContentType.Application.Json,
                    )
                }
                post("/claims") {
                    record(call.receiveText())
                    call.respondText(
                        """{"claim_id":"CL-8","policy_number":"POL-5120","events":[{"kind":"filed","severity":"major","claimedCents":240000}]}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
                post("/claims/CL-7/tags") {
                    record(call.receiveText())
                    call.respondText("""{"claim":"CL-7","tags":["fast-track","reviewed"]}""", ContentType.Application.Json)
                }
                post("/audit") {
                    record(call.receiveText())
                    call.respondText("", status = HttpStatusCode.Accepted)
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        private fun RoutingContext.record(body: String) {
            received += ReceivedRequest(
                call.request.local.uri,
                call.request.contentType().toString(),
                call.request.header(HttpHeaders.ContentLength),
                call.request.header(HttpHeaders.TransferEncoding),
                body,
            )
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private val auditMapper: ObjectMapper = jacksonObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private fun claimsClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            // Registered first: the default JSON matcher would also claim this +json vendor type.
            register(AUDIT_JSON, JacksonConverter(auditMapper, streamRequestBody = false))
            jackson()
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun claimHistoryIsDecodedIntoTheSealedEventHierarchy(): Unit = runBlocking {
        ClaimsService().use { service: ClaimsService ->
            claimsClient(service.baseUrl).use { client: HttpClient ->
                val claim: ClaimFile = client.get("claims/CL-7").body()

                assertThat(claim.claimId).isEqualTo("CL-7")
                assertThat(claim.policyNumber).isEqualTo("POL-4410")
                assertThat(claim.events).containsExactly(
                    ClaimEvent.Filed(Severity.TOTAL_LOSS, 1_850_000),
                    ClaimEvent.Assessed("J. Moreau", 1_620_000),
                    ClaimEvent.Paid("PAY-88213"),
                )
                val outstandingCents: Long = claim.events.sumOf { event: ClaimEvent ->
                    when (event) {
                        is ClaimEvent.Filed -> event.claimedCents
                        is ClaimEvent.Assessed -> -event.approvedCents
                        is ClaimEvent.Paid -> 0L
                    }
                }
                assertThat(outstandingCents).isEqualTo(230_000L)
            }
        }
    }

    @Test
    fun aSubmissionIsStreamedWithRenamedIgnoredAndOmittedProperties(): Unit = runBlocking {
        ClaimsService().use { service: ClaimsService ->
            claimsClient(service.baseUrl).use { client: HttpClient ->
                val submission = ClaimSubmission("POL-5120", Severity.MAJOR, 240_000, draftId = "draft-31")
                val response: HttpResponse = client.post("claims") {
                    contentType(ContentType.Application.Json)
                    setBody(submission)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                val filed: ClaimFile = response.body()
                assertThat(filed.claimId).isEqualTo("CL-8")
                assertThat(filed.events.single()).isEqualTo(ClaimEvent.Filed(Severity.MAJOR, 240_000))

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith("application/json")
                assertThat(seen.transferEncoding).isEqualTo("chunked")
                assertThat(seen.contentLength).isNull()
                val sent: JsonNode = jacksonObjectMapper().readTree(seen.body)
                assertThat(sent.path("policy_number").asText()).isEqualTo("POL-5120")
                assertThat(sent.path("severity").asText()).isEqualTo("major")
                assertThat(sent.path("claimedCents").asLong()).isEqualTo(240_000L)
                assertThat(sent.has("policyNumber")).isFalse()
                assertThat(sent.has("draftId")).isFalse()
                assertThat(sent.has("adjusterNote")).isFalse()
            }
        }
    }

    @Test
    fun auditNotesUseTheBufferedConverterRegisteredForTheVendorType(): Unit = runBlocking {
        ClaimsService().use { service: ClaimsService ->
            claimsClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.post("audit") {
                    contentType(AUDIT_JSON)
                    setBody(AuditNote("CL-7", "s.adeyemi", "Payout approved after site visit"))
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith(AUDIT_JSON.toString())
                assertThat(seen.transferEncoding).isNull()
                assertThat(seen.contentLength).isEqualTo(seen.body.toByteArray(Charsets.UTF_8).size.toString())
                assertThat(seen.body).isEqualTo(
                    """{"claim_id":"CL-7","recorded_by":"s.adeyemi","remark":"Payout approved after site visit"}""",
                )
            }
        }
    }

    @Test
    fun adHocDocumentsTravelAsJsonNodeTrees(): Unit = runBlocking {
        ClaimsService().use { service: ClaimsService ->
            claimsClient(service.baseUrl).use { client: HttpClient ->
                val summary: JsonNode = client.get("claims/CL-7/summary").body()
                assertThat(summary.path("open").asBoolean(true)).isFalse()
                assertThat(summary.at("/totals/claimed").asLong() - summary.at("/totals/approved").asLong())
                    .isEqualTo(230_000L)
                assertThat(summary.path("flags").map { flag: JsonNode -> flag.asText() }).containsExactly("fast-track")

                val tagRequest: ObjectNode = jacksonObjectMapper().createObjectNode()
                tagRequest.put("reviewer", "s.adeyemi")
                tagRequest.putArray("add").add("reviewed")
                val updated: ObjectNode = client.post("claims/CL-7/tags") {
                    contentType(ContentType.Application.Json)
                    setBody(tagRequest)
                }.body()

                assertThat(updated.path("tags").map { tag: JsonNode -> tag.asText() })
                    .containsExactly("fast-track", "reviewed")
                assertThat(service.received.single().body)
                    .isEqualTo("""{"reviewer":"s.adeyemi","add":["reviewed"]}""")
            }
        }
    }

    private companion object {
        val AUDIT_JSON: ContentType = ContentType("application", "vnd.claims.audit+json")
    }
}
