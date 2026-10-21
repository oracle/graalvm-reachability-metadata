/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises sealed-class polymorphism in `Json`: a `@JsonClassDiscriminator` hierarchy whose
 * subclasses are selected by `@SerialName`, with a nested sealed amount under the default
 * `type` discriminator, decoded from and re-encoded to JSON text.
 *
 * A payment service receives webhook batches from its provider, folds them into account
 * balances, and writes the accepted events to an audit log in the same wire shape.
 */
@OptIn(ExperimentalSerializationApi::class)
public class PaymentWebhookSealedPolymorphismTest {

    @Serializable
    public sealed interface Money {
        public val cents: Long
    }

    @Serializable
    @SerialName("eur")
    public data class Euro(override val cents: Long) : Money

    @Serializable
    @SerialName("usd")
    public data class Dollar(override val cents: Long) : Money

    @Serializable
    @JsonClassDiscriminator("event")
    public sealed class WebhookEvent {
        public abstract val id: String
    }

    @Serializable
    @SerialName("payment.captured")
    public data class PaymentCaptured(
        override val id: String,
        val account: String,
        val amount: Money,
    ) : WebhookEvent()

    @Serializable
    @SerialName("payment.refunded")
    public data class PaymentRefunded(
        override val id: String,
        val account: String,
        val amount: Money,
        val reason: String = "requested_by_customer",
    ) : WebhookEvent()

    @Serializable
    @SerialName("account.frozen")
    public data class AccountFrozen(override val id: String, val account: String) : WebhookEvent()

    @Serializable
    @SerialName("batch")
    public data class WebhookBatch(val sequence: Int, val events: List<WebhookEvent>)

    /** Folds webhook events into per-account balances, as the payment service's ledger does. */
    public class Ledger {
        private val balances: MutableMap<String, Long> = linkedMapOf()
        private val frozen: MutableSet<String> = linkedSetOf()

        public fun apply(event: WebhookEvent) {
            when (event) {
                is PaymentCaptured -> credit(event.account, event.amount.cents)
                is PaymentRefunded -> credit(event.account, -event.amount.cents)
                is AccountFrozen -> frozen.add(event.account)
            }
        }

        private fun credit(account: String, cents: Long) {
            if (account in frozen) {
                return
            }
            balances[account] = (balances[account] ?: 0L) + cents
        }

        public fun balanceOf(account: String): Long = balances[account] ?: 0L

        public fun isFrozen(account: String): Boolean = account in frozen
    }

    private val webhookJson: Json = Json {
        ignoreUnknownKeys = true
    }

    private val auditJson: Json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun foldsWebhookBatchIntoBalances() {
        val payload: String = """
            {
              "sequence": 42,
              "provider": "acme-pay",
              "events": [
                {"event": "payment.captured", "id": "ev_1", "account": "acc_a",
                 "amount": {"type": "eur", "cents": 12500}},
                {"event": "payment.captured", "id": "ev_2", "account": "acc_b",
                 "amount": {"type": "usd", "cents": 4000}, "livemode": true},
                {"event": "payment.refunded", "id": "ev_3", "account": "acc_a",
                 "amount": {"type": "eur", "cents": 2500}},
                {"event": "account.frozen", "id": "ev_4", "account": "acc_b"},
                {"event": "payment.captured", "id": "ev_5", "account": "acc_b",
                 "amount": {"type": "usd", "cents": 999}}
              ]
            }
        """.trimIndent()

        val batch: WebhookBatch = webhookJson.decodeFromString<WebhookBatch>(payload)
        val ledger: Ledger = Ledger()
        batch.events.forEach(ledger::apply)

        assertEquals(42, batch.sequence)
        assertEquals(listOf("ev_1", "ev_2", "ev_3", "ev_4", "ev_5"), batch.events.map { it.id })
        assertEquals(PaymentRefunded("ev_3", "acc_a", Euro(2500)), batch.events[2])
        assertEquals(10000L, ledger.balanceOf("acc_a"))
        assertEquals(4000L, ledger.balanceOf("acc_b"))
        assertTrue(ledger.isFrozen("acc_b"))
        assertFalse(ledger.isFrozen("acc_a"))
    }

    @Test
    fun writesAuditLogInWireShape() {
        val events: List<WebhookEvent> = listOf(
            PaymentCaptured("ev_10", "acc_c", Dollar(1500)),
            PaymentRefunded("ev_11", "acc_c", Dollar(500)),
            AccountFrozen("ev_12", "acc_c"),
        )

        val auditLine: String = auditJson.encodeToString<List<WebhookEvent>>(events)

        assertEquals(
            """[{"event":"payment.captured","id":"ev_10","account":"acc_c",""" +
                """"amount":{"type":"usd","cents":1500}},""" +
                """{"event":"payment.refunded","id":"ev_11","account":"acc_c",""" +
                """"amount":{"type":"usd","cents":500},"reason":"requested_by_customer"},""" +
                """{"event":"account.frozen","id":"ev_12","account":"acc_c"}]""",
            auditLine,
        )

        val auditTree: JsonArray = auditJson.parseToJsonElement(auditLine) as JsonArray
        val firstEntry: JsonObject = auditTree[0].jsonObject
        assertEquals("payment.captured", firstEntry["event"]!!.jsonPrimitive.content)

        val replayed: List<WebhookEvent> = auditJson.decodeFromString<List<WebhookEvent>>(auditLine)
        assertEquals(events, replayed)
    }

    @Test
    fun rejectsUnknownEventType() {
        val payload: String = """
            {"sequence": 7, "events": [
              {"event": "payment.disputed", "id": "ev_20", "account": "acc_d"}
            ]}
        """.trimIndent()

        val failure: SerializationException = assertThrows(SerializationException::class.java) {
            webhookJson.decodeFromString<WebhookBatch>(payload)
        }

        assertTrue(failure.message!!.contains("payment.disputed"), failure.message)
    }
}
