/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin value-class scalar binding for a payment gateway's domain identifiers and amounts.
 * The gateway reads a flat JSON payment into strongly typed fields and forwards it without
 * leaking the Kotlin wrappers into the public wire format.
 */
public class ValueClassPaymentTest {

    @JvmInline
    public value class AccountId(val value: String)

    @JvmInline
    public value class AmountCents(val value: Long)

    public data class PaymentInstruction(
        val reference: String,
        val debitAccount: AccountId,
        val creditAccount: AccountId,
        val amount: AmountCents,
    )

    @Test
    @Timeout(60)
    public fun forwardsStronglyTypedPaymentAsScalarJson(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val incomingJson: String = """
            {
              "reference": "invoice-1042",
              "debitAccount": "customer-0017",
              "creditAccount": "merchant-0083",
              "amount": 125050
            }
        """.trimIndent()

        val payment: PaymentInstruction = mapper.readValue(incomingJson)

        assertEquals("invoice-1042", payment.reference)
        assertEquals(AccountId("customer-0017"), payment.debitAccount)
        assertEquals(AccountId("merchant-0083"), payment.creditAccount)
        assertEquals(AmountCents(125_050L), payment.amount)
        assertEquals(125_050L, payment.amount.value)

        val forwardedJson: String = mapper.writeValueAsString(payment)
        val forwarded: JsonNode = mapper.readTree(forwardedJson)
        assertTrue(forwarded["debitAccount"].isTextual)
        assertTrue(forwarded["creditAccount"].isTextual)
        assertTrue(forwarded["amount"].isIntegralNumber)
        assertEquals("customer-0017", forwarded["debitAccount"].asText())
        assertEquals("merchant-0083", forwarded["creditAccount"].asText())
        assertEquals(125_050L, forwarded["amount"].asLong())
        assertEquals(mapper.readTree(incomingJson), forwarded)

        val received: PaymentInstruction = mapper.readValue(forwardedJson)
        assertEquals(payment, received)
    }
}
