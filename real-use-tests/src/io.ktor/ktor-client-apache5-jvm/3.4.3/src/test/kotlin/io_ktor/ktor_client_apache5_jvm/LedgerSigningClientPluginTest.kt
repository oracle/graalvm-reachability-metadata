/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Custom client plugins built with `createClientPlugin` hooks and `HttpSend` interceptors on the
 * `Apache5` engine.
 *
 * A bookkeeping client talks to a ledger service through an application-defined plugin: it
 * encodes ledger entries into the service's own media type, decodes balances back into domain
 * objects, signs every request with an HMAC over method, path, nonce and body, resynchronises
 * its nonce counter and re-sends when the ledger rejects a stale nonce, and records the request
 * ids the ledger returns. A separate `HttpSend` interceptor logs every attempt on the wire,
 * including the rejected one the plugin absorbs before the application sees a response.
 */
class LedgerSigningClientPluginTest {

    /** A posting the bookkeeping application books against an account. */
    data class LedgerEntry(val account: String, val amountCents: Long, val memo: String)

    /** The balance the ledger reports for an account. */
    data class LedgerBalance(val account: String, val balanceCents: Long)

    /** Configuration of [LedgerSigning]: the shared secret and where request ids are recorded. */
    class LedgerSigningConfig {
        var keyId: String = ""
        var secret: String = ""
        var auditTrail: MutableList<String> = CopyOnWriteArrayList()
    }

    /** HMAC-SHA256 signatures shared by the client plugin and the ledger service. */
    object LedgerSignatures {
        fun sign(secret: String, method: String, path: String, nonce: Long, body: String): String {
            val mac: Mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val payload: String = "$method\n$path\n$nonce\n$body"
            return HexFormat.of().formatHex(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
        }
    }

    /** The ledger's server-side state: balances, the next nonce it accepts, and issued ids. */
    class LedgerBook {
        val balances: MutableMap<String, Long> = ConcurrentHashMap()
        val expectedNonce: AtomicLong = AtomicLong(1)
        val requestIds: AtomicInteger = AtomicInteger(100)
        val conflicts: AtomicInteger = AtomicInteger(0)
    }

