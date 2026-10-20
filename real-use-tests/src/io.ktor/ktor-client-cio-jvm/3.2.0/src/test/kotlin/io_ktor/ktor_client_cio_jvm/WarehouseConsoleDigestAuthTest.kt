/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.digest
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.digest
import io.ktor.server.auth.principal
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP Digest authentication over the CIO client engine (the client `Auth` plugin `digest` provider).
 *
 * A warehouse operations console talks to an inventory service that protects its endpoints with
 * MD5 Digest authentication. The console never sends the password: it waits for the service's
 * `WWW-Authenticate: Digest` challenge, answers it with a digest over the realm, nonce, method and
 * URI, and reads the protected stock levels and posts a stock adjustment. A console configured with
 * a wrong password is refused, and a console bound to a different realm does not answer the
 * challenge at all.
 */
class WarehouseConsoleDigestAuthTest {

    /** Authorization header the inventory service received for one request, or null when absent. */
    data class ReceivedRequest(val method: String, val path: String, val authorization: String?)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    private val receivedRequests: ConcurrentLinkedQueue<ReceivedRequest> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(Authentication) {
                digest("inventory") {
                    realm = REALM
                    algorithmName = "MD5"
                    digestProvider { userName: String, realm: String ->
                        OPERATORS[userName]?.let { password: String -> md5("$userName:$realm:$password") }
                    }
                    validate { credential -> UserIdPrincipal(credential.userName) }
                }
            }
            routing {
                authenticate("inventory") {
                    get("/stock/{sku}") {
                        record(call.request.local.method.value, call.request.local.uri,
                            call.request.headers[HttpHeaders.Authorization])
                        val operator: String = call.principal<UserIdPrincipal>()!!.name
                        val sku: String = call.parameters["sku"]!!
                        call.respondText("$sku on hand=${STOCK.getValue(sku)} (viewed by $operator)")
                    }
                    post("/stock/{sku}/adjustments") {
                        record(call.request.local.method.value, call.request.local.uri,
                            call.request.headers[HttpHeaders.Authorization])
                        val operator: String = call.principal<UserIdPrincipal>()!!.name
                        val delta: Int = call.receiveText().trim().toInt()
                        val sku: String = call.parameters["sku"]!!
                        call.respondText("$sku adjusted to ${STOCK.getValue(sku) + delta} by $operator")
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun consoleAnswersDigestChallengesForReadsAndWrites() {
        val client: HttpClient = consoleClient(OPERATOR, OPERATORS.getValue(OPERATOR), REALM)

        client.use {
            runBlocking {
                val stock: HttpResponse = client.get("stock/PALLET-42")
                assertEquals(HttpStatusCode.OK, stock.status)
                assertEquals("PALLET-42 on hand=128 (viewed by $OPERATOR)", stock.bodyAsText())

                val adjustment: HttpResponse = client.post("stock/CRATE-7/adjustments") {
                    contentType(ContentType.Text.Plain)
                    setBody("-5")
                }
                assertEquals(HttpStatusCode.OK, adjustment.status)
                assertEquals("CRATE-7 adjusted to 35 by $OPERATOR", adjustment.bodyAsText())
            }
        }

        val received: List<ReceivedRequest> = receivedRequests.toList()
        assertEquals(listOf("GET", "POST"), received.map { it.method })
        assertEquals(listOf("/stock/PALLET-42", "/stock/CRATE-7/adjustments"), received.map { it.path })
        received.forEach { request: ReceivedRequest ->
            val authorization: String = checkNotNull(request.authorization) { "${request.path} sent no Authorization" }
            val header: HttpAuthHeader? = parseAuthorizationHeader(authorization)
            assertTrue(header is HttpAuthHeader.Parameterized, "authorization is $authorization")
            val digest = header as HttpAuthHeader.Parameterized
            assertEquals("Digest", digest.authScheme)
            assertEquals(OPERATOR, digest.parameter("username"))
            assertEquals(REALM, digest.parameter("realm"))
            assertEquals(request.path, digest.parameter("uri"))
            assertNotNull(digest.parameter("nonce"))
            assertEquals(32, digest.parameter("response")!!.length)
            assertTrue(!authorization.contains(OPERATORS.getValue(OPERATOR)), "password leaked")
        }
    }

    @Test
    fun consoleWithWrongPasswordIsRefused() {
        val client: HttpClient = consoleClient(OPERATOR, "not-the-password", REALM)

        client.use {
            runBlocking {
                val response: HttpResponse = client.get("stock/PALLET-42")
                assertEquals(HttpStatusCode.Unauthorized, response.status)
                val challenge: HttpAuthHeader? = parseAuthorizationHeader(
                    response.headers[HttpHeaders.WWWAuthenticate]!!
                )
                assertTrue(challenge is HttpAuthHeader.Parameterized)
                assertEquals("Digest", challenge!!.authScheme)
                assertEquals(REALM, (challenge as HttpAuthHeader.Parameterized).parameter("realm"))
            }
        }

        assertEquals(emptyList<ReceivedRequest>(), receivedRequests.toList())
    }

    @Test
    fun consoleBoundToAnotherRealmDoesNotAnswerTheChallenge() {
        val client: HttpClient = consoleClient(OPERATOR, OPERATORS.getValue(OPERATOR), "Head Office")

        client.use {
            runBlocking {
                val response: HttpResponse = client.get("stock/PALLET-42")
                assertEquals(HttpStatusCode.Unauthorized, response.status)
                assertNull(response.request.headers[HttpHeaders.Authorization])
            }
        }

        assertEquals(emptyList<ReceivedRequest>(), receivedRequests.toList())
    }

    private fun consoleClient(user: String, secret: String, clientRealm: String): HttpClient {
        val inventoryUrl: String = "http://127.0.0.1:$port/"
        return HttpClient(ClientCIO) {
            install(Auth) {
                digest {
                    algorithmName = "MD5"
                    realm = clientRealm
                    credentials { DigestAuthCredentials(username = user, password = secret) }
                }
            }
            defaultRequest {
                url(inventoryUrl)
            }
        }
    }

    private fun record(method: String, path: String, authorization: String?) {
        receivedRequests += ReceivedRequest(method, path, authorization)
    }

    private companion object {
        const val REALM: String = "Warehouse Ops"

        const val OPERATOR: String = "dock-supervisor"

        val OPERATORS: Map<String, String> = mapOf(OPERATOR to "forklift-2026", "night-shift" to "pallet-jack")

        val STOCK: Map<String, Int> = mapOf("PALLET-42" to 128, "CRATE-7" to 40)

        fun md5(text: String): ByteArray = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
    }
}
