/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import java.math.BigDecimal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the `JsonElement` tree API as a dynamic document model: `JsonElement`-typed
 * properties, `buildJsonObject`/`buildJsonArray` DSL, `encodeToJsonElement` and
 * `decodeFromJsonElement`, typed primitive accessors, `JsonNull`, and `JsonUnquotedLiteral`.
 *
 * An inventory service answers JSON-RPC 2.0 calls: the envelope keeps `id`, `params`, and
 * `result` as raw trees until the method name picks the typed parameters, and errors and
 * batch replies are assembled by hand with the builder DSL.
 */
@OptIn(ExperimentalSerializationApi::class)
public class JsonRpcDispatcherElementTreeTest {

    @Serializable
    public data class RpcRequest(
        val jsonrpc: String,
        val method: String,
        val params: JsonElement = JsonNull,
        val id: JsonElement = JsonNull,
    )

    @Serializable
    public data class StockQuery(val sku: String, val warehouse: String = "main")

    @Serializable
    public data class StockLevel(val sku: String, val warehouse: String, val onHand: Int)

    @Serializable
    public data class Reservation(val sku: String, val quantity: Int, val note: String? = null)

    /** Holds stock and turns raw JSON-RPC requests into raw JSON-RPC replies. */
    public class InventoryDispatcher(private val json: Json) {
        private val stock: MutableMap<String, Int> = mutableMapOf("BOLT-M6" to 120, "NUT-M6" to 80)
        private val unitPrices: Map<String, String> = mapOf(
            "BOLT-M6" to "0.0425000000000000000001",
            "NUT-M6" to "0.0180",
        )

        public fun handle(request: JsonElement): JsonElement? =
            if (request is JsonArray) {
                val replies: List<JsonElement> = request.mapNotNull { handleSingle(it) }
                if (replies.isEmpty()) null else JsonArray(replies)
            } else {
                handleSingle(request)
            }

        private fun handleSingle(raw: JsonElement): JsonElement? {
            val call: RpcRequest = try {
                json.decodeFromJsonElement<RpcRequest>(raw)
            } catch (e: SerializationException) {
                return error(JsonNull, -32600, "Invalid Request")
            }
            val isNotification: Boolean = call.id is JsonNull
            val reply: JsonElement = try {
                success(call.id, invoke(call.method, call.params))
            } catch (e: NoSuchElementException) {
                error(call.id, -32601, "Method not found: ${call.method}")
            } catch (e: IllegalArgumentException) {
                error(call.id, -32602, e.message ?: "Invalid params")
            }
            return if (isNotification) null else reply
        }

        private fun invoke(method: String, params: JsonElement): JsonElement = when (method) {
            "stock.get" -> {
                val query: StockQuery = json.decodeFromJsonElement<StockQuery>(params)
                val onHand: Int = stock[query.sku] ?: throw IllegalArgumentException(
                    "Unknown sku ${query.sku}",
                )
                json.encodeToJsonElement(StockLevel(query.sku, query.warehouse, onHand))
            }
            "stock.reserve" -> {
                val reservations: List<Reservation> =
                    json.decodeFromJsonElement<List<Reservation>>(params)
                reservations.forEach { reserve(it) }
                buildJsonObject {
                    put("reserved", reservations.sumOf { it.quantity })
                    putJsonArray("remaining") {
                        reservations.map { it.sku }.distinct().forEach { sku ->
                            addJsonObject {
                                put("sku", sku)
                                put("onHand", stock.getValue(sku))
                            }
                        }
                    }
                }
            }
            "price.unit" -> {
                val sku: String = params.jsonArray[0].jsonPrimitive.content
                val price: String = unitPrices[sku]
                    ?: throw IllegalArgumentException("Unknown sku $sku")
                buildJsonObject {
                    put("sku", sku)
                    put("unitPrice", JsonUnquotedLiteral(price))
                }
            }
            else -> throw NoSuchElementException(method)
        }

        private fun reserve(reservation: Reservation) {
            val onHand: Int = stock[reservation.sku]
                ?: throw IllegalArgumentException("Unknown sku ${reservation.sku}")
            require(reservation.quantity in 1..onHand) {
                "Cannot reserve ${reservation.quantity} of ${reservation.sku}"
            }
            stock[reservation.sku] = onHand - reservation.quantity
        }

        private fun success(id: JsonElement, result: JsonElement): JsonObject = buildJsonObject {
            put("jsonrpc", "2.0")
            put("result", result)
            put("id", id)
        }