    private val book: LedgerBook = LedgerBook()
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/entries") {
                    val body: String = call.receiveText()
                    if (!authorize(call, body)) return@post
                    val (account: String, amount: String, _) = body.split(';', limit = 3)
                    val balance: Long = book.balances.merge(account, amount.toLong(), Long::plus)!!
                    call.respondBalance(account, balance, HttpStatusCode.Created)
                }
                get("/accounts/{account}/balance") {
                    if (!authorize(call, "")) return@get
                    val account: String = call.parameters["account"]!!
                    call.respondBalance(account, book.balances[account] ?: 0, HttpStatusCode.OK)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    /** Verifies the signature and nonce of a ledger call, answering the rejection itself. */
    private suspend fun authorize(call: RoutingCall, body: String): Boolean {
        val keyId: String? = call.request.headers["X-Ledger-Key"]
        val nonce: Long = call.request.headers["X-Ledger-Nonce"]?.toLongOrNull() ?: -1
        val expected: String = LedgerSignatures.sign(
            LEDGER_SECRET,
            call.request.httpMethod.value,
            call.request.path(),
            nonce,
            body,
        )
        if (keyId != LEDGER_KEY_ID || call.request.headers["X-Ledger-Signature"] != expected) {
            call.respondText("bad signature", status = HttpStatusCode.Unauthorized)
            return false
        }
        val next: Long = book.expectedNonce.get()
        if (nonce < next || !book.expectedNonce.compareAndSet(nonce, nonce + 1)) {
            book.conflicts.incrementAndGet()
            call.response.header("X-Ledger-Expected-Nonce", next.toString())
            call.respondText("stale nonce", status = HttpStatusCode.Conflict)
            return false
        }
        call.response.header("X-Request-Id", "req-${book.requestIds.incrementAndGet()}")
        return true
    }

    private suspend fun RoutingCall.respondBalance(account: String, balance: Long, status: HttpStatusCode) {
        respondText("account=$account;balance=$balance", LEDGER_BALANCE, status)
    }

    /** Builds a client whose only ledger knowledge comes from the [LedgerSigning] plugin. */
    private fun ledgerClient(secret: String, auditTrail: MutableList<String>): HttpClient = HttpClient(Apache5) {
        install(LedgerSigning) {
            keyId = LEDGER_KEY_ID
            this.secret = secret
            this.auditTrail = auditTrail
        }
    }

    @Test
    fun signedEntriesAreBookedAndBalancesDecodeIntoDomainObjects() = runBlocking {
        val audit: MutableList<String> = CopyOnWriteArrayList()
        val ledger: String = "http://127.0.0.1:$port"
        ledgerClient(LEDGER_SECRET, audit).use { client: HttpClient ->
            val first: HttpResponse = client.post("$ledger/entries") {
                setBody(LedgerEntry("ACME-1", 12_500, "invoice 2026-001"))
            }
            assertEquals(HttpStatusCode.Created, first.status)
            assertEquals(LedgerBalance("ACME-1", 12_500), first.body<LedgerBalance>())

            val second: LedgerBalance = client.post("$ledger/entries") {
                setBody(LedgerEntry("ACME-1", -2_000, "refund; partial"))
            }.body()
            assertEquals(LedgerBalance("ACME-1", 10_500), second)

            val balance: LedgerBalance = client.get("$ledger/accounts/ACME-1/balance").body()
            assertEquals(10_500, balance.balanceCents)
            val untyped: String = client.get("$ledger/accounts/ACME-1/balance").bodyAsText()
            assertEquals("account=ACME-1;balance=10500", untyped)
        }
        assertEquals(listOf("req-101", "req-102", "req-103", "req-104"), audit)
        assertEquals(0, book.conflicts.get())
    }

    @Test
    fun staleNonceIsResynchronisedAndTheRequestResentOnce() = runBlocking {
        val audit: MutableList<String> = CopyOnWriteArrayList()
        val ledger: String = "http://127.0.0.1:$port"
        book.expectedNonce.set(41)
        ledgerClient(LEDGER_SECRET, audit).use { client: HttpClient ->
            val booked: LedgerBalance = client.post("$ledger/entries") {
                setBody(LedgerEntry("ACME-2", 7_700, "late posting"))
            }.body()
            assertEquals(LedgerBalance("ACME-2", 7_700), booked)
            val balance: LedgerBalance = client.get("$ledger/accounts/ACME-2/balance").body()
            assertEquals(7_700, balance.balanceCents)
        }
        assertEquals(1, book.conflicts.get())
        assertEquals(43, book.expectedNonce.get())
        assertEquals(listOf("req-101", "req-102"), audit)
    }

    @Test
    fun wrongSecretIsRejectedWithoutBookingAnything() = runBlocking {
        val audit: MutableList<String> = CopyOnWriteArrayList()
        val ledger: String = "http://127.0.0.1:$port"
        ledgerClient("not-the-ledger-secret", audit).use { client: HttpClient ->
            val rejected: HttpResponse = client.post("$ledger/entries") {
                setBody(LedgerEntry("ACME-3", 1_000, "forged"))
            }
            assertEquals(HttpStatusCode.Unauthorized, rejected.status)
            assertEquals("bad signature", rejected.bodyAsText())
        }
        assertEquals(null, book.balances["ACME-3"])
        assertEquals(emptyList<String>(), audit)
    }

    @Test
    fun sendInterceptorSeesEveryWireAttemptWhileTheApplicationSeesOneResponse() = runBlocking {
        val audit: MutableList<String> = CopyOnWriteArrayList()
        val wireAttempts: MutableList<String> = CopyOnWriteArrayList()
        val ledger: String = "http://127.0.0.1:$port"
        book.expectedNonce.set(7)
        ledgerClient(LEDGER_SECRET, audit).use { client: HttpClient ->
            client.plugin(HttpSend).intercept { request: HttpRequestBuilder ->
                val call: HttpClientCall = execute(request)
                wireAttempts.add("${request.headers["X-Ledger-Nonce"]}:${call.response.status.value}")
                call
            }

            val response: HttpResponse = client.post("$ledger/entries") {
                setBody(LedgerEntry("ACME-4", 3_300, "subscription"))
            }
            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(LedgerBalance("ACME-4", 3_300), response.body<LedgerBalance>())
        }
        assertEquals(listOf("1:409", "7:201"), wireAttempts)
        assertEquals(listOf("req-101"), audit)
    }

    companion object {
        const val LEDGER_KEY_ID: String = "bookkeeping-app"
        const val LEDGER_SECRET: String = "ledger-shared-secret"
        val LEDGER_ENTRY: ContentType = ContentType("application", "vnd.ledger.entry")
        val LEDGER_BALANCE: ContentType = ContentType("application", "vnd.ledger.balance")

        /** The application's ledger plugin: codec, request signing, nonce resync and auditing. */
        val LedgerSigning: ClientPlugin<LedgerSigningConfig> =
            createClientPlugin("LedgerSigning", ::LedgerSigningConfig) {
                val keyId: String = pluginConfig.keyId
                val secret: String = pluginConfig.secret
                val auditTrail: MutableList<String> = pluginConfig.auditTrail
                val nonce: AtomicLong = AtomicLong(1)

                fun signInto(request: HttpRequestBuilder) {
                    val body: String = (request.body as? TextContent)?.text ?: ""
                    val current: Long = nonce.getAndIncrement()
                    val signature: String =
                        LedgerSignatures.sign(secret, request.method.value, request.url.encodedPath, current, body)
                    request.headers["X-Ledger-Key"] = keyId
                    request.headers["X-Ledger-Nonce"] = current.toString()
                    request.headers["X-Ledger-Signature"] = signature
                }

                transformRequestBody { _, content, _ ->
                    if (content is LedgerEntry) {
                        TextContent("${content.account};${content.amountCents};${content.memo}", LEDGER_ENTRY)
                    } else {
                        null
                    }
                }

                on(Send) { request: HttpRequestBuilder ->
                    signInto(request)
                    val call: HttpClientCall = proceed(request)
                    val expected: Long? = call.response.headers["X-Ledger-Expected-Nonce"]?.toLongOrNull()
                    if (call.response.status == HttpStatusCode.Conflict && expected != null) {
                        nonce.set(expected)
                        signInto(request)
                        proceed(request)
                    } else {
                        call
                    }
                }

                onResponse { response: HttpResponse ->
                    response.headers["X-Request-Id"]?.let { id: String -> auditTrail.add(id) }
                }

                transformResponseBody { _, content: ByteReadChannel, requestedType ->
                    if (requestedType.type != LedgerBalance::class) {
                        null
                    } else {
                        val fields: Map<String, String> = content.readRemaining().readString()
                            .split(';')
                            .associate { field: String -> field.substringBefore('=') to field.substringAfter('=') }
                        LedgerBalance(fields.getValue("account"), fields.getValue("balance").toLong())
                    }
                }
            }
    }
}
