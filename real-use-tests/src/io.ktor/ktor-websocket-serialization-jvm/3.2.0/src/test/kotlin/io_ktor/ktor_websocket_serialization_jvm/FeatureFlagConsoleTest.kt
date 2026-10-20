/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Schemaless JSON trees and top-level collection and primitive payloads over WebSockets,
 * where `JsonObject`, `List`, `Map`, `Long` and nullable `String` values travel through
 * `sendSerialized` and `receiveDeserialized` with the kotlinx JSON WebSocket converter.
 *
 * A feature-flag admin console patches flags with free-form JSON objects, in which a JSON
 * `null` deletes a flag, asks which of a list of flags are on, reads the store revision as
 * a bare number, and signs off by sending a JSON `null`.
 */
public class FeatureFlagConsoleTest {
    @Test
    fun `operator patches flags and reads them back as typed collections`(): Unit = flagConsole { client: HttpClient ->
        val patch: JsonObject = buildJsonObject {
            put("darkMode", true)
            put("checkout", buildJsonObject {
                put("variant", "B")
                put("rollout", 25)
            })
            put("beta", JsonNull)
        }
        var snapshot: JsonObject? = null
        var enabled: Map<String, Boolean?>? = null
        var revision: Long? = null
        var observedClose: CloseReason? = null

        client.webSocket("/console") {
            sendSerialized(patch)
            snapshot = receiveDeserialized<JsonObject>()
            sendSerialized(listOf("darkMode", "checkout", "beta", "legacyCart"))
            enabled = receiveDeserialized<Map<String, Boolean?>>()
            revision = receiveDeserialized<Long>()
            sendSerialized<String?>(null)
            observedClose = closeReason.await()
        }

        val flags: JsonObject = snapshot!!
        assertEquals(setOf("darkMode", "checkout", "legacyCart"), flags.keys)
        assertEquals(true, flags.getValue("darkMode").jsonPrimitive.booleanOrNull)
        assertEquals("B", flags.getValue("checkout").jsonObject.getValue("variant").jsonPrimitive.content)
        assertEquals(25, flags.getValue("checkout").jsonObject.getValue("rollout").jsonPrimitive.int)
        assertEquals(
            mapOf("darkMode" to true, "checkout" to true, "beta" to null, "legacyCart" to false),
            enabled,
        )
        assertEquals(1L, revision)
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "signed off at revision 1"), observedClose)
    }

    @Test
    fun `browser console sees bare JSON values on the wire`(): Unit = flagConsole { client: HttpClient ->
        var snapshotText: String? = null
        var enabledText: String? = null
        var revisionText: String? = null

        client.webSocket("/console") {
            send(Frame.Text("""{"legacyCart":true}"""))
            snapshotText = assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()
            send(Frame.Text("""["legacyCart","beta"]"""))
            enabledText = assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()
            revisionText = assertInstanceOf(Frame.Text::class.java, incoming.receive()).readText()
            send(Frame.Text("null"))
            closeReason.await()
        }

        assertEquals("""{"beta":true,"legacyCart":true}""", snapshotText)
        assertEquals("""{"legacyCart":true,"beta":true}""", enabledText)
        assertEquals("1", revisionText)
    }

    /** Runs [scenario] against the flag console application with a kotlinx JSON WebSocket client. */
    private fun flagConsole(scenario: suspend (HttpClient) -> Unit): Unit = testApplication {
        application {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(FLAG_JSON)
            }
            routing {
                webSocket("/console") {
                    val flags: MutableMap<String, JsonElement> = SEED_FLAGS.toMutableMap()
                    var revision: Long = 0
                    val patch: JsonObject = receiveDeserialized<JsonObject>()
                    for ((name: String, value: JsonElement) in patch) {
                        if (value is JsonNull) {
                            flags.remove(name)
                        } else {
                            flags[name] = value
                        }
                    }
                    revision++
                    sendSerialized(JsonObject(flags))
                    val names: List<String> = receiveDeserialized<List<String>>()
                    val enabled: Map<String, Boolean?> = names.associateWith { name: String ->
                        flags[name]?.let(::isOn)
                    }
                    sendSerialized(enabled)
                    sendSerialized(revision)
                    val signOff: String? = receiveDeserialized<String?>()
                    close(CloseReason(CloseReason.Codes.NORMAL, signOff ?: "signed off at revision $revision"))
                }
            }
        }
        withTimeout(TIMEOUT_MILLIS) {
            scenario(flagClient())
        }
    }

    private fun ApplicationTestBuilder.flagClient(): HttpClient = createClient {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(FLAG_JSON)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS: Long = 30_000

        val FLAG_JSON: Json = Json

        val SEED_FLAGS: Map<String, JsonElement> = linkedMapOf(
            "beta" to JsonPrimitive(true),
            "legacyCart" to JsonPrimitive(false),
        )

        /** A boolean flag is on when true; a rollout flag is on when its rollout is above zero. */
        fun isOn(value: JsonElement): Boolean = when (value) {
            is JsonObject -> (value["rollout"]?.jsonPrimitive?.int ?: 0) > 0
            else -> value.jsonPrimitive.booleanOrNull ?: false
        }
    }
}
