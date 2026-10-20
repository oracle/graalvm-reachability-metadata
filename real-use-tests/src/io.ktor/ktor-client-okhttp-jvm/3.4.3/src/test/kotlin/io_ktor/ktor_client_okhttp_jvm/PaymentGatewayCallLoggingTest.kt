/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Request and response logging through the client `Logging` plugin over the OkHttp engine.
 *
 * A payment-gateway client logs its calls into the application's audit log. In Ktor's default
 * format it records the URL, method, headers and bodies of each charge, with the `Authorization`
 * header and the merchant API key masked; in OkHttp's own line format it records one request
 * line and one response line per call with their body sizes; and a request filter keeps health
 * probes out of the audit log. The test reads the lines the application's logger received and
 * checks the calls themselves still reached the gateway unmasked.
 */
class PaymentGatewayCallLoggingTest {

    private lateinit var gateway: MockWebServer

    @BeforeEach
    fun start() {
        gateway = MockWebServer()
        gateway.start()
    }

    @AfterEach
    fun stop() {
        gateway.close()
    }

    @Test
    fun chargesAreLoggedWithHeadersAndBodiesAndMaskedCredentials() {
        gateway.enqueue(jsonReply(201, CHARGE_RECEIPT))

        val audit = AuditLog()
        lateinit var receipt: String
        lateinit var status: HttpStatusCode
        gatewayClient(audit, LoggingFormat.Default, LogLevel.ALL).use { client: HttpClient ->
            runBlocking {
                val response: HttpResponse = client.post(gateway.url("/v1/charges").toString()) {
                    bearerAuth(ACCESS_TOKEN)
                    header(MERCHANT_KEY_HEADER, MERCHANT_KEY)
                    contentType(ContentType.Application.Json)
                    setBody(CHARGE_REQUEST)
                }
                status = response.status
                receipt = response.bodyAsText()
            }
        }

        assertEquals(HttpStatusCode.Created, status)
        assertEquals(CHARGE_RECEIPT, receipt)
        audit.awaitLine { line: String -> line.contains(CHARGE_RECEIPT) }

        val log: String = audit.text()
        assertTrue(log.contains("REQUEST: ${gateway.url("/v1/charges")}"), log)
        assertTrue(log.contains("METHOD: POST"), log)
        assertTrue(log.contains("-> ${HttpHeaders.Authorization}: ***"), log)
        assertTrue(log.contains("-> $MERCHANT_KEY_HEADER: ***"), log)
        assertTrue(log.contains(CHARGE_REQUEST), log)
        assertTrue(log.contains("RESPONSE: 201 "), log)
        assertTrue(log.contains("FROM: ${gateway.url("/v1/charges")}"), log)
        assertTrue(log.contains("-> ${HttpHeaders.ContentType}: application/json", ignoreCase = true), log)
        assertFalse(log.contains(ACCESS_TOKEN), log)
        assertFalse(log.contains(MERCHANT_KEY), log)

        val charge: RecordedRequest = takeRecorded()
        assertEquals("Bearer $ACCESS_TOKEN", charge.headers[HttpHeaders.Authorization])
        assertEquals(MERCHANT_KEY, charge.headers[MERCHANT_KEY_HEADER])
        assertEquals(CHARGE_REQUEST, charge.body!!.utf8())
    }

    @Test
    fun okHttpFormatWritesOneRequestLineAndOneResponseLinePerCall() {
        gateway.enqueue(jsonReply(200, REFUND_RECEIPT))

        val audit = AuditLog()
        lateinit var receipt: String
        gatewayClient(audit, LoggingFormat.OkHttp, LogLevel.INFO).use { client: HttpClient ->
            runBlocking {
                val response: HttpResponse = client.post(gateway.url("/v1/refunds").toString()) {
                    contentType(ContentType.Application.Json)
                    setBody(REFUND_REQUEST)
                }
                receipt = response.bodyAsText()
            }
        }

        assertEquals(REFUND_RECEIPT, receipt)
        audit.awaitLine { line: String -> line.startsWith("<-- ") }

        val refundUrl: String = gateway.url("/v1/refunds").toString()
        val requestLine: String = audit.single { line: String -> line.startsWith("--> ") }
        assertEquals("--> POST $refundUrl (${REFUND_REQUEST.length}-byte body)", requestLine)
        val responseLine: String = audit.single { line: String -> line.startsWith("<-- ") }
        val expectedResponse = Regex(
            """<-- 200 OK ${Regex.escape(refundUrl)} \(\d+ms, ${REFUND_RECEIPT.length}-byte body\)""",
        )
        assertTrue(expectedResponse.matches(responseLine), responseLine)
        assertEquals(REFUND_REQUEST, takeRecorded().body!!.utf8())
    }

