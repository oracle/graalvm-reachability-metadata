/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Converts untyped JSON tree bodies (`JsonObject`, `JsonArray`, `JsonPrimitive`, `JsonElement`
 * and `JsonNull`) through the kotlinx JSON converter that `json()` registers on server and client
 * content negotiation, including 400 for a tree of the wrong shape or malformed JSON.
 *
 * A tenant settings store keeps one schemaless JSON document per tenant: admin tools replace
 * documents, apply RFC 7396 merge patches where `null` deletes a key, read single values and
 * resolve dotted paths, all without a data class describing the document.
 */
public class TenantSettingsJsonTreeBodyTest {

    /** The wire format both sides agree on. */
    private val settingsJson: Json = Json {
        ignoreUnknownKeys = false
    }

    private val documents: MutableMap<String, JsonObject> = ConcurrentHashMap()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var admin: HttpClient
    private lateinit var raw: HttpClient
    private var port: Int = 0

    /** Applies an RFC 7396 merge patch: `null` removes a key, objects merge recursively. */
    private fun mergePatch(target: JsonObject, patch: JsonObject): JsonObject {
        val merged: MutableMap<String, JsonElement> = target.toMutableMap()
        for ((key: String, value: JsonElement) in patch) {
            val current: JsonElement? = merged[key]
            when {
                value is JsonNull -> merged.remove(key)
                value is JsonObject && current is JsonObject -> merged[key] = mergePatch(current, value)
                value is JsonObject -> merged[key] = mergePatch(JsonObject(emptyMap()), value)
                else -> merged[key] = value
            }
        }
        return JsonObject(merged)
    }

