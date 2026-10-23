/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin sealed-class subtype discovery for a polymorphic notification queue.
 * A dispatcher reads channel-tagged messages without maintaining a separate subtype registry,
 * routes them by their Kotlin type, and persists the batch for another worker.
 */
public class SealedNotificationQueueTest {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "channel")
    public sealed class Notification {
        @JsonTypeName("email")
        public data class Email(
            val recipient: String,
            val subject: String,
            val body: String,
        ) : Notification()

        @JsonTypeName("sms")
        public data class Sms(
            val phoneNumber: String,
            val text: String,
        ) : Notification()
    }

    @Test
    @Timeout(60)
    public fun dispatchesAndPersistsMixedChannelNotifications(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val queueJson: String = """
            [
              {
                "channel": "email",
                "recipient": "customer@example.com",
                "subject": "Order shipped",
                "body": "Your order is on its way."
              },
              {
                "channel": "sms",
                "phoneNumber": "+15550102030",
                "text": "Delivery code: 4821"
              }
            ]
        """.trimIndent()

        val notifications: List<Notification> = mapper.readValue(queueJson)

        assertEquals(
            listOf(
                Notification.Email(
                    "customer@example.com",
                    "Order shipped",
                    "Your order is on its way.",
                ),
                Notification.Sms("+15550102030", "Delivery code: 4821"),
            ),
            notifications,
        )
        val dispatches: List<String> = notifications.map { notification: Notification ->
            when (notification) {
                is Notification.Email ->
                    "email:${notification.recipient}:${notification.subject}:${notification.body}"
                is Notification.Sms -> "sms:${notification.phoneNumber}:${notification.text}"
            }
        }
        assertEquals(
            listOf(
                "email:customer@example.com:Order shipped:Your order is on its way.",
                "sms:+15550102030:Delivery code: 4821",
            ),
            dispatches,
        )

        val persistedJson: String = mapper.writerFor(jacksonTypeRef<List<Notification>>())
            .writeValueAsString(notifications)
        val persistedTree: JsonNode = mapper.readTree(persistedJson)
        assertEquals("email", persistedTree[0]["channel"].asText())
        assertEquals("sms", persistedTree[1]["channel"].asText())
        assertEquals("Order shipped", persistedTree[0]["subject"].asText())
        assertEquals("Delivery code: 4821", persistedTree[1]["text"].asText())
        val restoredNotifications: List<Notification> = mapper.readValue(persistedJson)
        assertEquals(notifications, restoredNotifications)
    }
}
