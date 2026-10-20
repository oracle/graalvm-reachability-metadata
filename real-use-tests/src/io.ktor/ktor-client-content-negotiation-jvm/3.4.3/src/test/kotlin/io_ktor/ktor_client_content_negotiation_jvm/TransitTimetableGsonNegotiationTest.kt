/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.FieldNamingPolicy
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.gson.gson
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Gson content negotiation through the client ContentNegotiation plugin with an application-configured
 * GsonBuilder, as a transit application reads departures from an operator service, reserves seats,
 * and decodes a timetable it requests in ISO-8859-1.
 */
public class TransitTimetableGsonNegotiationTest {

    enum class DepartureStatus {
        @SerializedName("on-time")
        ON_TIME,

        @SerializedName("delayed")
        DELAYED,

        @SerializedName("cancelled")
        CANCELLED,
    }

    data class Departure(
        val trainNumber: String = "",
        val destination: String = "",
        val departsAt: LocalTime = LocalTime.MIDNIGHT,
        val platform: String? = null,
        val status: DepartureStatus = DepartureStatus.ON_TIME,
        val delayMinutes: Int = 0,
    )

    data class SeatReservation(
        val trainNumber: String = "",
        val passengerName: String = "",
        val departsAt: LocalTime = LocalTime.MIDNIGHT,
        val seatNumber: String? = null,
    )

    data class ReservationConfirmation(
        val reference: String = "",
        val trainNumber: String = "",
        val seatNumber: String? = null,
        val boardingFrom: LocalTime = LocalTime.MIDNIGHT,
    )

    data class Timetable(val station: String = "", val departures: List<Departure> = emptyList())

    /** Operator bookkeeping the transit application never decodes; its Gson setup skips the class. */
    data class OperatorShiftNote(val driver: String = "", val remark: String = "")

    /** Writes and reads clock times as `HH:mm`, the format the operator publishes. */
    class ClockTimeAdapter : TypeAdapter<LocalTime>() {
        override fun write(writer: JsonWriter, value: LocalTime?) {
            if (value == null) {
                writer.nullValue()
            } else {
                writer.value(value.format(CLOCK))
            }
        }

        override fun read(reader: JsonReader): LocalTime = LocalTime.parse(reader.nextString(), CLOCK)
    }

    /** Keeps operator-internal classes out of the application's JSON handling. */
    class SkipOperatorInternals : ExclusionStrategy {
        override fun shouldSkipClass(clazz: Class<*>): Boolean = clazz == OperatorShiftNote::class.java

        override fun shouldSkipField(field: FieldAttributes): Boolean = false
    }

    /** What the operator service saw of one incoming request. */
    data class ReceivedRequest(val path: String, val accept: String?, val contentType: String?, val body: String)

