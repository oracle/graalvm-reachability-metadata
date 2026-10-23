/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises bearer authentication by refreshing an expired access token while retrieving billing invoices. */
class BillingBearerRefreshTest {
    @Test
    @Timeout(60)
    fun refreshesAfterAChallengeAndReusesTheRenewedCredentials(): Unit = runBlocking {
        withTimeout(45_000) {
            val invoiceCredentials: MutableList<String?> = CopyOnWriteArrayList()
            val tokenGrants: MutableList<String?> = CopyOnWriteArrayList()
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    get("/invoices/{id}") {
                        val authorization: String? = call.request.headers[HttpHeaders.Authorization]
                        invoiceCredentials.add(authorization)
                        if (authorization != "Bearer renewed-access") {
                            call.response.headers.append(HttpHeaders.WWWAuthenticate, "Bearer realm=\"billing\"")
                            call.respondText("Access token expired", status = HttpStatusCode.Unauthorized)
                        } else {
                            call.respondText("Invoice ${call.parameters["id"]}: USD 17.00")
                        }
                    }
                    post("/oauth/token") {
                        val form: Parameters = call.receiveParameters()
                        tokenGrants.add(form["grant_type"])
                        if (call.request.headers[HttpHeaders.Authorization] != null
                            || form["grant_type"] != "refresh_token"
                            || form["refresh_token"] != "billing-refresh") {
                            call.respondText("Invalid refresh grant", status = HttpStatusCode.BadRequest)
                        } else {
                            call.respondText(
                                """{"access_token":"renewed-access","refresh_token":"rotated-refresh"}""",
                                ContentType.Application.Json
                            )
                        }
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                val baseUrl: String = "http://127.0.0.1:$port"
                HttpClient(CIO) {
                    install(ContentNegotiation) { json() }
                    install(Auth) {
                        bearer {
                            loadTokens { BearerTokens("expired-access", "billing-refresh") }
                            sendWithoutRequest { request -> request.url.encodedPath.startsWith("/invoices/") }
                            refreshTokens {
                                val refreshed: HttpResponse = client.submitForm(
                                    url = "$baseUrl/oauth/token",
                                    formParameters = Parameters.build {
                                        append("grant_type", "refresh_token")
                                        append("refresh_token", requireNotNull(oldTokens?.refreshToken))
                                    }
                                ) {
                                    // Do not authenticate the token exchange with the expired access token.
                                    markAsRefreshTokenRequest()
                                }
                                assertEquals(HttpStatusCode.OK, refreshed.status)
                                val tokens: TokenReply = refreshed.body<TokenReply>()
                                BearerTokens(tokens.accessToken, tokens.refreshToken)
                            }
                        }
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val firstInvoice: HttpResponse = client.get("$baseUrl/invoices/invoice-42")
                    assertEquals(HttpStatusCode.OK, firstInvoice.status)
                    assertEquals("Invoice invoice-42: USD 17.00", firstInvoice.bodyAsText())

                    val nextInvoice: HttpResponse = client.get("$baseUrl/invoices/invoice-43")
                    assertEquals(HttpStatusCode.OK, nextInvoice.status)
                    assertEquals("Invoice invoice-43: USD 17.00", nextInvoice.bodyAsText())
                    assertEquals(
                        listOf("Bearer expired-access", "Bearer renewed-access", "Bearer renewed-access"),
                        invoiceCredentials
                    )
                    assertEquals(listOf("refresh_token"), tokenGrants)
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }

    @Serializable
    data class TokenReply(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String
    )
}
