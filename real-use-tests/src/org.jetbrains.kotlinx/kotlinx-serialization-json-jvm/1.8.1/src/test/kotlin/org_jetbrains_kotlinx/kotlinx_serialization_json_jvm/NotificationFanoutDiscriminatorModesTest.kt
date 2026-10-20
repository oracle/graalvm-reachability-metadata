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
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the polymorphic wire-shape options of `Json`: the `ClassDiscriminatorMode` values
 * `POLYMORPHIC`, `ALL_JSON_OBJECTS` and `NONE`, a custom `classDiscriminator` key, and
 * `useArrayPolymorphism`, applied to one sealed hierarchy and read back by each sink.
 *
 * A notification service fans each dispatch out to sinks with different contracts: its own
 * queue keeps the default shape, the event warehouse wants every object tagged with its type,
 * a legacy mobile gateway reads `[type, payload]` pairs, and a partner webhook must never see
 * internal type names.
 */
@OptIn(ExperimentalSerializationApi::class)
public class NotificationFanoutDiscriminatorModesTest {

    @Serializable
    @SerialName("recipient")
    public data class Recipient(val userId: String, val locale: String)

    @Serializable
    public sealed interface Notification {
        public val title: String
    }

    @Serializable
    @SerialName("email")
    public data class EmailNotification(
        override val title: String,
        val address: String,
        val html: Boolean,
    ) : Notification

    @Serializable
    @SerialName("sms")
    public data class SmsNotification(override val title: String, val phone: String) : Notification

    @Serializable
    @SerialName("push")
    public data class PushNotification(
        override val title: String,
        val deviceTokens: List<String>,
        val badge: Int,
    ) : Notification

    @Serializable
    @SerialName("dispatch")
    public data class Dispatch(
        val id: String,
        val recipient: Recipient,
        val notification: Notification,
        val tags: Map<String, String>,
    )

    /** The partner's own model of the email it receives: a plain class, no hierarchy. */
    @Serializable
    public data class PartnerEmail(val title: String, val address: String, val html: Boolean)

    /** Sends one dispatch to every sink, each through the `Json` its contract requires. */
    public class Fanout(
        private val queueJson: Json,
        private val warehouseJson: Json,
        private val gatewayJson: Json,
        private val partnerJson: Json,
    ) {
        public val queue: MutableList<String> = mutableListOf()
        public val warehouse: MutableList<String> = mutableListOf()
        public val gateway: MutableList<String> = mutableListOf()
        public val partner: MutableList<String> = mutableListOf()

        public fun publish(dispatch: Dispatch) {
            queue.add(queueJson.encodeToString<Dispatch>(dispatch))
            warehouse.add(warehouseJson.encodeToString<Dispatch>(dispatch))
            gateway.add(gatewayJson.encodeToString<Notification>(dispatch.notification))
            partner.add(partnerJson.encodeToString<Notification>(dispatch.notification))
        }
    }

    private val queueJson: Json = Json

    private val warehouseJson: Json = Json {
        classDiscriminator = "@type"
        classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
    }

    private val gatewayJson: Json = Json {
        useArrayPolymorphism = true
    }

    private val partnerJson: Json = Json {
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
    }

    private val dispatches: List<Dispatch> = listOf(
        Dispatch(
            id = "d-1",
            recipient = Recipient("u-17", "de-DE"),
            notification = EmailNotification("Your order shipped", "anna@example.com", html = true),
            tags = mapOf("campaign" to "shipping"),
        ),
        Dispatch(
            id = "d-2",
            recipient = Recipient("u-42", "en-US"),
            notification = SmsNotification("Code 731904", "+15550100"),
            tags = emptyMap(),
        ),
        Dispatch(
            id = "d-3",
            recipient = Recipient("u-42", "en-US"),
            notification = PushNotification("Back in stock", listOf("tok-a", "tok-b"), badge = 3),
            tags = mapOf("campaign" to "restock", "priority" to "low"),
        ),
    )

    private fun publishAll(): Fanout {
        val fanout: Fanout = Fanout(queueJson, warehouseJson, gatewayJson, partnerJson)
        dispatches.forEach(fanout::publish)
        return fanout
    }