        private fun error(id: JsonElement, code: Int, message: String): JsonObject =
            buildJsonObject {
                put("jsonrpc", "2.0")
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                }
                put("id", id)
            }
    }

    private val json: Json = Json { ignoreUnknownKeys = true }

    private fun call(dispatcher: InventoryDispatcher, request: String): JsonElement? =
        dispatcher.handle(json.parseToJsonElement(request))

    @Test
    fun answersTypedCallWithStringAndNumericIds() {
        val dispatcher: InventoryDispatcher = InventoryDispatcher(json)

        val byString: JsonObject = call(
            dispatcher,
            """{"jsonrpc":"2.0","method":"stock.get","params":{"sku":"BOLT-M6"},"id":"q-1"}""",
        )!!.jsonObject
        val byNumber: JsonObject = call(
            dispatcher,
            """{"jsonrpc":"2.0","method":"stock.get",
               "params":{"sku":"NUT-M6","warehouse":"east"},"id":7}""",
        )!!.jsonObject

        assertEquals(
            """{"jsonrpc":"2.0","result":{"sku":"BOLT-M6","warehouse":"main","onHand":120},"id":"q-1"}""",
            json.encodeToString(byString),
        )
        val idString: JsonPrimitive = byString.getValue("id").jsonPrimitive
        assertTrue(idString.isString)
        assertNull(idString.intOrNull)

        val idNumber: JsonPrimitive = byNumber.getValue("id").jsonPrimitive
        assertFalse(idNumber.isString)
        assertEquals(7L, idNumber.longOrNull)
        val level: StockLevel = json.decodeFromJsonElement<StockLevel>(byNumber.getValue("result"))
        assertEquals(StockLevel("NUT-M6", "east", 80), level)
    }

    @Test
    fun processesBatchWithNotificationsAndErrors() {
        val dispatcher: InventoryDispatcher = InventoryDispatcher(json)
        val batch: JsonArray = buildJsonArray {
            addJsonObject {
                put("jsonrpc", "2.0")
                put("method", "stock.reserve")
                putJsonArray("params") {
                    addJsonObject {
                        put("sku", "BOLT-M6")
                        put("quantity", 20)
                        put("note", "line 4")
                    }
                    addJsonObject {
                        put("sku", "BOLT-M6")
                        put("quantity", 5)
                    }
                }
                put("id", 1)
            }
            addJsonObject {
                put("jsonrpc", "2.0")
                put("method", "stock.reserve")
                putJsonArray("params") {
                    addJsonObject {
                        put("sku", "NUT-M6")
                        put("quantity", 10)
                    }
                }
            }
            addJsonObject {
                put("jsonrpc", "2.0")
                put("method", "stock.audit")
                put("id", 2)
            }
            addJsonObject {
                put("jsonrpc", "2.0")
                put("method", "stock.reserve")
                putJsonArray("params") {
                    addJsonObject {
                        put("sku", "NUT-M6")
                        put("quantity", 500)
                    }
                }
                put("id", 3)
            }
            add("not a request")
        }

        val replies: JsonArray = dispatcher.handle(batch)!!.jsonArray

        assertEquals(4, replies.size)
        val reserved: JsonObject = replies[0].jsonObject.getValue("result").jsonObject
        assertEquals(25, reserved.getValue("reserved").jsonPrimitive.int)
        assertEquals(
            buildJsonArray {
                addJsonObject {
                    put("sku", "BOLT-M6")
                    put("onHand", 95)
                }
            },
            reserved.getValue("remaining"),
        )

        val unknownMethod: JsonObject = replies[1].jsonObject
        assertEquals(-32601, unknownMethod.getValue("error").jsonObject.getValue("code").jsonPrimitive.int)
        assertEquals(2, unknownMethod.getValue("id").jsonPrimitive.int)
        assertFalse("result" in unknownMethod)

        val overReserved: JsonObject = replies[2].jsonObject.getValue("error").jsonObject
        assertEquals(-32602, overReserved.getValue("code").jsonPrimitive.int)
        assertEquals(
            "Cannot reserve 500 of NUT-M6",
            overReserved.getValue("message").jsonPrimitive.contentOrNull,
        )

        val invalid: JsonObject = replies[3].jsonObject
        assertEquals(JsonNull, invalid.getValue("id"))
        assertNull(invalid.getValue("id").jsonPrimitive.contentOrNull)
        assertEquals(-32600, invalid.getValue("error").jsonObject.getValue("code").jsonPrimitive.int)

        val level: JsonObject = call(
            dispatcher,
            """{"jsonrpc":"2.0","method":"stock.get","params":{"sku":"NUT-M6"},"id":4}""",
        )!!.jsonObject
        assertEquals(70, level.getValue("result").jsonObject.getValue("onHand").jsonPrimitive.int)
    }

    @Test
    fun dropsRepliesForAllNotificationBatch() {
        val dispatcher: InventoryDispatcher = InventoryDispatcher(json)
        val batch: String = """
            [
              {"jsonrpc":"2.0","method":"stock.reserve","params":[{"sku":"BOLT-M6","quantity":1}]},
              {"jsonrpc":"2.0","method":"stock.reserve","params":[{"sku":"BOLT-M6","quantity":2}]}
            ]
        """.trimIndent()

        assertNull(call(dispatcher, batch))

        val level: JsonObject = call(
            dispatcher,
            """{"jsonrpc":"2.0","method":"stock.get","params":{"sku":"BOLT-M6"},"id":"after"}""",
        )!!.jsonObject
        assertEquals(117, level.getValue("result").jsonObject.getValue("onHand").jsonPrimitive.int)
    }

    @Test
    fun keepsDecimalPrecisionWithUnquotedLiteral() {
        val dispatcher: InventoryDispatcher = InventoryDispatcher(json)

        val reply: JsonElement = call(
            dispatcher,
            """{"jsonrpc":"2.0","method":"price.unit","params":["BOLT-M6"],"id":9}""",
        )!!
        val text: String = json.encodeToString(reply)

        assertEquals(
            """{"jsonrpc":"2.0","result":{"sku":"BOLT-M6","unitPrice":0.0425000000000000000001},"id":9}""",
            text,
        )

        val unitPrice: JsonPrimitive = json.decodeFromString<JsonObject>(text)
            .getValue("result").jsonObject.getValue("unitPrice").jsonPrimitive
        assertFalse(unitPrice.isString)
        assertEquals(BigDecimal("0.0425000000000000000001"), BigDecimal(unitPrice.content))
        assertEquals(0.0425, unitPrice.double)
        assertNull(unitPrice.booleanOrNull)
    }
}
