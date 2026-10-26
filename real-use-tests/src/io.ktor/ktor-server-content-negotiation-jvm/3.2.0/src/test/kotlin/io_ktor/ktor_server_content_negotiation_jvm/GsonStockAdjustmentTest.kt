/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import com.google.gson.FieldNamingPolicy
import com.google.gson.annotations.SerializedName
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
import io.ktor.serialization.gson.gson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Gson-backed content negotiation with legacy field aliases for a stock-adjustment import API.
 */
@Timeout(60)
class GsonStockAdjustmentTest {
    @Test
    fun importsLegacyAndCurrentFieldNamesAndReturnsCanonicalSnakeCaseJson(): Unit = testApplication {
        application {
            install(ContentNegotiation) {
                gson {
                    setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    serializeNulls()
                }
            }
            routing {
                post("/stock/preview") {
                    val batch: AdjustmentBatch = call.receive<AdjustmentBatch>()
                    val quantities: Map<String, Int> = batch.adjustments.groupBy { it.sku }
                        .mapValues { (_, adjustments) -> adjustments.sumOf { it.deltaUnits } }
                    call.respond(
                        AdjustmentPreview(batch.warehouseId, batch.adjustments.size, quantities, batch.adjustments)
                    )
                }
            }
        }

        val warehouseClient: HttpClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        // Older scanners send product_code; current scanners send the canonical sku field.
        val response: HttpResponse = warehouseClient.post("/stock/preview") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                """
                {
                  "warehouse_id":"west",
                  "adjustments":[
                    {"product_code":"PAD-A4","delta_units":12,"operator_note":"Delivery"},
                    {"sku":"PAD-A4","delta_units":-2,"operator_note":null},
                    {"product_code":"PEN-BLUE","delta_units":5,"operator_note":"Count correction"}
                  ]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        // Compare the actual wire document independently of Gson, including canonical aliases and nulls.
        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "warehouse_id":"west",
                  "applied_count":3,
                  "quantities":{"PAD-A4":10,"PEN-BLUE":5},
                  "adjustments":[
                    {"sku":"PAD-A4","delta_units":12,"operator_note":"Delivery"},
                    {"sku":"PAD-A4","delta_units":-2,"operator_note":null},
                    {"sku":"PEN-BLUE","delta_units":5,"operator_note":"Count correction"}
                  ]
                }
                """.trimIndent()
            ),
            Json.parseToJsonElement(response.bodyAsText())
        )
    }

    data class AdjustmentBatch(val warehouseId: String, val adjustments: List<Adjustment>)

    data class Adjustment(
        @SerializedName(value = "sku", alternate = ["product_code"]) val sku: String,
        val deltaUnits: Int,
        val operatorNote: String?
    )

    data class AdjustmentPreview(
        val warehouseId: String,
        val appliedCount: Int,
        val quantities: Map<String, Int>,
        val adjustments: List<Adjustment>
    )
}
