/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO authenticated routing through a supplier invoice API protected by HTTP Basic challenges.
 */
class AuthenticatedSupplierInvoicesTest {
    @Test
    @Timeout(60)
    fun `challenges unknown callers and returns only the authenticated supplier invoice`(): Unit = runBlocking {
        val accounts: Map<String, SupplierAccount> = mapOf(
            "north" to SupplierAccount("north-test-secret", "invoice:N-104:USD:1275"),
            "south" to SupplierAccount("south-test-secret", "invoice:S-208:USD:4200"),
        )
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(Authentication) {
                basic("supplier-access") {
                    realm = "supplier-invoices"
                    validate { credentials: UserPasswordCredential ->
                        if (accounts[credentials.name]?.password == credentials.password) {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }
            }
            routing {
                authenticate("supplier-access") {
                    get("/supplier/invoice") {
                        val supplier: String = checkNotNull(call.principal<UserIdPrincipal>()).name
                        call.respondText(accounts.getValue(supplier).invoice, ContentType.Text.Plain)
                    }
                }
            }
        }
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                // Plain HTTP is confined to loopback; a deployed Basic endpoint requires TLS.
                HttpClient(ClientCIO) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        requestTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val url: String = "http://127.0.0.1:$port/supplier/invoice"
                    assertChallenged(client.get(url))
                    assertChallenged(client.get(url) { basicAuth("north", "wrong-secret") })
                    assertChallenged(client.get(url) { basicAuth("unknown", "north-test-secret") })

                    val north: HttpResponse = client.get(url) { basicAuth("north", "north-test-secret") }
                    assertEquals(HttpStatusCode.OK, north.status)
                    assertEquals("invoice:N-104:USD:1275", north.bodyAsText())

                    val south: HttpResponse = client.get(url) { basicAuth("south", "south-test-secret") }
                    assertEquals(HttpStatusCode.OK, south.status)
                    assertEquals("invoice:S-208:USD:4200", south.bodyAsText())

                    // Authentication belongs to each request, not to a previously authenticated connection.
                    assertChallenged(client.get(url))
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }

    private suspend fun assertChallenged(response: HttpResponse): Unit {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val challenge: HttpAuthHeader.Parameterized = parseAuthorizationHeader(
            checkNotNull(response.headers[HttpHeaders.WWWAuthenticate]),
        ) as HttpAuthHeader.Parameterized
        assertEquals("Basic", challenge.authScheme)
        assertEquals("supplier-invoices", challenge.parameter("realm"))
        assertFalse(response.bodyAsText().contains("invoice:"), "A rejected caller must not receive an invoice")
    }

    private data class SupplierAccount(val password: String, val invoice: String)
}
