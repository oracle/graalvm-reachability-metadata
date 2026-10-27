/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.Either
import arrow.core.Ior
import arrow.core.NonEmptyList
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.serialization.ArrowModule
import arrow.core.serialization.EitherSerializer
import arrow.core.serialization.IorSerializer
import arrow.core.serialization.NonEmptyListSerializer
import arrow.core.some
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JSON serialization of Arrow data types (`Either`, `Option`, `Ior`, `NonEmptyList`) through
 * the kotlinx.serialization serializers and `ArrowModule`.
 *
 * An order-sync service answers each warehouse push with a response that keeps its Arrow
 * types on the wire: the outcome is an `Either` of a failure or an accepted snapshot, the
 * snapshot's lines are a `NonEmptyList`, an optional coupon is an `Option`, and the stock
 * reconciliation is an `Ior` that can carry warnings and a count at the same time. The
 * warehouse client decodes the JSON back into the same Arrow values and branches on them.
 */
class OrderSyncJsonSerializationTest {

    @Serializable
    data class OrderLine(val sku: String, val quantity: Int)

    @Serializable
    data class SyncFailure(val code: String, val retryable: Boolean)

    @Serializable
    data class OrderSnapshot(
        val orderId: String,
        @Serializable(with = NonEmptyListSerializer::class)
        val lines: NonEmptyList<OrderLine>,
        @Contextual
        val coupon: Option<String>,
    )

    @Serializable
    data class SyncResponse(
        val requestId: Long,
        @Serializable(with = EitherSerializer::class)
        val outcome: Either<SyncFailure, OrderSnapshot>,
        @Serializable(with = IorSerializer::class)
        val reconciliation: Ior<@Serializable(with = NonEmptyListSerializer::class)
            NonEmptyList<String>, Int>,
    )

    private val json: Json = Json {
        serializersModule = ArrowModule
        encodeDefaults = true
    }

    private fun roundTrip(response: SyncResponse): Pair<String, SyncResponse> {
        val encoded: String = json.encodeToString(SyncResponse.serializer(), response)
        val decoded: SyncResponse = json.decodeFromString(SyncResponse.serializer(), encoded)
        return encoded to decoded
    }

    private fun describe(response: SyncResponse): String {
        val outcome: String = response.outcome.fold(
            ifLeft = { failure -> "failed ${failure.code} retry=${failure.retryable}" },
            ifRight = { snapshot ->
                val units: Int = snapshot.lines.sumOf { line -> line.quantity }
                val coupon: String = snapshot.coupon.getOrElse { "none" }
                "accepted ${snapshot.orderId} first=${snapshot.lines.head.sku} " +
                    "units=$units coupon=$coupon"
            },
        )
        val reconciliation: String = response.reconciliation.fold(
            { warnings -> "warnings=${warnings.all}" },
            { count -> "reconciled=$count" },
            { warnings, count -> "reconciled=$count warnings=${warnings.all}" },
        )
        return "$outcome; $reconciliation"
    }

    @Test
    fun acceptedOrderWithCouponSurvivesTheWire() {
        val response = SyncResponse(
            requestId = 41L,
            outcome = OrderSnapshot(
                orderId = "ORD-1001",
                lines = nonEmptyListOf(OrderLine("SKU-RED", 2), OrderLine("SKU-BLUE", 3)),
                coupon = "SPRING10".some(),
            ).right(),
            reconciliation = Ior.Right(5),
        )

        val (encoded: String, decoded: SyncResponse) = roundTrip(response)

        assertEquals(response, decoded)
        assertEquals(
            "accepted ORD-1001 first=SKU-RED units=5 coupon=SPRING10; reconciled=5",
            describe(decoded),
        )
        assertEquals(
            """{"requestId":41,"outcome":{"right":{"orderId":"ORD-1001","lines":[""" +
                """{"sku":"SKU-RED","quantity":2},{"sku":"SKU-BLUE","quantity":3}],""" +
                """"coupon":"SPRING10"}},"reconciliation":{"right":5}}""",
            encoded,
        )
        val tree: JsonObject = json.parseToJsonElement(encoded).jsonObject
        assertEquals(41, tree.getValue("requestId").jsonPrimitive.int)
    }

