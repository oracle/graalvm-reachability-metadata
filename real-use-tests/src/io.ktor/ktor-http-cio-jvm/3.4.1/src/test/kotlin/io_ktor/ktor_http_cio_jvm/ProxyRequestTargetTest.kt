/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.cio.Request
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.expectHttpBody
import io.ktor.http.cio.parseRequest
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Covers HTTP/1.1 request-target forms as a forward proxy sees them: absolute-form targets rewritten
 * to origin-form, authority-form CONNECT tunnels, and the asterisk-form OPTIONS request.
 */
class ProxyRequestTargetTest {

    /** What the proxy decides to do with one parsed request. */
    private sealed class Route {

        /** Forward [wire] to [host]:[port] after rewriting the absolute-form target. */
        data class Forward(val host: String, val port: Int, val wire: String) : Route()

        /** Open a raw tunnel to [host]:[port] and relay [earlyData] the client already sent. */
        data class Tunnel(val host: String, val port: Int, val earlyData: String) : Route()

        /** Answer a server-wide capability probe directly with the methods in [allow]. */
        data class Capabilities(val allow: String) : Route()

        /** Serve the request from the proxy itself, addressed to [path] on [host]. */
        data class Local(val host: String, val path: String) : Route()
    }

    /** A forward proxy front door that inspects the request-target of each incoming request. */
    private class ForwardProxy {

        suspend fun route(connection: ByteReadChannel): Route {
            val request: Request = parseRequest(connection) ?: error("connection closed before a request")
            return request.use { classify(it, connection) }
        }

        private suspend fun classify(request: Request, connection: ByteReadChannel): Route {
            val target: String = request.uri.toString()
            return when {
                request.method == CONNECT -> tunnel(target, connection)
                target == "*" -> Route.Capabilities(ALLOWED.joinToString(", ") { it.value })
                target.startsWith("http://") -> forward(request, target.removePrefix("http://"))
                else -> Route.Local(request.headers[HttpHeaders.Host].toString(), target)
            }
        }

        private suspend fun tunnel(authority: String, connection: ByteReadChannel): Route.Tunnel {
            val host: String = authority.substringBeforeLast(':')
            val port: Int = authority.substringAfterLast(':').toInt()
            val earlyData: String = connection.readRemaining().readByteArray().decodeToString()
            return Route.Tunnel(host, port, earlyData)
        }

        private fun forward(request: Request, rest: String): Route.Forward {
            val authority: String = rest.substringBefore('/')
            val path: String = "/" + rest.substringAfter('/', missingDelimiterValue = "")
            val builder = RequestResponseBuilder()
            try {
                builder.requestLine(request.method, path, request.version.toString())
                for (offset: Int in request.headers.offsets()) {
                    val name: String = request.headers.nameAtOffset(offset).toString()
                    if (!name.equals(HttpHeaders.ProxyAuthorization, ignoreCase = true)) {
                        builder.headerLine(name, request.headers.valueAtOffset(offset))
                    }
                }
                builder.emptyLine()
                val wire: String = builder.build().readByteArray().decodeToString()
                return Route.Forward(
                    host = authority.substringBefore(':'),
                    port = authority.substringAfter(':', missingDelimiterValue = "80").toInt(),
                    wire = wire,
                )
            } finally {
                builder.release()
            }
        }

        companion object {
            val CONNECT: HttpMethod = HttpMethod("CONNECT")
            val ALLOWED: List<HttpMethod> = listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options, CONNECT)
        }
    }

    private fun route(wire: String): Route = runBlocking {
        withTimeout(10_000) { ForwardProxy().route(ByteReadChannel(wire.encodeToByteArray())) }
    }

    @Test
    fun absoluteFormTargetIsRewrittenToOriginForm() {
        val route: Route = route(
            "GET http://catalog.example:8080/items/42?view=full HTTP/1.1\r\n" +
                "Host: catalog.example:8080\r\n" +
                "Proxy-Authorization: Basic c2VjcmV0\r\n" +
                "Accept: application/json\r\n" +
                "\r\n",
        )
        assertEquals(
            Route.Forward(
                host = "catalog.example",
                port = 8080,
                wire = "GET /items/42?view=full HTTP/1.1\r\n" +
                    "Host: catalog.example:8080\r\n" +
                    "Accept: application/json\r\n" +
                    "\r\n",
            ),
            route,
        )

        val bare: Route = route("HEAD http://catalog.example HTTP/1.1\r\nHost: catalog.example\r\n\r\n")
        assertEquals(
            Route.Forward("catalog.example", 80, "HEAD / HTTP/1.1\r\nHost: catalog.example\r\n\r\n"),
            bare,
        )
    }

    @Test
    fun authorityFormConnectOpensTunnelWithEarlyData() {
        val wire: String = "CONNECT secure.example:443 HTTP/1.1\r\n" +
            "Host: secure.example:443\r\n" +
            "\r\n" +
            "\u0016\u0003\u0001client-hello"
        val parsed: Boolean = runBlocking {
            parseRequest(ByteReadChannel(wire.encodeToByteArray()))!!.use { expectHttpBody(it) }
        }
        assertFalse(parsed, "a CONNECT request carries no message body of its own")

        assertEquals(Route.Tunnel("secure.example", 443, "\u0016\u0003\u0001client-hello"), route(wire))
    }

    @Test
    fun asteriskFormOptionsProbesTheProxyItself() {
        val route: Route = route("OPTIONS * HTTP/1.1\r\nHost: proxy.example\r\nMax-Forwards: 0\r\n\r\n")
        assertEquals(Route.Capabilities("GET, HEAD, OPTIONS, CONNECT"), route)
    }

    @Test
    fun originFormTargetIsServedLocally() {
        val route: Route = route("GET /pac/proxy.pac HTTP/1.1\r\nHost: proxy.example:3128\r\n\r\n")
        assertEquals(Route.Local("proxy.example:3128", "/pac/proxy.pac"), route)
    }
}
