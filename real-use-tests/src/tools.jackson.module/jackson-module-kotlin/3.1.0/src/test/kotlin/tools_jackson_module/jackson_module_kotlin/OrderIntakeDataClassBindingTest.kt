/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.module.kotlin.KotlinInvalidNullException
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Data-class binding through primary constructors, with Kotlin default arguments and
 * null-safety, as an order-intake endpoint parses and echoes request bodies.
 */
public class OrderIntakeDataClassBindingTest {

    enum class Priority { STANDARD, EXPRESS }

    data class Address(
        val street: String,
        val city: String,
        val postalCode: String,
        val country: String = "NL",
    )

    data class OrderLine(val sku: String, val quantity: Int = 1, val unitPriceCents: Long)

    data class OrderRequest(
        val customerId: String,
        val lines: List<OrderLine>,
        val shipTo: Address,
        val priority: Priority = Priority.STANDARD,
        val giftMessage: String? = null,
        val tags: Set<String> = emptySet(),
        val attributes: Map<String, String> = emptyMap(),
    ) {
        val totalCents: Long
            get() = lines.sumOf { it.quantity * it.unitPriceCents }
    }

    private val mapper = jacksonObjectMapper()

    @Test
    fun fillsOmittedFieldsFromConstructorDefaults(): Unit {
        val body = """
            {
              "customerId": "c-1001",
              "lines": [
                {"sku": "BOOK-42", "unitPriceCents": 1999},
                {"sku": "PEN-7", "quantity": 3, "unitPriceCents": 250}
              ],
              "shipTo": {"street": "Keizersgracht 1", "city": "Amsterdam", "postalCode": "1015CJ"}
            }
        """.trimIndent()

        val order: OrderRequest = mapper.readValue(body)

        assertThat(order.customerId).isEqualTo("c-1001")
        assertThat(order.lines).containsExactly(
            OrderLine(sku = "BOOK-42", quantity = 1, unitPriceCents = 1999),
            OrderLine(sku = "PEN-7", quantity = 3, unitPriceCents = 250),
        )
        assertThat(order.shipTo.country).isEqualTo("NL")
        assertThat(order.priority).isEqualTo(Priority.STANDARD)
        assertThat(order.giftMessage).isNull()
        assertThat(order.tags).isEmpty()
        assertThat(order.attributes).isEmpty()
        assertThat(order.totalCents).isEqualTo(2749L)
    }

    @Test
    fun roundTripsAFullyPopulatedOrder(): Unit {
        val order = OrderRequest(
            customerId = "c-2002",
            lines = listOf(OrderLine(sku = "LAMP-3", quantity = 2, unitPriceCents = 4500)),
            shipTo = Address(street = "Rue de Rivoli 10", city = "Paris", postalCode = "75001", country = "FR"),
            priority = Priority.EXPRESS,
            giftMessage = "Happy birthday!",
            tags = setOf("gift", "fragile"),
            attributes = mapOf("channel" to "mobile"),
        )

        val json = mapper.writeValueAsString(order)
        val echoed: OrderRequest = mapper.readValue(json)

        assertThat(echoed).isEqualTo(order)
        assertThat(mapper.readTree(json).get("priority").asString()).isEqualTo("EXPRESS")
        assertThat(echoed.totalCents).isEqualTo(9000L)
    }

    @Test
    fun bindsAListOfOrdersThroughReifiedTypeInference(): Unit {
        val batch = """
            [
              {"customerId": "c-1", "lines": [], "shipTo": {"street": "A 1", "city": "Utrecht", "postalCode": "3511"}},
              {"customerId": "c-2", "lines": [], "shipTo": {"street": "B 2", "city": "Berlin", "postalCode": "10115", "country": "DE"},
               "priority": "EXPRESS", "giftMessage": "Thanks"}
            ]
        """.trimIndent()

        val orders: List<OrderRequest> = mapper.readValue(batch)

        assertThat(orders.map { it.customerId }).containsExactly("c-1", "c-2")
        assertThat(orders.map { it.shipTo.country }).containsExactly("NL", "DE")
        assertThat(orders[1].priority).isEqualTo(Priority.EXPRESS)
        assertThat(orders[1].giftMessage).isEqualTo("Thanks")
    }

    @Test
    fun rejectsAnExplicitNullForANonNullField(): Unit {
        val body = """
            {"customerId": null, "lines": [], "shipTo": {"street": "A 1", "city": "Utrecht", "postalCode": "3511"}}
        """.trimIndent()

        assertThatThrownBy { mapper.readValue<OrderRequest>(body) }
            .isInstanceOf(KotlinInvalidNullException::class.java)
            .hasMessageContaining("customerId")
    }

    @Test
    fun rejectsAMissingRequiredField(): Unit {
        val body = """
            {"customerId": "c-3", "lines": []}
        """.trimIndent()

        assertThatThrownBy { mapper.readValue<OrderRequest>(body) }
            .isInstanceOf(MismatchedInputException::class.java)
            .hasMessageContaining("shipTo")
    }
}
