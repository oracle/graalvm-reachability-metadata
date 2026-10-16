/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.gson.GsonWebsocketContentConverter
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import java.time.LocalDate
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Gson messaging over WebSockets, where plain Kotlin classes travel through
 * `sendSerialized` and `receiveDeserialized` with the Gson WebSocket converter, a
 * snake_case `Gson` and a registered type adapter for `LocalDate`.
 *
 * A warehouse answers stock queries with per-SKU levels and restock dates, pushes
 * its low-stock list as a generic list, and closes when a query names no SKU.
 */
public class GsonWarehouseStockTest {
    /** A request for the stock of [skus]; an empty list ends the session. */
    data class StockQuery(val skus: List<String> = emptyList())

    /** The units of one SKU on hand, and when the next delivery arrives. */
    data class StockLevel(val sku: String = "", val unitsOnHand: Int = 0, val nextRestock: LocalDate? = null)

    /** The warehouse's answer to one query. */
    data class StockReport(
        val warehouse: String = "",
        val levels: List<StockLevel> = emptyList(),
        val totalUnits: Int = 0,
        val unknownSkus: List<String> = emptyList(),
    )

    /** Writes a `LocalDate` as its ISO-8601 string and reads it back. */
    class IsoDateAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        override fun serialize(src: LocalDate, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
            JsonPrimitive(src.toString())

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDate =
            LocalDate.parse(json.asString)
    }

    @Test
    fun `stock queries are answered until a query names no SKU`(): Unit = warehouse { client: HttpClient ->
        val queries: List<StockQuery> = listOf(
            StockQuery(listOf("bolt-m8", "nut-m8")),
            StockQuery(listOf("washer-m8", "rivet-4")),
        )
        val reports: MutableList<StockReport> = mutableListOf()
        var observedClose: CloseReason? = null

        client.webSocket("/stock") {
            for (query: StockQuery in queries) {
                sendSerialized(query)
                reports += receiveDeserialized<StockReport>()
            }
            sendSerialized(StockQuery())
            observedClose = closeReason.await()
        }

        assertEquals(
            listOf(
                StockReport("north-dock", listOf(INVENTORY.getValue("bolt-m8"), INVENTORY.getValue("nut-m8")), 1_540),
                StockReport("north-dock", listOf(INVENTORY.getValue("washer-m8")), 0, listOf("rivet-4")),
            ),
            reports,
        )
        assertEquals(LocalDate.of(2026, 11, 3), reports[1].levels.single().nextRestock)
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "answered 2 queries"), observedClose)
    }

    @Test
    fun `low-stock alerts arrive as a typed generic list`(): Unit = warehouse { client: HttpClient ->
        var alerts: List<StockLevel>? = null
        var observedClose: CloseReason? = null

        client.webSocket("/low-stock") {
            alerts = receiveDeserialized<List<StockLevel>>()
            observedClose = closeReason.await()
        }

        assertEquals(listOf(INVENTORY.getValue("washer-m8"), INVENTORY.getValue("nut-m8")), alerts)
        assertEquals(StockLevel::class.java, alerts!!.first().javaClass)
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "2 SKUs below 500 units"), observedClose)
    }

    @Test
    fun `reports travel as snake_case JSON text with ISO dates`(): Unit = warehouse { client: HttpClient ->
        var reportJson: JsonObject? = null

        client.webSocket("/stock") {
            send(Frame.Text("""{"skus":["washer-m8"]}"""))
            val reply: Frame = incoming.receive()
            reportJson = WAREHOUSE_GSON.fromJson(
                assertInstanceOf(Frame.Text::class.java, reply).readText(),
                JsonObject::class.java,
            )
            sendSerialized(StockQuery())
            closeReason.await()
        }

        val report: JsonObject = reportJson!!
        assertEquals("north-dock", report.get("warehouse").asString)
        assertEquals(0, report.get("total_units").asInt)
        val level: JsonObject = report.getAsJsonArray("levels")[0].asJsonObject
        assertEquals(0, level.get("units_on_hand").asInt)
        assertEquals("2026-11-03", level.get("next_restock").asString)
    }

    /** Runs [scenario] against the warehouse application with a Gson WebSocket client. */
    private fun warehouse(scenario: suspend (HttpClient) -> Unit): Unit = testApplication {
        application {
            install(ServerWebSockets) {
                contentConverter = GsonWebsocketContentConverter(WAREHOUSE_GSON)
            }
            routing {
                webSocket("/stock") {
                    var answered: Int = 0
                    while (true) {
                        val query: StockQuery = receiveDeserialized<StockQuery>()
                        if (query.skus.isEmpty()) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "answered $answered queries"))
                            break
                        }
                        answered++
                        sendSerialized(report(query))
                    }
                }
                webSocket("/low-stock") {
                    val low: List<StockLevel> = INVENTORY.values
                        .filter { it.unitsOnHand < LOW_STOCK_UNITS }
                        .sortedBy { it.unitsOnHand }
                    sendSerialized(low)
                    close(CloseReason(CloseReason.Codes.NORMAL, "${low.size} SKUs below $LOW_STOCK_UNITS units"))
                }
            }
        }
        withTimeout(TIMEOUT_MILLIS) {
            scenario(gsonClient())
        }
    }

    private fun ApplicationTestBuilder.gsonClient(): HttpClient = createClient {
        install(ClientWebSockets) {
            contentConverter = GsonWebsocketContentConverter(WAREHOUSE_GSON)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS: Long = 30_000
        const val LOW_STOCK_UNITS: Int = 500

        val INVENTORY: Map<String, StockLevel> = listOf(
            StockLevel("bolt-m8", 1_200),
            StockLevel("nut-m8", 340, LocalDate.of(2026, 10, 28)),
            StockLevel("washer-m8", 0, LocalDate.of(2026, 11, 3)),
        ).associateBy { it.sku }

        val WAREHOUSE_GSON: Gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(LocalDate::class.java, IsoDateAdapter())
            .create()

        /** Builds the warehouse's report for [query] from [INVENTORY]. */
        fun report(query: StockQuery): StockReport {
            val (known: List<String>, unknown: List<String>) = query.skus.partition { it in INVENTORY }
            val levels: List<StockLevel> = known.map { INVENTORY.getValue(it) }
            return StockReport("north-dock", levels, levels.sumOf { it.unitsOnHand }, unknown)
        }
    }
}
