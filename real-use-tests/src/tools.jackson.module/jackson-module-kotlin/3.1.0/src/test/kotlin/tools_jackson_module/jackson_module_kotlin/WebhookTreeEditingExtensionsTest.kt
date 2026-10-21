/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.ObjectReader
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.contains
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.jacksonTypeRef
import tools.jackson.module.kotlin.minusAssign
import tools.jackson.module.kotlin.plusAssign
import tools.jackson.module.kotlin.treeToValue
import java.math.BigDecimal

/**
 * The Kotlin extension API over the JSON tree model (`+=`/`-=` and `in` operators on
 * object and array nodes, reified `treeToValue`, `convertValue` and `jacksonTypeRef`), as a
 * webhook relay scrubs, enriches and re-binds incoming payment events.
 */
public class WebhookTreeEditingExtensionsTest {

    data class Customer(val id: String, val email: String? = null)

    data class PaymentEvent(
        val eventId: String,
        val type: String,
        val amount: BigDecimal,
        val customer: Customer,
        val tags: List<String> = emptyList(),
        val attempts: List<Int> = emptyList(),
    )

    private val mapper: ObjectMapper = jacksonObjectMapper()

    private val incoming = """
        {
          "eventId": "evt-901",
          "type": "payment.captured",
          "amount": 129.95,
          "customer": {"id": "cus-17", "email": "dana@example.com"},
          "cardNumber": "4111111111111111",
          "cvc": "123",
          "internalTrace": "trace-abc",
          "tags": ["eu", "card"],
          "attempts": [1, 2, 3]
        }
    """.trimIndent()

    /** Removes the fields the relay must never forward and stamps routing tags on the event. */
    private fun scrub(event: ObjectNode): Unit {
        event -= "cardNumber"
        event -= listOf("cvc", "internalTrace")
        (event.get("customer") as ObjectNode) -= "email"
        val tags = event.get("tags") as ArrayNode
        tags += "relayed"
        tags += mapper.createArrayNode().add("audited").add("v2")
        val attempts = event.get("attempts") as ArrayNode
        attempts -= 0
        attempts += 4
    }

    @Test
    fun scrubsSensitiveFieldsWithTreeOperators(): Unit {
        val event = mapper.readTree(incoming) as ObjectNode

        scrub(event)

        assertThat("cardNumber" in event).isFalse()
        assertThat("cvc" in event).isFalse()
        assertThat("internalTrace" in event).isFalse()
        assertThat("eventId" in event).isTrue()
        assertThat("email" in event.get("customer")).isFalse()
        val tags: JsonNode = event.get("tags")
        assertThat(4 in tags).isTrue()
        assertThat(5 in tags).isFalse()
        assertThat(tags.values().map { it.asString() })
            .containsExactly("eu", "card", "relayed", "audited", "v2")
        assertThat(event.get("attempts").values().map { it.intValue() }).containsExactly(2, 3, 4)
    }

    @Test
    fun bindsTheScrubbedTreeToATypedEvent(): Unit {
        val event = mapper.readTree(incoming) as ObjectNode
        scrub(event)

        val payment: PaymentEvent = mapper.treeToValue(event)

        assertThat(payment.eventId).isEqualTo("evt-901")
        assertThat(payment.type).isEqualTo("payment.captured")
        assertThat(payment.amount).isEqualByComparingTo("129.95")
        assertThat(payment.customer).isEqualTo(Customer(id = "cus-17"))
        assertThat(payment.tags).containsExactly("eu", "card", "relayed", "audited", "v2")
        assertThat(payment.attempts).containsExactly(2, 3, 4)
    }

    @Test
    fun buildsAnOutboundTreeAndReadsItWithAReaderForAGenericType(): Unit {
        val batch: ArrayNode = mapper.createArrayNode()
        for (id in listOf("evt-1", "evt-2")) {
            val node: ObjectNode = mapper.createObjectNode()
            node.put("eventId", id)
            node.put("type", "payment.refunded")
            node.put("amount", BigDecimal("10.00"))
            node.putObject("customer").put("id", "cus-$id")
            batch += node
        }

        val eventsType: TypeReference<List<PaymentEvent>> = jacksonTypeRef()
        val reader: ObjectReader = mapper.readerFor(eventsType)
        val events: List<PaymentEvent> = checkNotNull(reader.treeToValue<List<PaymentEvent>>(batch))

        assertThat(events.map { it.eventId }).containsExactly("evt-1", "evt-2")
        assertThat(events.map { it.customer.id }).containsExactly("cus-evt-1", "cus-evt-2")
        assertThat(events).allSatisfy { assertThat(it.tags).isEmpty() }
    }

    @Test
    fun convertsBetweenTypedEventsAndGenericMapsForAuditLogging(): Unit {
        val payment = PaymentEvent(
            eventId = "evt-55",
            type = "payment.failed",
            amount = BigDecimal("3.50"),
            customer = Customer(id = "cus-2", email = "lee@example.com"),
            tags = listOf("retry"),
            attempts = listOf(1),
        )

        val auditRecord: Map<String, Any?> = mapper.convertValue(payment)
        @Suppress("UNCHECKED_CAST")
        val customer = auditRecord["customer"] as Map<String, Any?>

        assertThat(auditRecord.keys)
            .containsExactlyInAnyOrder("eventId", "type", "amount", "customer", "tags", "attempts")
        assertThat(customer).containsEntry("email", "lee@example.com")
        assertThat(auditRecord["tags"]).isEqualTo(listOf("retry"))

        val restored: PaymentEvent = mapper.convertValue(auditRecord)
        assertThat(restored).isEqualTo(payment)

        val customers: List<Customer> = mapper.convertValue(
            listOf(mapOf("id" to "cus-a"), mapOf("id" to "cus-b", "email" to "b@example.com")),
        )
        assertThat(customers).containsExactly(
            Customer(id = "cus-a"),
            Customer(id = "cus-b", email = "b@example.com"),
        )
    }
}
