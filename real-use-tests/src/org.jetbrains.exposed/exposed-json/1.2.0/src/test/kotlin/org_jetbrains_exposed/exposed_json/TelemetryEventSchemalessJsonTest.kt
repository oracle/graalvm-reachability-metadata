/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Schemaless and top-level-array JSON columns: a telemetry collector stores free-form event
 * properties as kotlinx `JsonObject` documents and per-event sample series as JSON arrays.
 */
class TelemetryEventSchemalessJsonTest {

    object Events : Table("telemetry_events") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 64)
        val properties = json<JsonObject>("properties", Json)
        val latenciesMs = json<IntArray>("latencies_ms", Json)
        val breadcrumbs = json<List<String>>("breadcrumbs", Json)
        val context = json<JsonArray>("context", Json)

        override val primaryKey = PrimaryKey(id)
    }

    data class Event(
        val name: String,
        val properties: JsonObject,
        val latenciesMs: IntArray,
        val breadcrumbs: List<String>,
        val context: JsonArray,
    )

    @Test
    fun storesAndReadsBackFreeFormEventDocuments() {
        val database = Database.connect(
            url = "jdbc:h2:mem:telemetry_events;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val checkout = Event(
            name = "checkout",
            properties = buildJsonObject {
                put("cartValue", 12_450)
                put("currency", "EUR")
                put("firstPurchase", true)
                putJsonObject("device") {
                    put("os", "linux")
                    put("screen", "1920x1080")
                }
                putJsonArray("coupons") {
                    add(JsonPrimitive("WELCOME10"))
                    add(JsonPrimitive("FREESHIP"))
                }
            },
            latenciesMs = intArrayOf(120, 95, 310),
            breadcrumbs = listOf("/cart", "/checkout/address", "/checkout/pay"),
            context = buildJsonArray {
                add(JsonPrimitive("web"))
                add(JsonPrimitive(3))
                add(buildJsonObject { put("experiment", "one-click") })
            },
        )
        val search = Event(
            name = "search",
            properties = buildJsonObject {
                put("query", "graalvm native image")
                put("results", 42)
                put("firstPurchase", false)
            },
            latenciesMs = intArrayOf(48),
            breadcrumbs = emptyList(),
            context = JsonArray(emptyList()),
        )

        transaction(database) {
            SchemaUtils.create(Events)
            Events.batchInsert(listOf(checkout, search)) { event ->
                this[Events.name] = event.name
                this[Events.properties] = event.properties
                this[Events.latenciesMs] = event.latenciesMs
                this[Events.breadcrumbs] = event.breadcrumbs
                this[Events.context] = event.context
            }
        }

        val stored: Map<String, Event> = transaction(database) {
            Events.selectAll().orderBy(Events.id).associate { row ->
                row[Events.name] to Event(
                    row[Events.name],
                    row[Events.properties],
                    row[Events.latenciesMs],
                    row[Events.breadcrumbs],
                    row[Events.context],
                )
            }
        }
        assertThat(stored.keys).containsExactly("checkout", "search")

        val storedCheckout: Event = stored.getValue("checkout")
        assertThat(storedCheckout.properties).isEqualTo(checkout.properties)
        assertThat(storedCheckout.properties.getValue("cartValue").jsonPrimitive.int).isEqualTo(12_450)
        assertThat(storedCheckout.properties.getValue("device").jsonObject.getValue("os").jsonPrimitive.content)
            .isEqualTo("linux")
        assertThat(storedCheckout.properties.getValue("coupons").jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("WELCOME10", "FREESHIP")
        assertThat(storedCheckout.latenciesMs).containsExactly(120, 95, 310)
        assertThat(storedCheckout.latenciesMs.max()).isEqualTo(310)
        assertThat(storedCheckout.breadcrumbs.last()).isEqualTo("/checkout/pay")
        assertThat(storedCheckout.context).isEqualTo(checkout.context)
        assertThat(storedCheckout.context[2].jsonObject.getValue("experiment").jsonPrimitive.content)
            .isEqualTo("one-click")

        val storedSearch: Event = stored.getValue("search")
        assertThat(storedSearch.properties.getValue("firstPurchase").jsonPrimitive.boolean).isFalse()
        assertThat(storedSearch.properties).doesNotContainKey("device")
        assertThat(storedSearch.latenciesMs).containsExactly(48)
        assertThat(storedSearch.breadcrumbs).isEmpty()
        assertThat(storedSearch.context).isEmpty()

        val enriched = JsonObject(
            storedSearch.properties + ("clickedResult" to JsonPrimitive(3)) - "firstPurchase",
        )
        val updatedRows: Int = transaction(database) {
            Events.update({ Events.name eq "search" }) {
                it[properties] = enriched
                it[latenciesMs] = storedSearch.latenciesMs + 72
                it[breadcrumbs] = listOf("/search", "/product/3")
            }
        }
        assertThat(updatedRows).isEqualTo(1)

        val reloaded: Triple<JsonObject, IntArray, List<String>> = transaction(database) {
            val row = Events.selectAll().where { Events.name eq "search" }.single()
            Triple(row[Events.properties], row[Events.latenciesMs], row[Events.breadcrumbs])
        }
        assertThat(reloaded.first.keys).containsExactlyInAnyOrder("query", "results", "clickedResult")
        assertThat(reloaded.first.getValue("clickedResult").jsonPrimitive.int).isEqualTo(3)
        assertThat(reloaded.second).containsExactly(48, 72)
        assertThat(reloaded.third).containsExactly("/search", "/product/3")

        transaction(database) { SchemaUtils.drop(Events) }
    }
}
