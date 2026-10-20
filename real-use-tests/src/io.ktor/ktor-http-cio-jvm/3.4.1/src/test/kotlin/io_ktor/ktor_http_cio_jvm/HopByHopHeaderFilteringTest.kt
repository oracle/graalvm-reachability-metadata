/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.HttpHeaders
import io.ktor.http.cio.CIOHeaders
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.HttpHeadersMap
import io.ktor.http.cio.parseHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers standalone header-block parsing with `parseHeaders` and `Connection` option tokenizing:
 * a forwarding proxy strips hop-by-hop headers, including those the `Connection` header nominates.
 */
class HopByHopHeaderFilteringTest {

    /** What the proxy sends upstream for one inbound header block; header order is not preserved. */
    private data class Forwarded(
        val headers: List<Pair<String, String>>,
        val connection: ConnectionOptions?,
        val log: String,
    )

    /** A forwarding proxy that rewrites an inbound header block before passing it upstream. */
    private class ForwardingProxy(private val name: String) {

        suspend fun forward(inbound: ByteReadChannel): Forwarded {
            val parsed: HttpHeadersMap = parseHeaders(inbound)
            try {
                val connection: ConnectionOptions? = ConnectionOptions.parse(parsed[HttpHeaders.Connection])
                val nominated: Set<String> = connection?.extraOptions.orEmpty().map { it.lowercase() }.toSet()
                val dropped: Set<String> = HOP_BY_HOP + nominated
                val kept: MutableList<Pair<String, String>> = mutableListOf()
                for (offset: Int in parsed.offsets()) {
                    val headerName: String = parsed.nameAtOffset(offset).toString()
                    if (headerName.lowercase() !in dropped) {
                        kept += headerName to parsed.valueAtOffset(offset).toString()
                    }
                }
                val priorVia: List<String> = CIOHeaders(parsed).getAll(HttpHeaders.Via).orEmpty()
                kept.removeAll { it.first.equals(HttpHeaders.Via, ignoreCase = true) }
                kept += HttpHeaders.Via to (priorVia + "1.1 $name").joinToString(", ")
                return Forwarded(kept, connection, parsed.toString())
            } finally {
                parsed.release()
            }
        }

        private companion object {
            val HOP_BY_HOP: Set<String> = setOf(
                "connection", "keep-alive", "proxy-connection", "te", "trailer", "upgrade",
                "proxy-authorization",
            )
        }
    }

    private val proxy: ForwardingProxy = ForwardingProxy("edge-proxy")

    @Test
    fun stripsStandardAndNominatedHopByHopHeaders() = runBlocking {
        val inbound: ByteReadChannel = ByteReadChannel(
            """
            Host: shop.example
            Accept: application/json
            Connection: keep-alive, X-Session-Trace
            Keep-Alive: timeout=5, max=100
            X-Session-Trace: tr-77
            Proxy-Authorization: Basic YWxpY2U6c2VjcmV0
            TE: trailers
            Via: 1.1 cdn-a
            Via: 1.0 cdn-b
            X-Request-Id: req-9

            """.trimIndent().replace("\n", "\r\n") + "\r\n",
        )

        val forwarded: Forwarded = withTimeout(10_000) { proxy.forward(inbound) }

        assertEquals(
            setOf(
                "Host" to "shop.example",
                "Accept" to "application/json",
                "X-Request-Id" to "req-9",
                "Via" to "1.1 cdn-a, 1.0 cdn-b, 1.1 edge-proxy",
            ),
            forwarded.headers.toSet(),
        )
        val connection: ConnectionOptions = forwarded.connection!!
        assertTrue(connection.keepAlive)
        assertFalse(connection.close)
        assertFalse(connection.upgrade)
        assertEquals(listOf("X-Session-Trace"), connection.extraOptions)
        assertTrue(forwarded.log.contains("X-Session-Trace"), forwarded.log)
        assertTrue(forwarded.log.contains("tr-77"), forwarded.log)
    }

    @Test
    fun recognizesCloseAndUpgradeTokensCaseInsensitively() = runBlocking {
        val inbound: ByteReadChannel = ByteReadChannel(
            "Host: chat.example\r\n" +
                "CONNECTION: Upgrade, Close\r\n" +
                "Upgrade: websocket\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "\r\n",
        )

        val forwarded: Forwarded = withTimeout(10_000) { proxy.forward(inbound) }

        val connection: ConnectionOptions = forwarded.connection!!
        assertTrue(connection.close)
        assertTrue(connection.upgrade)
        assertFalse(connection.keepAlive)
        assertTrue(connection.extraOptions.isEmpty())
        assertEquals(
            setOf(
                "Host" to "chat.example",
                "Sec-WebSocket-Version" to "13",
                "Via" to "1.1 edge-proxy",
            ),
            forwarded.headers.toSet(),
        )
    }

    @Test
    fun consumesOnlyTheHeaderBlockAndLeavesTheBodyOnTheChannel() = runBlocking {
        val inbound: ByteReadChannel = ByteReadChannel(
            "Content-Type: text/plain\r\n" +
                "Content-Length: 11\r\n" +
                "\r\n" +
                "hello proxy",
        )

        val forwarded: Forwarded = withTimeout(10_000) { proxy.forward(inbound) }
        val body: String = inbound.readRemaining().readByteArray().decodeToString()

        assertNull(forwarded.connection)
        assertEquals(
            setOf(
                "Content-Type" to "text/plain",
                "Content-Length" to "11",
                "Via" to "1.1 edge-proxy",
            ),
            forwarded.headers.toSet(),
        )
        assertEquals("hello proxy", body)
    }
}
