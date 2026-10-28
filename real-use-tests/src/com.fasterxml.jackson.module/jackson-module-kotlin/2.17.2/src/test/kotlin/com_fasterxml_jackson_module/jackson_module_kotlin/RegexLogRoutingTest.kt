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
 * Exercises Kotlin Regex JSON deserialization for configurable application log routing.
 * A log collector loads plain and option-bearing patterns from configuration and routes
 * matching messages to operational queues, leaving unmatched messages in the general queue.
 */
public class RegexLogRoutingTest {

    public data class RoutingRule(val pattern: Regex, val destination: String)

    public data class LogRouting(val rules: List<RoutingRule>) {
        public fun destinationFor(message: String): String =
            rules.firstOrNull { rule: RoutingRule -> rule.pattern.matches(message) }
                ?.destination ?: "general"
    }

    @Test
    @Timeout(60)
    public fun loadsPatternOptionsAndRoutesOperationalMessages(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val configurationJson: String = """
            {
              "rules": [
                {
                  "pattern": {
                    "pattern": "^payment-[0-9]+: declined$",
                    "options": ["IGNORE_CASE"]
                  },
                  "destination": "billing-alerts"
                },
                {
                  "pattern": "^AUDIT: .+$",
                  "destination": "audit-archive"
                }
              ]
            }
        """.trimIndent()

        val routing: LogRouting = mapper.readValue(configurationJson)
        val messages: List<String> = listOf(
            "payment-1042: declined",
            "PAYMENT-1043: DECLINED",
            "AUDIT: customer-17 changed address",
            "audit: request received",
            "payment-1044: approved",
            "health: ready",
        )
        val queuedMessages: Map<String, List<String>> = messages.groupBy { message: String ->
            routing.destinationFor(message)
        }

        assertEquals(
            mapOf(
                "billing-alerts" to listOf(
                    "payment-1042: declined",
                    "PAYMENT-1043: DECLINED",
                ),
                "audit-archive" to listOf("AUDIT: customer-17 changed address"),
                "general" to listOf(
                    "audit: request received",
                    "payment-1044: approved",
                    "health: ready",
                ),
            ),
            queuedMessages,
        )
    }
}