    /** The operator service; it answers with fixed bodies so the client's decoding is what is checked. */
    class OperatorService : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/departures") {
                    received += ReceivedRequest(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    call.respondText(
                        """
                        [
                          {"train_number":"IC 712","destination":"Basel SBB","departs_at":"07:45",
                           "platform":"7","status":"on-time","delay_minutes":0,"rolling_stock":"IC2000"},
                          {"train_number":"RE 4018","destination":"Olten","departs_at":"08:02",
                           "platform":null,"status":"delayed","delay_minutes":6},
                          {"train_number":"S 3","destination":"Wetzikon","departs_at":"08:10",
                           "status":"cancelled"}
                        ]
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                post("/reservations") {
                    val body: String = call.receiveText()
                    received += ReceivedRequest(
                        call.request.local.uri,
                        call.request.header(HttpHeaders.Accept),
                        call.request.contentType().toString(),
                        body,
                    )
                    call.respondText(
                        """
                        {"reference":"RSV-55120","train_number":"IC 712","seat_number":"21A",
                         "boarding_from":"07:35"}
                        """.trimIndent(),
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
                get("/timetable") {
                    received += ReceivedRequest(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    val charset: Charset =
                        if (call.request.header(HttpHeaders.AcceptCharset) == "ISO-8859-1") Charsets.ISO_8859_1 else Charsets.UTF_8
                    val json = """
                        {"station":"$STATION","departures":[
                          {"train_number":"IR 15","destination":"$DESTINATION","departs_at":"09:12","platform":"4"},
                          {"train_number":"TER 96520","destination":"Annemasse","departs_at":"09:20","platform":"1"}
                        ]}
                    """.trimIndent()
                    call.respondBytes(
                        json.toByteArray(charset),
                        ContentType.Application.Json.withCharset(charset),
                    )
                }
                get("/shift-notes/latest") {
                    call.respondText("""{"driver":"P. Moser","remark":"late handover"}""", ContentType.Application.Json)
                }
                get("/departures/broken") {
                    call.respondText("""[{"train_number":"IC 712","departs_at":"07:45",""", ContentType.Application.Json)
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

    private fun operatorClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            gson {
                setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                registerTypeAdapter(LocalTime::class.java, ClockTimeAdapter())
                setExclusionStrategies(SkipOperatorInternals())
                serializeNulls()
            }
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun departuresAreDecodedWithSnakeCaseNamesAndClockTimes(): Unit = runBlocking {
        OperatorService().use { service: OperatorService ->
            operatorClient(service.baseUrl).use { client: HttpClient ->
                val departures: List<Departure> = client.get("departures").body()

                assertThat(departures).containsExactly(
                    Departure("IC 712", "Basel SBB", LocalTime.of(7, 45), "7", DepartureStatus.ON_TIME, 0),
                    Departure("RE 4018", "Olten", LocalTime.of(8, 2), null, DepartureStatus.DELAYED, 6),
                    Departure("S 3", "Wetzikon", LocalTime.of(8, 10), null, DepartureStatus.CANCELLED, 0),
                )
                val delayed: Departure = departures.single { departure: Departure -> departure.status == DepartureStatus.DELAYED }
                assertThat(delayed.departsAt.plusMinutes(delayed.delayMinutes.toLong())).isEqualTo(LocalTime.of(8, 8))
                assertThat(service.received.single().accept).contains("application/json")
            }
        }
    }

    @Test
    fun aReservationIsWrittenWithNullsKeptAndTheConfirmationDecoded(): Unit = runBlocking {
        OperatorService().use { service: OperatorService ->
            operatorClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.post("reservations") {
                    contentType(ContentType.Application.Json)
                    setBody(SeatReservation("IC 712", "Ana Kova\u010d", LocalTime.of(7, 45)))
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                val confirmation: ReservationConfirmation = response.body()
                assertThat(confirmation)
                    .isEqualTo(ReservationConfirmation("RSV-55120", "IC 712", "21A", LocalTime.of(7, 35)))

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith("application/json")
                val written = JsonParser.parseString(seen.body).asJsonObject
                assertThat(written.keySet())
                    .containsExactlyInAnyOrder("train_number", "passenger_name", "departs_at", "seat_number")
                assertThat(written.get("seat_number").isJsonNull).isTrue()
                assertThat(written.get("departs_at").asString).isEqualTo("07:45")
                assertThat(written.get("passenger_name").asString).isEqualTo("Ana Kova\u010d")
            }
        }
    }

    @Test
    fun aLatin1TimetableIsDecodedWithTheCharsetTheRequestAccepts(): Unit = runBlocking {
        OperatorService().use { service: OperatorService ->
            operatorClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("timetable") {
                    header(HttpHeaders.AcceptCharset, "ISO-8859-1")
                }

                assertThat(response.contentType()?.charset()).isEqualTo(Charsets.ISO_8859_1)
                val timetable: Timetable = response.body()
                assertThat(timetable.station).isEqualTo(STATION)
                assertThat(timetable.departures.map { departure: Departure -> departure.destination })
                    .containsExactly(DESTINATION, "Annemasse")
                assertThat(timetable.departures.first().departsAt).isEqualTo(LocalTime.of(9, 12))
                assertThat(timetable.departures.map { departure: Departure -> departure.status })
                    .containsOnly(DepartureStatus.ON_TIME)
            }
        }
    }

    @Test
    fun aClassTheGsonSetupExcludesIsRefusedInsteadOfDecoded(): Unit = runBlocking {
        OperatorService().use { service: OperatorService ->
            operatorClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("shift-notes/latest")

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThatThrownBy { runBlocking { response.body<OperatorShiftNote>() } }
                    .hasMessageContaining(OperatorShiftNote::class.java.name)
                    .hasMessageContaining("is excluded")
            }
        }
    }

    @Test
    fun aTruncatedDepartureListSurfacesAsAJsonConversionFailure(): Unit = runBlocking {
        OperatorService().use { service: OperatorService ->
            operatorClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("departures/broken")

                assertThatThrownBy { runBlocking { response.body<List<Departure>>() } }
                    .isInstanceOf(JsonConvertException::class.java)
                    .hasMessageContaining("Illegal json parameter found")
            }
        }
    }

    private companion object {
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        const val STATION: String = "Gen\u00e8ve-Cornavin"
        const val DESTINATION: String = "Z\u00fcrich HB"
    }
}
