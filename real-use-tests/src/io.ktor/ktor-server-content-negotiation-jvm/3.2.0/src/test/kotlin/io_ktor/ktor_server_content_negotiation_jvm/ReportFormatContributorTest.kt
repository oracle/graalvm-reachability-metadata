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
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentTypeWithQuality
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
 * Exercises accepted-content-type contributors for report download URLs with an explicit format parameter.
 */
@OptIn(ExperimentalSerializationApi::class)
@Timeout(60)
class ReportFormatContributorTest {
    @Test
    fun downloadFormatOverridesClientPreferencesWithoutChangingOtherRequests(): Unit = testApplication {
        val report: BillingReport = BillingReport("billing-west", listOf("INV-41", "INV-42"), 18500)
        application {
            install(ContentNegotiation) {
                json()
                cbor()
                // Shareable download URLs select a format even when a browser sends another preference.
                accept { call, acceptedTypes ->
                    when (call.request.queryParameters["format"]) {
                        "json" -> listOf(ContentTypeWithQuality(ContentType.Application.Json))
                        "cbor" -> listOf(ContentTypeWithQuality(ContentType.Application.Cbor))
                        else -> acceptedTypes
                    }
                }
            }
            routing {
                get("/reports/billing-west") {
                    call.respond(report)
                }
            }
        }

        val reportClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }

        val jsonDownload = reportClient.get("/reports/billing-west?format=json") {
            accept(ContentType.Application.Cbor)
        }
        assertEquals(HttpStatusCode.OK, jsonDownload.status)
        assertEquals(ContentType.Application.Json, jsonDownload.contentType()?.withoutParameters())
        assertEquals(report, Json.decodeFromString<BillingReport>(jsonDownload.bodyAsText()))

        val archiveDownload = reportClient.get("/reports/billing-west?format=cbor") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, archiveDownload.status)
        assertEquals(ContentType.Application.Cbor, archiveDownload.contentType()?.withoutParameters())
        assertEquals(report, Cbor.decodeFromByteArray<BillingReport>(archiveDownload.bodyAsBytes()))

        // Ordinary API requests still follow Accept after a binary download has been served.
        val dashboardResponse = reportClient.get("/reports/billing-west") {
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, dashboardResponse.status)
        assertEquals(ContentType.Application.Json, dashboardResponse.contentType()?.withoutParameters())
        assertEquals(report, Json.decodeFromString<BillingReport>(dashboardResponse.bodyAsText()))
    }

    @Serializable
    data class BillingReport(val id: String, val invoices: List<String>, val totalCents: Int)
}
