/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JSON request and response conversion for an order-submission endpoint.
 */
@Timeout(60)
class JsonOrderSubmissionTest {
    @Test
    fun submitsOrderAndReceivesCalculatedReceipt() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("/orders") {
                    val order: Order = call.receive<Order>()
                    val receipt = Receipt(
                        customer = order.customer,
                        itemCount = order.lines.sumOf { it.quantity },
                        totalCents = order.lines.sumOf { it.quantity * it.unitPriceCents }
                    )
                    call.respond(HttpStatusCode.Created, receipt)
                }
            }
        }

        val customerClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        val response = customerClient.post("/orders") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                  "customer": "Ada",
                  "lines": [
                    {"sku": "notebook", "quantity": 2, "unitPriceCents": 450},
                    {"sku": "pencil", "quantity": 3, "unitPriceCents": 125}
                  ]
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val receipt = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(setOf("customer", "itemCount", "totalCents"), receipt.keys)
        assertEquals("Ada", receipt.getValue("customer").jsonPrimitive.content)
        assertEquals("5", receipt.getValue("itemCount").jsonPrimitive.content)
        assertEquals("1275", receipt.getValue("totalCents").jsonPrimitive.content)
    }

    @Serializable
    data class Order(val customer: String, val lines: List<OrderLine>)

    @Serializable
    data class OrderLine(val sku: String, val quantity: Int, val unitPriceCents: Int)

    @Serializable
    data class Receipt(val customer: String, val itemCount: Int, val totalCents: Int)
}