    /** Resolves a dotted path such as `mail.smtp.port`, or `JsonNull` when any segment is missing. */
    private fun resolve(document: JsonObject, path: String): JsonElement {
        var node: JsonElement = document
        for (segment: String in path.split('.')) {
            node = (node as? JsonObject)?.get(segment) ?: return JsonNull
        }
        return node
    }

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json(settingsJson)
            }
            routing {
                put("/settings/{tenant}") {
                    val tenant: String = call.parameters["tenant"]!!
                    val document: JsonObject = call.receive()
                    documents[tenant] = document
                    call.respond(HttpStatusCode.OK, document)
                }
                patch("/settings/{tenant}") {
                    val tenant: String = call.parameters["tenant"]!!
                    val patch: JsonObject = call.receive()
                    val merged: JsonObject = mergePatch(documents[tenant] ?: JsonObject(emptyMap()), patch)
                    documents[tenant] = merged
                    call.respond(merged)
                }
                get("/settings/{tenant}") {
                    val document: JsonObject? = documents[call.parameters["tenant"]!!]
                    if (document == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            buildJsonObject { put("error", "unknown_tenant") },
                        )
                    } else {
                        call.respond(document)
                    }
                }
                get("/settings/{tenant}/value/{key}") {
                    val document: JsonObject = documents[call.parameters["tenant"]!!] ?: JsonObject(emptyMap())
                    val value: JsonElement = document[call.parameters["key"]!!] ?: JsonNull
                    call.respond(value)
                }
                get("/settings/{tenant}/size") {
                    val document: JsonObject = documents[call.parameters["tenant"]!!] ?: JsonObject(emptyMap())
                    call.respond(JsonPrimitive(document.size))
                }
                get("/tenants") {
                    call.respond(JsonArray(documents.keys.sorted().map { JsonPrimitive(it) }))
                }
                post("/settings/{tenant}/lookup") {
                    val document: JsonObject = documents[call.parameters["tenant"]!!] ?: JsonObject(emptyMap())
                    val paths: JsonArray = call.receive()
                    val answer: JsonObject = buildJsonObject {
                        for (path: JsonElement in paths) {
                            val name: String = path.jsonPrimitive.content
                            put(name, resolve(document, name))
                        }
                    }
                    call.respond(answer)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        admin = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json(settingsJson)
            }
        }
        raw = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        admin.close()
        raw.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    @Test
    fun `admin replaces and merge-patches schemaless documents as JSON trees`() = runBlocking {
        val initial: JsonObject = buildJsonObject {
            put("displayName", "Acme Corp")
            put("seats", 250)
            put("trial", false)
            put("ledgerId", Json.parseToJsonElement("12345678901234567890"))
            putJsonObject("mail") {
                put("sender", "noreply@acme.example")
                putJsonObject("smtp") {
                    put("host", "smtp.acme.example")
                    put("port", 587)
                }
            }
            putJsonArray("regions") {
                add("eu-west")
                add("us-east")
            }
        }
        val stored: HttpResponse = admin.put(url("/settings/acme")) {
            contentType(ContentType.Application.Json)
            setBody(initial)
        }
        assertEquals(HttpStatusCode.OK, stored.status)
        assertTrue(stored.contentType()!!.match(ContentType.Application.Json))
        val echoed: JsonObject = stored.body()
        assertEquals(initial, echoed)
        assertEquals("12345678901234567890", echoed.getValue("ledgerId").jsonPrimitive.content)

        val patch: JsonObject = buildJsonObject {
            put("seats", 300)
            put("trial", JsonNull)
            putJsonObject("mail") {
                putJsonObject("smtp") { put("port", 2525) }
            }
            putJsonObject("billing") { put("plan", "enterprise") }
        }
        val merged: JsonObject = admin.patch(url("/settings/acme")) {
            contentType(ContentType.Application.Json)
            setBody(patch)
        }.body()
        assertEquals(300, merged.getValue("seats").jsonPrimitive.int)
        assertFalse(merged.containsKey("trial"))
        val smtp: JsonObject = merged.getValue("mail").jsonObject.getValue("smtp").jsonObject
        assertEquals("smtp.acme.example", smtp.getValue("host").jsonPrimitive.content)
        assertEquals(2525, smtp.getValue("port").jsonPrimitive.int)
        assertEquals("noreply@acme.example", merged.getValue("mail").jsonObject.getValue("sender").jsonPrimitive.content)
        assertEquals("enterprise", merged.getValue("billing").jsonObject.getValue("plan").jsonPrimitive.content)
        assertEquals(listOf("eu-west", "us-east"), merged.getValue("regions").jsonArray.map { it.jsonPrimitive.content })

        assertEquals(merged, admin.get(url("/settings/acme")).body<JsonObject>())
        assertEquals(documents.getValue("acme"), merged)

        val seats: JsonElement = admin.get(url("/settings/acme/value/seats")).body()
        assertEquals(JsonPrimitive(300), seats)
        val regions: JsonElement = admin.get(url("/settings/acme/value/regions")).body()
        assertEquals(buildJsonArray { add("eu-west"); add("us-east") }, regions)
        val absent: JsonElement = admin.get(url("/settings/acme/value/trial")).body()
        assertEquals(JsonNull, absent)
        val size: JsonPrimitive = admin.get(url("/settings/acme/size")).body()
        assertEquals(6, size.int)
    }

    @Test
    fun `admin lists tenants and resolves dotted paths with JSON array bodies`() = runBlocking {
        for ((tenant: String, sso: Boolean) in listOf("globex" to true, "initech" to false)) {
            val response: HttpResponse = admin.put(url("/settings/$tenant")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        putJsonObject("auth") {
                            put("sso", sso)
                            put("sessionMinutes", if (sso) 480 else 60)
                        }
                    },
                )
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val tenants: JsonArray = admin.get(url("/tenants")).body()
        assertEquals(listOf("globex", "initech"), tenants.map { it.jsonPrimitive.content })

        val lookup: JsonObject = admin.post(url("/settings/globex/lookup")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonArray { add("auth.sso"); add("auth.sessionMinutes"); add("auth.mfa.required") })
        }.body()
        assertTrue(lookup.getValue("auth.sso").jsonPrimitive.boolean)
        assertEquals(480, lookup.getValue("auth.sessionMinutes").jsonPrimitive.int)
        assertEquals(JsonNull, lookup.getValue("auth.mfa.required"))

        val missing: HttpResponse = admin.get(url("/settings/hooli"))
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals(buildJsonObject { put("error", "unknown_tenant") }, missing.body<JsonObject>())
    }

    @Test
    fun `hand-written clients see the raw tree and get 400 for the wrong shape`() = runBlocking {
        val handWritten: String = """{"theme": {"accent": "#0a84ff", "dark": true}, "quota": 1.5e3, "tags": []}"""
        val stored: HttpResponse = raw.put(url("/settings/umbrella")) {
            contentType(ContentType.Application.Json)
            setBody(handWritten)
        }
        assertEquals(HttpStatusCode.OK, stored.status)
        val wire: JsonObject = Json.parseToJsonElement(stored.bodyAsText()).jsonObject
        assertEquals(setOf("theme", "quota", "tags"), wire.keys)
        assertEquals(JsonArray(emptyList()), wire.getValue("tags"))
        assertEquals(wire.getValue("theme"), documents.getValue("umbrella").getValue("theme"))
        // The stored tree keeps the exponent literal; encoding it back writes the double value.
        val accepted: JsonObject = documents.getValue("umbrella")
        assertEquals("1.5e3", accepted.getValue("quota").jsonPrimitive.content)
        assertEquals("1500.0", wire.getValue("quota").jsonPrimitive.content)
        assertEquals(1500.0, wire.getValue("quota").jsonPrimitive.double)

        assertEquals("null", raw.get(url("/settings/umbrella/value/missing")).bodyAsText())
        assertEquals("3", raw.get(url("/settings/umbrella/size")).bodyAsText())
        assertEquals("""["umbrella"]""", raw.get(url("/tenants")).bodyAsText())

        val arrayInsteadOfObject: HttpResponse = raw.put(url("/settings/umbrella")) {
            contentType(ContentType.Application.Json)
            setBody("""["not", "a", "document"]""")
        }
        assertEquals(HttpStatusCode.BadRequest, arrayInsteadOfObject.status)

        val objectInsteadOfArray: HttpResponse = raw.post(url("/settings/umbrella/lookup")) {
            contentType(ContentType.Application.Json)
            setBody("""{"path": "theme.accent"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, objectInsteadOfArray.status)

        val malformed: HttpResponse = raw.patch(url("/settings/umbrella")) {
            contentType(ContentType.Application.Json)
            setBody("""{"theme": {"accent": """)
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals(accepted, documents.getValue("umbrella"))
    }
}