    @Test
    fun queueTagsOnlyThePolymorphicNotification() {
        val fanout: Fanout = publishAll()

        assertEquals(
            """{"id":"d-2","recipient":{"userId":"u-42","locale":"en-US"},""" +
                """"notification":{"type":"sms","title":"Code 731904","phone":"+15550100"},"tags":{}}""",
            fanout.queue[1],
        )
        val replayed: List<Dispatch> = fanout.queue.map { queueJson.decodeFromString<Dispatch>(it) }
        assertEquals(dispatches, replayed)
    }

    @Test
    fun warehouseTagsEveryObjectButNotMaps() {
        val fanout: Fanout = publishAll()

        assertEquals(
            """{"@type":"dispatch","id":"d-1",""" +
                """"recipient":{"@type":"recipient","userId":"u-17","locale":"de-DE"},""" +
                """"notification":{"@type":"email","title":"Your order shipped",""" +
                """"address":"anna@example.com","html":true},""" +
                """"tags":{"campaign":"shipping"}}""",
            fanout.warehouse[0],
        )

        val row: JsonObject = warehouseJson.parseToJsonElement(fanout.warehouse[2]).jsonObject
        assertEquals("dispatch", row["@type"]!!.jsonPrimitive.content)
        assertEquals("push", row["notification"]!!.jsonObject["@type"]!!.jsonPrimitive.content)
        assertFalse("@type" in row["tags"]!!.jsonObject)

        // Tags on non-polymorphic objects are unknown keys to a strict reader.
        assertThrows(SerializationException::class.java) {
            warehouseJson.decodeFromString<Dispatch>(fanout.warehouse[0])
        }
        val replayJson: Json = Json(warehouseJson) { ignoreUnknownKeys = true }
        val restored: List<Dispatch> = fanout.warehouse.map { replayJson.decodeFromString<Dispatch>(it) }
        assertEquals(dispatches, restored)
    }

    @Test
    fun gatewayReadsTypePayloadPairs() {
        val fanout: Fanout = publishAll()

        assertEquals(
            """["push",{"title":"Back in stock","deviceTokens":["tok-a","tok-b"],"badge":3}]""",
            fanout.gateway[2],
        )
        val routed: List<String> = fanout.gateway.map {
            gatewayJson.parseToJsonElement(it).jsonArray[0].jsonPrimitive.content
        }
        assertEquals(listOf("email", "sms", "push"), routed)

        val decoded: List<Notification> = fanout.gateway.map { gatewayJson.decodeFromString<Notification>(it) }
        assertEquals(dispatches.map { it.notification }, decoded)
        val pushed: PushNotification = decoded[2] as PushNotification
        assertEquals(3, pushed.badge)
    }

    @Test
    fun partnerSeesNoInternalTypeNames() {
        val fanout: Fanout = publishAll()

        assertEquals(
            """{"title":"Your order shipped","address":"anna@example.com","html":true}""",
            fanout.partner[0],
        )
        fanout.partner.forEach { body: String ->
            val tree: JsonObject = partnerJson.parseToJsonElement(body).jsonObject
            assertFalse("type" in tree, body)
            assertTrue("title" in tree, body)
        }

        val received: PartnerEmail = Json.decodeFromString<PartnerEmail>(fanout.partner[0])
        assertEquals(PartnerEmail("Your order shipped", "anna@example.com", html = true), received)

        assertThrows(SerializationException::class.java) {
            partnerJson.decodeFromString<Notification>(fanout.partner[1])
        }
    }

    @Test
    fun sinksDisagreeOnTheShapeOfTheSameNotification() {
        val notification: Notification = dispatches[1].notification

        val shapes: List<String> = listOf(queueJson, warehouseJson, gatewayJson, partnerJson)
            .map { json: Json -> json.encodeToString<Notification>(notification) }

        assertEquals(
            listOf(
                """{"type":"sms","title":"Code 731904","phone":"+15550100"}""",
                """{"@type":"sms","title":"Code 731904","phone":"+15550100"}""",
                """["sms",{"title":"Code 731904","phone":"+15550100"}]""",
                """{"title":"Code 731904","phone":"+15550100"}""",
            ),
            shapes,
        )
        val asArray: JsonArray = gatewayJson.parseToJsonElement(shapes[2]).jsonArray
        assertEquals(2, asArray.size)
    }
}