    @Test
    fun rejectedOrderAndPartialReconciliationDecodeFromWarehouseJson() {
        val warehouseJson: String = """
            {
              "requestId": 42,
              "outcome": {"left": {"code": "STOCK_LOCKED", "retryable": true}},
              "reconciliation": {"left": ["SKU-RED oversold", "SKU-GREEN unknown"], "right": 1}
            }
        """.trimIndent()

        val decoded: SyncResponse = json.decodeFromString(SyncResponse.serializer(), warehouseJson)

        val expected = SyncResponse(
            requestId = 42L,
            outcome = SyncFailure(code = "STOCK_LOCKED", retryable = true).left(),
            reconciliation = Ior.Both(nonEmptyListOf("SKU-RED oversold", "SKU-GREEN unknown"), 1),
        )
        assertEquals(expected, decoded)
        assertTrue(decoded.outcome.isLeft())
        assertTrue(decoded.reconciliation.isBoth())
        assertEquals(
            "failed STOCK_LOCKED retry=true; reconciled=1 " +
                "warnings=[SKU-RED oversold, SKU-GREEN unknown]",
            describe(decoded),
        )
        val (encoded: String, roundTripped: SyncResponse) = roundTrip(decoded)
        assertEquals(decoded, roundTripped)
        assertFalse(encoded.contains("orderId"), encoded)
    }

    @Test
    fun orderWithoutCouponKeepsOptionEmpty() {
        val snapshot = OrderSnapshot(
            orderId = "ORD-1002",
            lines = nonEmptyListOf(OrderLine("SKU-GREEN", 1)),
            coupon = None,
        )
        val response = SyncResponse(
            requestId = 43L,
            outcome = snapshot.right(),
            reconciliation = Ior.Left(nonEmptyListOf("late delivery")),
        )

        val (encoded: String, decoded: SyncResponse) = roundTrip(response)

        assertEquals(response, decoded)
        assertTrue(encoded.contains("\"coupon\":null"), encoded)
        assertTrue(encoded.endsWith("\"reconciliation\":{\"left\":[\"late delivery\"]}}"), encoded)
        val decodedSnapshot: OrderSnapshot = decoded.outcome.getOrNull()!!
        assertEquals(None, decodedSnapshot.coupon)
        assertEquals(1, decodedSnapshot.lines.size)
        assertEquals(
            "accepted ORD-1002 first=SKU-GREEN units=1 coupon=none; warnings=[late delivery]",
            describe(decoded),
        )
    }

    @Test
    fun nonEmptyLinesAndContextualCouponsRoundTripOnTheirOwn() {
        val coupons: List<Option<String>> = listOf(Some("A1"), None, Some("B2"))
        val lines: NonEmptyList<OrderLine> =
            nonEmptyListOf(OrderLine("SKU-X", 7), OrderLine("SKU-Y", 1))
        val linesSerializer: NonEmptyListSerializer<OrderLine> =
            NonEmptyListSerializer(OrderLine.serializer())
        val encodedLines: String = json.encodeToString(linesSerializer, lines)
        val array: JsonArray = json.parseToJsonElement(encodedLines).jsonArray
        assertEquals(2, array.size)
        assertEquals("SKU-X", array[0].jsonObject.getValue("sku").jsonPrimitive.content)
        assertEquals(lines, json.decodeFromString(linesSerializer, encodedLines))

        val couponSnapshots: List<OrderSnapshot> = coupons.map { coupon ->
            OrderSnapshot("ORD-C", lines, coupon)
        }
        val decodedCoupons: List<Option<String>> = couponSnapshots.map { snapshot ->
            val text: String = json.encodeToString(OrderSnapshot.serializer(), snapshot)
            json.decodeFromString(OrderSnapshot.serializer(), text).coupon
        }
        assertEquals(coupons, decodedCoupons)

        val emptyOrder: String = """{"orderId":"ORD-EMPTY","lines":[],"coupon":null}"""
        val rejected: Either<Throwable, OrderSnapshot> = Either.catch {
            json.decodeFromString(OrderSnapshot.serializer(), emptyOrder)
        }
        assertTrue(rejected.isLeft(), rejected.toString())
    }
}
