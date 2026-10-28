/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises route-scoped content negotiation policies for compact mobile and complete audit responses.
 */
@Timeout(60)
class RouteScopedJsonPolicyTest {
    @Test
    fun keepsMobileAndAuditSerializationPoliciesIsolated(): Unit = testApplication {
        val shipment: Shipment = Shipment("shipment-42", "in-transit")
        application {
            routing {
                route("/mobile") {
                    install(ContentNegotiation) {
                        json(Json {
                            encodeDefaults = false
                            explicitNulls = false
                        })
                    }
                    get("/shipment") {
                        call.respond(shipment)
                    }
                }
                route("/audit") {
                    install(ContentNegotiation) {
                        json(Json {
                            encodeDefaults = true
                            explicitNulls = true
                        })
                    }
                    get("/shipment") {
                        call.respond(shipment)
                    }
                }
            }
        }

        val consumerClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }

        // Mobile clients omit unchanged defaults and absent fields to reduce payload size.
        val mobileResponse = consumerClient.get("/mobile/shipment") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, mobileResponse.status)
        assertEquals(ContentType.Application.Json, mobileResponse.contentType()?.withoutParameters())
        val mobileDocument = Json.parseToJsonElement(mobileResponse.bodyAsText()).jsonObject
        assertEquals(setOf("id", "status"), mobileDocument.keys)
        assertEquals("shipment-42", mobileDocument.getValue("id").jsonPrimitive.content)
        assertEquals("in-transit", mobileDocument.getValue("status").jsonPrimitive.content)

        // Auditors need every field, including explicit absence and the default schema version.
        val auditResponse = consumerClient.get("/audit/shipment") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, auditResponse.status)
        assertEquals(ContentType.Application.Json, auditResponse.contentType()?.withoutParameters())
        val auditDocument = Json.parseToJsonElement(auditResponse.bodyAsText()).jsonObject
        assertEquals(setOf("id", "status", "deliveredTo", "schemaVersion"), auditDocument.keys)
        assertEquals("shipment-42", auditDocument.getValue("id").jsonPrimitive.content)
        assertEquals("in-transit", auditDocument.getValue("status").jsonPrimitive.content)
        assertEquals(JsonNull, auditDocument.getValue("deliveredTo"))
        assertEquals("1", auditDocument.getValue("schemaVersion").jsonPrimitive.content)

        // Serving the audit route must not change the mobile route's converter configuration.
        val nextMobileResponse = consumerClient.get("/mobile/shipment") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, nextMobileResponse.status)
        assertEquals(mobileDocument, Json.parseToJsonElement(nextMobileResponse.bodyAsText()).jsonObject)
    }

    @Serializable
    data class Shipment(
        val id: String,
        val status: String,
        val deliveredTo: String? = null,
        val schemaVersion: Int = 1
    )
}
