/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin primary-constructor binding and default arguments for order intake.
 * An order endpoint accepts immutable request models, applies omitted checkout defaults,
 * and writes the accepted order as JSON for a downstream fulfillment service.
 */
public class OrderConstructorDefaultsTest {

    public data class OrderLine(
        val sku: String,
        val unitPriceCents: Long,
        val quantity: Int = 1,
    )

    public data class OrderRequest(
        val customerId: String,
        val items: List<OrderLine>,
        val delivery: String = "standard",
    ) {
        public fun totalCents(): Long = items.sumOf { line: OrderLine ->
            line.unitPriceCents * line.quantity
        }
    }

    @Test
    @Timeout(60)
    public fun acceptsCheckoutDefaultsAndPreservesExplicitChoices(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val checkoutJson: String = """
            {
              "customerId": "customer-42",
              "items": [
                {"sku": "NOTEBOOK", "unitPriceCents": 750},
                {"sku": "PENCIL", "unitPriceCents": 125, "quantity": 4}
              ]
            }
        """.trimIndent()

        val accepted: OrderRequest = mapper.readValue(checkoutJson)

        assertEquals("customer-42", accepted.customerId)
        assertEquals("standard", accepted.delivery)
        assertEquals(listOf(OrderLine("NOTEBOOK", 750), OrderLine("PENCIL", 125, 4)), accepted.items)
        assertEquals(1_250L, accepted.totalCents())

        val fulfillmentJson: String = mapper.writeValueAsString(accepted)
        val fulfillmentOrder: OrderRequest = mapper.readValue(fulfillmentJson)
        assertEquals(accepted, fulfillmentOrder)
        assertEquals("standard", mapper.readTree(fulfillmentJson)["delivery"].asText())
        assertEquals(1, mapper.readTree(fulfillmentJson)["items"][0]["quantity"].asInt())

        val expressCheckout: OrderRequest = mapper.readValue(
            """
                {
                  "customerId": "customer-73",
                  "items": [{"sku": "PEN", "unitPriceCents": 300, "quantity": 2}],
                  "delivery": "express"
                }
            """.trimIndent(),
        )
        assertEquals("express", expressCheckout.delivery)
        assertEquals(listOf(OrderLine("PEN", 300, 2)), expressCheckout.items)
        assertEquals(600L, expressCheckout.totalCents())
    }
}
