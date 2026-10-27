/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises response media-type negotiation using weighted Accept preferences for an inventory feed.
 */
@OptIn(ExperimentalSerializationApi::class)
@Timeout(60)
class InventoryRepresentationNegotiationTest {
    @Test
    fun servesThePreferredAvailableRepresentation() = testApplication {
        val inventory = InventorySnapshot("warehouse-west", mapOf("notebook" to 12, "pencil" to 40))
        application {
            install(ContentNegotiation) {
                json()
                cbor()
            }
            routing {
                get("/inventory") {
                    call.respond(inventory)
                }
            }
        }

        val feedClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }

        // A bandwidth-conscious consumer prefers the binary representation, even though JSON is first.
        val binaryResponse = feedClient.get("/inventory") {
            header(HttpHeaders.Accept, "application/json;q=0.3, application/cbor;q=0.9")
        }
        assertEquals(HttpStatusCode.OK, binaryResponse.status)
        assertEquals(ContentType.Application.Cbor, binaryResponse.contentType()?.withoutParameters())
        assertEquals(inventory, Cbor.decodeFromByteArray<InventorySnapshot>(binaryResponse.bodyAsBytes()))

        // A browser-facing consumer prefers JSON for the same application value.
        val jsonResponse = feedClient.get("/inventory") {
            header(HttpHeaders.Accept, "application/cbor;q=0.2, application/json;q=1.0")
        }
        assertEquals(HttpStatusCode.OK, jsonResponse.status)
        assertEquals(ContentType.Application.Json, jsonResponse.contentType()?.withoutParameters())
        assertEquals(inventory, Json.decodeFromString<InventorySnapshot>(jsonResponse.bodyAsText()))

        // The endpoint cannot silently send JSON to a consumer that only accepts XML.
        val unsupportedResponse = feedClient.get("/inventory") {
            header(HttpHeaders.Accept, "application/xml")
        }
        assertEquals(HttpStatusCode.NotAcceptable, unsupportedResponse.status)
    }

    @Serializable
    data class InventorySnapshot(val location: String, val quantities: Map<String, Int>)
}
