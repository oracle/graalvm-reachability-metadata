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
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.gson.gson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.response.respondNullable
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Generic and nullable body types carried to the converters through the plugin's type information.
 *
 * A bulk-repricing service takes a whole list of price changes in one request and answers with
 * the changes grouped by warehouse, a nested per-warehouse total, and the set of warehouses it
 * touched. Current clients speak kotlinx JSON; a legacy back office speaks Gson-encoded JSON
 * under its own vendor type. Shelf notes may be absent, so they are read and written as nullable
 * bodies. The test checks that `List`, `Map`, nested `Map` and `Set` bodies keep their element
 * types through both converters, so the route works with real price changes rather than raw
 * maps, and that a JSON `null` is received as `null` and sent back as `null`.
 */
class BulkRepricingGenericBodyTest {

    @Serializable
    data class PriceChange(val sku: String, val warehouse: String, val newPriceCents: Long)

    @Serializable
    data class ShelfNote(val text: String, val author: String)

    private val applied: ConcurrentLinkedQueue<PriceChange> = ConcurrentLinkedQueue()
    private val notes: ConcurrentHashMap<String, ShelfNote> = ConcurrentHashMap()

    private fun runService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json()
                gson(contentType = LEGACY_JSON)
            }
            routing {
                post("/repricing") {
                    val changes: List<PriceChange> = call.receive()
                    applied.addAll(changes)
                    val byWarehouse: Map<String, List<PriceChange>> = changes.groupBy { it.warehouse }
                    call.respond(byWarehouse)
                }
                get("/repricing/totals") {
                    val totals: Map<String, Map<String, Long>> = applied.groupBy { it.warehouse }
                        .mapValues { (_, changes) ->
                            changes.groupBy { it.sku }.mapValues { (_, perSku) -> perSku.sumOf { it.newPriceCents } }
                        }
                    call.respond(totals)
                }
                get("/repricing/warehouses") {
                    val warehouses: Set<String> = applied.map { it.warehouse }.toSortedSet()
                    call.respond(warehouses)
                }
                get("/notes/{sku}") {
                    val note: ShelfNote? = notes[call.parameters["sku"]!!]
                    call.respondNullable(note)
                }
                put("/notes/{sku}") {
                    val sku: String = call.parameters["sku"]!!
                    val note: ShelfNote? = call.receiveNullable()
                    if (note == null) notes.remove(sku) else notes[sku] = note
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
        block()
    }

    @Test
    fun listBodiesKeepTheirElementTypeThroughKotlinxJson() = runService {
        val response: HttpResponse = client.post("/repricing") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody(Json.encodeToString(BATCH))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertFormat(ContentType.Application.Json, response)
        assertEquals(BATCH.groupBy { it.warehouse }, Json.decodeFromString<Map<String, List<PriceChange>>>(response.bodyAsText()))
        assertEquals(BATCH, applied.toList())
    }

    @Test
    fun listBodiesKeepTheirElementTypeThroughGson() = runService {
        val response: HttpResponse = client.post("/repricing") {
            contentType(LEGACY_JSON)
            header(HttpHeaders.Accept, LEGACY_JSON.toString())
            setBody(Json.encodeToString(BATCH))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertFormat(LEGACY_JSON, response)
        assertEquals(BATCH.groupBy { it.warehouse }, Json.decodeFromString<Map<String, List<PriceChange>>>(response.bodyAsText()))
        assertEquals(BATCH, applied.toList())
    }

    @Test
    fun nestedMapsAndSetsAreEncodedByTheNegotiatedConverter() = runService {
        val posted: HttpResponse = client.post("/repricing") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(BATCH))
        }
        assertEquals(HttpStatusCode.OK, posted.status)

        val expectedTotals: Map<String, Map<String, Long>> = mapOf(
            "berlin" to mapOf("valve-22" to 1_250L, "pump-7" to 48_000L),
            "lyon" to mapOf("valve-22" to 1_310L),
        )
        for (accept: ContentType in listOf(ContentType.Application.Json, LEGACY_JSON)) {
            val totals: HttpResponse = client.get("/repricing/totals") { header(HttpHeaders.Accept, accept.toString()) }
            assertEquals(HttpStatusCode.OK, totals.status)
            assertFormat(accept, totals)
            assertEquals(expectedTotals, Json.decodeFromString<Map<String, Map<String, Long>>>(totals.bodyAsText()))

            val warehouses: HttpResponse = client.get("/repricing/warehouses") { header(HttpHeaders.Accept, accept.toString()) }
            assertEquals(HttpStatusCode.OK, warehouses.status)
            assertFormat(accept, warehouses)
            assertEquals(listOf("berlin", "lyon"), Json.decodeFromString<List<String>>(warehouses.bodyAsText()))
        }
    }

    @Test
    fun nullableBodiesRoundTripJsonNull() = runService {
        val absent: HttpResponse = client.get("/notes/valve-22") { header(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
        assertEquals(HttpStatusCode.OK, absent.status)
        assertFormat(ContentType.Application.Json, absent)
        assertEquals("null", absent.bodyAsText())

        val note = ShelfNote("Recount after Friday delivery", "j.weber")
        val stored: HttpResponse = client.put("/notes/valve-22") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(note))
        }
        assertEquals(HttpStatusCode.NoContent, stored.status)
        assertEquals(note, notes["valve-22"])

        val present: HttpResponse = client.get("/notes/valve-22") { header(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
        assertEquals(HttpStatusCode.OK, present.status)
        assertEquals(note, Json.decodeFromString<ShelfNote>(present.bodyAsText()))

        val cleared: HttpResponse = client.put("/notes/valve-22") {
            contentType(ContentType.Application.Json)
            setBody("null")
        }
        assertEquals(HttpStatusCode.NoContent, cleared.status)
        assertTrue(notes.isEmpty(), notes.toString())

        val gone: HttpResponse = client.get("/notes/valve-22") { header(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
        assertEquals("null", gone.bodyAsText())
    }

    private fun assertFormat(expected: ContentType, response: HttpResponse) {
        val actual: ContentType? = response.contentType()
        assertTrue(actual != null && actual.match(expected), "expected $expected, got $actual")
    }

    private companion object {
        val LEGACY_JSON: ContentType = ContentType("application", "vnd.backoffice+json")

        val BATCH: List<PriceChange> = listOf(
            PriceChange("valve-22", "berlin", 1_250),
            PriceChange("valve-22", "lyon", 1_310),
            PriceChange("pump-7", "berlin", 48_000),
        )
    }
}
