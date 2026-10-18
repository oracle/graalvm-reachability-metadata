/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * kotlinx.serialization JSON negotiation driven by an application SerializersModule: contextual
 * serializers for JDK types, open polymorphism registered at runtime, a hand-written KSerializer,
 * inline value classes, a snake_case naming strategy, and untyped JsonObject tree bodies, as an
 * expense-reporting client submits and reads reports from a finance service.
 */
public class ExpenseReportSerializersModuleNegotiationTest {

    /** Employee identifiers travel as bare JSON strings. */
    @JvmInline
    @Serializable
    value class EmployeeId(val value: String)

    /** A money amount the finance service writes as one string such as `"42.50 EUR"`. */
    @Serializable(with = MoneySerializer::class)
    data class Money(val amount: BigDecimal, val currency: String)

    object MoneySerializer : KSerializer<Money> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("expenses.Money", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Money) {
            encoder.encodeString("${value.amount.toPlainString()} ${value.currency}")
        }

        override fun deserialize(decoder: Decoder): Money {
            val (amount: String, currency: String) = decoder.decodeString().split(' ', limit = 2)
            return Money(BigDecimal(amount), currency)
        }
    }

    object UuidSerializer : KSerializer<UUID> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("expenses.Uuid", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: UUID): Unit = encoder.encodeString(value.toString())
        override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    }

    object InstantSerializer : KSerializer<Instant> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("expenses.Instant", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: Instant): Unit = encoder.encodeString(value.toString())
        override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
    }

    object LocalDateSerializer : KSerializer<LocalDate> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("expenses.LocalDate", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: LocalDate): Unit = encoder.encodeString(value.toString())
        override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
    }

    /** An expense line; the concrete kinds are registered in the module, not sealed. */
    interface ExpenseItem {
        val incurredOn: LocalDate
    }

    @Serializable
    @SerialName("receipt")
    data class ReceiptItem(
        @Contextual override val incurredOn: LocalDate,
        val merchantName: String,
        val total: Money,
    ) : ExpenseItem

    @Serializable
    @SerialName("mileage")
    data class MileageItem(
        @Contextual override val incurredOn: LocalDate,
        val distanceKm: Int,
        val ratePerKm: Money,
    ) : ExpenseItem

    @Serializable
    data class ExpenseReport(
        @Contextual val reportId: UUID,
        val employeeId: EmployeeId,
        @Contextual val submittedAt: Instant,
        val items: List<ExpenseItem>,
        val approverNote: String? = null,
    )

    @Serializable
    data class ReportReceipt(
        @Contextual val reportId: UUID,
        val reviewQueue: String,
        val reimbursable: Money,
    )

    /** What the finance service saw of one incoming request. */
    data class ReceivedRequest(val path: String, val contentType: String?, val body: String)

    /** The finance service; it answers with fixed JSON so the client's decoding is what is checked. */
    class FinanceService : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/reports") {
                    val body: String = call.receiveText()
                    received += ReceivedRequest(call.request.local.uri, call.request.contentType().toString(), body)
                    call.respondText(
                        """
                        {"report_id":"$SUBMITTED_REPORT_ID","review_queue":"travel-eu","reimbursable":"72.30 EUR"}
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                get("/reports/$STORED_REPORT_ID") {
                    received += ReceivedRequest(call.request.local.uri, null, "")
                    call.respondText(
                        """
                        {
                          "report_id": "$STORED_REPORT_ID",
                          "employee_id": "E-1042",
                          "submitted_at": "2026-03-02T08:15:30Z",
                          "items": [
                            {"type":"mileage","incurred_on":"2026-02-27","distance_km":118,"rate_per_km":"0.30 EUR"},
                            {"type":"receipt","incurred_on":"2026-02-28","merchant_name":"Hotel Danube","total":"95.00 EUR"}
                          ],
                          "approver_note": "Approved by cost centre 4410"
                        }
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                get("/reports/$MISSING_REPORT_ID") {
                    received += ReceivedRequest(call.request.local.uri, null, "")
                    call.respondText("null", ContentType.Application.Json)
                }
                post("/policy/check") {
                    val body: String = call.receiveText()
                    received += ReceivedRequest(call.request.local.uri, call.request.contentType().toString(), body)
                    call.respondText(
                        """{"policy":"EU-2026","violations":[{"rule":"hotel-cap","limit":"90.00 EUR"}],"score":82}""",
                        ContentType.Application.Json,
                    )
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

    /** The JSON format the client shares with the finance service. */
    @OptIn(ExperimentalSerializationApi::class)
    private val financeJson: Json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        classDiscriminator = "type"
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(UUID::class, UuidSerializer)
            contextual(Instant::class, InstantSerializer)
            contextual(LocalDate::class, LocalDateSerializer)
            polymorphic(ExpenseItem::class) {
                subclass(ReceiptItem::class)
                subclass(MileageItem::class)
            }
        }
    }

    private fun financeClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            json(financeJson)
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun aReportIsEncodedThroughTheApplicationModule(): Unit = runBlocking {
        FinanceService().use { service: FinanceService ->
            financeClient(service.baseUrl).use { client: HttpClient ->
                val report = ExpenseReport(
                    reportId = UUID.fromString(SUBMITTED_REPORT_ID),
                    employeeId = EmployeeId("E-1042"),
                    submittedAt = Instant.parse("2026-03-05T17:00:00Z"),
                    items = listOf(
                        ReceiptItem(LocalDate.of(2026, 3, 3), "Cafe Central", Money(BigDecimal("18.30"), "EUR")),
                        MileageItem(LocalDate.of(2026, 3, 4), 180, Money(BigDecimal("0.30"), "EUR")),
                    ),
                )

                val receipt: ReportReceipt = client.post("reports") {
                    contentType(ContentType.Application.Json)
                    setBody(report)
                }.body()

                assertThat(receipt).isEqualTo(
                    ReportReceipt(UUID.fromString(SUBMITTED_REPORT_ID), "travel-eu", Money(BigDecimal("72.30"), "EUR")),
                )

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith("application/json")
                assertThat(seen.body)
                    .contains("\"report_id\":\"$SUBMITTED_REPORT_ID\"")
                    .contains("\"employee_id\":\"E-1042\"")
                    .contains("\"submitted_at\":\"2026-03-05T17:00:00Z\"")
                    .contains("\"type\":\"receipt\"")
                    .contains("\"merchant_name\":\"Cafe Central\"")
                    .contains("\"total\":\"18.30 EUR\"")
                    .contains("\"type\":\"mileage\"")
                    .contains("\"distance_km\":180")
                    .doesNotContain("approver_note")
                assertThat(financeJson.decodeFromString(ExpenseReport.serializer(), seen.body)).isEqualTo(report)
            }
        }
    }

    @Test
    fun aStoredReportIsDecodedIntoRegisteredSubclasses(): Unit = runBlocking {
        FinanceService().use { service: FinanceService ->
            financeClient(service.baseUrl).use { client: HttpClient ->
                val report: ExpenseReport = client.get("reports/$STORED_REPORT_ID").body()

                assertThat(report.reportId).isEqualTo(UUID.fromString(STORED_REPORT_ID))
                assertThat(report.employeeId).isEqualTo(EmployeeId("E-1042"))
                assertThat(report.submittedAt).isEqualTo(Instant.parse("2026-03-02T08:15:30Z"))
                assertThat(report.approverNote).isEqualTo("Approved by cost centre 4410")
                assertThat(report.items).containsExactly(
                    MileageItem(LocalDate.of(2026, 2, 27), 118, Money(BigDecimal("0.30"), "EUR")),
                    ReceiptItem(LocalDate.of(2026, 2, 28), "Hotel Danube", Money(BigDecimal("95.00"), "EUR")),
                )
                val mileage: MileageItem = report.items.filterIsInstance<MileageItem>().single()
                assertThat(mileage.ratePerKm.amount.multiply(BigDecimal(mileage.distanceKm)))
                    .isEqualByComparingTo("35.40")
            }
        }
    }

    @Test
    fun aJsonNullReplyDecodesToAnAbsentReport(): Unit = runBlocking {
        FinanceService().use { service: FinanceService ->
            financeClient(service.baseUrl).use { client: HttpClient ->
                val report: ExpenseReport? = client.get("reports/$MISSING_REPORT_ID").body()

                assertThat(report).isNull()
                assertThat(service.received.single().path).isEqualTo("/reports/$MISSING_REPORT_ID")
            }
        }
    }

    @Test
    fun untypedJsonTreesRoundTripThroughThePlugin(): Unit = runBlocking {
        FinanceService().use { service: FinanceService ->
            financeClient(service.baseUrl).use { client: HttpClient ->
                val query: JsonObject = buildJsonObject {
                    put("policy", "EU-2026")
                    put("country", "AT")
                    putJsonArray("categories") {
                        add("lodging")
                        add("meals")
                    }
                }

                val verdict: JsonObject = client.post("policy/check") {
                    contentType(ContentType.Application.Json)
                    setBody(query)
                }.body()

                assertThat(verdict["score"]!!.jsonPrimitive.int).isEqualTo(82)
                val violation: JsonObject = verdict["violations"]!!.jsonArray.single().jsonObject
                assertThat(violation["rule"]!!.jsonPrimitive.content).isEqualTo("hotel-cap")
                assertThat(financeJson.decodeFromJsonElement(MoneySerializer, violation["limit"]!!))
                    .isEqualTo(Money(BigDecimal("90.00"), "EUR"))

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith("application/json")
                assertThat(financeJson.parseToJsonElement(seen.body)).isEqualTo(query)
            }
        }
    }

    private companion object {
        const val SUBMITTED_REPORT_ID: String = "3f2b8c1e-7d4a-4e5b-9c61-0a8d2f4b7e19"
        const val STORED_REPORT_ID: String = "a61c0f9d-2b7e-4c38-8d15-5e9a3b7c2f40"
        const val MISSING_REPORT_ID: String = "00000000-0000-4000-8000-000000000000"
    }
}