    @Test
    fun healthProbesAreFilteredOutOfTheAuditLog() {
        gateway.enqueue(MockResponse.Builder().code(200).body("UP").build())
        gateway.enqueue(jsonReply(200, BALANCE))

        val audit = AuditLog()
        lateinit var health: String
        lateinit var balance: String
        val client: HttpClient = HttpClient(OkHttp) {
            Logging {
                logger = audit
                level = LogLevel.INFO
                filter { request: HttpRequestBuilder -> "health" !in request.url.pathSegments }
            }
        }
        client.use {
            runBlocking {
                health = client.get(gateway.url("/health").toString()).bodyAsText()
                balance = client.get(gateway.url("/v1/balance").toString()).bodyAsText()
            }
        }

        assertEquals("UP", health)
        assertEquals(BALANCE, balance)
        audit.awaitLine { line: String -> line.startsWith("RESPONSE: ") }

        val log: String = audit.text()
        assertTrue(log.contains("REQUEST: ${gateway.url("/v1/balance")}"), log)
        assertTrue(log.contains("RESPONSE: 200 OK"), log)
        assertFalse(log.contains("/health"), log)
        assertEquals("/health", takeRecorded().url.encodedPath)
        assertEquals("/v1/balance", takeRecorded().url.encodedPath)
    }

    /** Builds the gateway client the way the application does: OkHttp under Ktor's Logging. */
    private fun gatewayClient(audit: AuditLog, logFormat: LoggingFormat, logLevel: LogLevel): HttpClient =
        HttpClient(OkHttp) {
            Logging {
                logger = audit
                level = logLevel
                format = logFormat
                sanitizeHeader { header: String -> header == HttpHeaders.Authorization }
                sanitizeHeader("***") { header: String -> header == MERCHANT_KEY_HEADER }
            }
        }

    private fun jsonReply(code: Int, body: String): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader(HttpHeaders.ContentType, "application/json")
        .body(body)
        .build()

    private fun takeRecorded(): RecordedRequest =
        checkNotNull(gateway.takeRequest(10, TimeUnit.SECONDS)) { "the gateway received no request" }

    /** The application's audit log: a Ktor `Logger` that keeps every message it is given. */
    private class AuditLog : Logger {
        private val lines: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

        override fun log(message: String) {
            message.lines().forEach { line: String -> lines.add(line) }
        }

        fun text(): String = lines.joinToString("\n")

        fun single(predicate: (String) -> Boolean): String = lines.filter(predicate).single()

        /** Waits, bounded, for a line the plugin may write after the response was handed back. */
        fun awaitLine(predicate: (String) -> Boolean) {
            val deadline: Long = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (lines.none(predicate)) {
                check(System.nanoTime() < deadline) { "expected log line never arrived:\n${text()}" }
                Thread.sleep(10)
            }
        }
    }

    private companion object {
        const val ACCESS_TOKEN: String = "tok_live_5f2a9c"
        const val MERCHANT_KEY_HEADER: String = "X-Merchant-Key"
        const val MERCHANT_KEY: String = "mk_7781_secret"
        const val CHARGE_REQUEST: String = """{"amount":4200,"currency":"EUR","card":"tok_visa"}"""
        const val CHARGE_RECEIPT: String = """{"id":"ch_1001","status":"captured","amount":4200}"""
        const val REFUND_REQUEST: String = """{"charge":"ch_1001","amount":1200}"""
        const val REFUND_RECEIPT: String = """{"id":"re_2001","status":"pending"}"""
        const val BALANCE: String = """{"available":98000,"currency":"EUR"}"""
    }
}
