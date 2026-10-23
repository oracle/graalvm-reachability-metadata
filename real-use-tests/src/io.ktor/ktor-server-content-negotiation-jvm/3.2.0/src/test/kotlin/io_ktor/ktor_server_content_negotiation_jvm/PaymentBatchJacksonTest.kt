/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Jackson-backed content negotiation for typed payment batches with exact decimal amounts.
 */
@Timeout(60)
class PaymentBatchJacksonTest {
    @Test
    fun previewsTypedPaymentListsUsingTheConfiguredWireNamingAndDecimalTotals(): Unit = testApplication {
        application {
            install(ContentNegotiation) {
                jackson {
                    propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                    setSerializationInclusion(JsonInclude.Include.NON_NULL)
                }
            }
            routing {
                post("/payments/preview") {
                    val payments: List<Payment> = call.receive<List<Payment>>()
                    val total: BigDecimal = payments.fold(BigDecimal.ZERO) { sum, payment -> sum + payment.amount }
                    val fee: BigDecimal = BigDecimal("0.10").multiply(BigDecimal(payments.size))
                    call.respond(BatchPreview(total, fee, total + fee, payments))
                }
            }
        }

        val treasuryClient: HttpClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        val response: HttpResponse = treasuryClient.post("/payments/preview") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                [
                  {"reference":"PAY-41","payer_name":"Ada Supplies","amount":19.99},
                  {"reference":"PAY-42","payer_name":"West Office","amount":0.10,"memo":"Top-up"}
                ]
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        // Assert the wire document, not a round-trip through the same Jackson mapper.
        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "total_amount":20.09,
                  "service_fee":0.20,
                  "debit_amount":20.29,
                  "payments":[
                    {"reference":"PAY-41","payer_name":"Ada Supplies","amount":19.99,"currency":"EUR"},
                    {"reference":"PAY-42","payer_name":"West Office","amount":0.10,"currency":"EUR","memo":"Top-up"}
                  ]
                }
                """.trimIndent()
            ),
            Json.parseToJsonElement(response.bodyAsText())
        )

        // An empty preview remains a typed JSON response with no charges or previous batch entries.
        val emptyResponse: HttpResponse = treasuryClient.post("/payments/preview") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody("[]")
        }
        assertEquals(HttpStatusCode.OK, emptyResponse.status)
        assertEquals(ContentType.Application.Json, emptyResponse.contentType()?.withoutParameters())
        assertEquals(
            Json.parseToJsonElement(
                """{"total_amount":0,"service_fee":0.00,"debit_amount":0.00,"payments":[]}"""
            ),
            Json.parseToJsonElement(emptyResponse.bodyAsText())
        )
    }

    data class Payment(
        val reference: String,
        val payerName: String,
        val amount: BigDecimal,
        val currency: String = "EUR",
        val memo: String? = null
    )

    data class BatchPreview(
        val totalAmount: BigDecimal,
        val serviceFee: BigDecimal,
        val debitAmount: BigDecimal,
        val payments: List<Payment>
    )
}
