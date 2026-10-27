/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.BufferedReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO HTTP/1.1 request pipelining through a gateway requesting shipping quotes on one connection.
 */
class PipelinedShippingQuotesTest {
    @Test
    @Timeout(60)
    fun `returns distinct shipping quotes in request order before closing the connection`(): Unit = runBlocking {
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                post("/shipping/quotes/{service}") {
                    val service: String = call.parameters["service"]!!
                    val weight: Int = call.receiveText().toInt()
                    val basePrice: Int = if (service == "express") 1000 else 400
                    call.respondText(
                        "quote:$service:$weight:${basePrice + weight * 100}",
                        ContentType.Text.Plain,
                    )
                }
            }
        }
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                Socket().use { socket: Socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 15_000)
                    socket.soTimeout = 15_000
                    val requests: String = quoteRequest(port, "standard", "2", false) +
                        quoteRequest(port, "express", "12", true)
                    // The gateway submits the entire batch before reading either response.
                    socket.getOutputStream().write(requests.toByteArray(Charsets.US_ASCII))
                    socket.getOutputStream().flush()
                    val reader: BufferedReader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val standard: QuoteResponse = readResponse(reader)
                    val express: QuoteResponse = readResponse(reader)

                    assertEquals("HTTP/1.1 200 OK", standard.status)
                    assertEquals("quote:standard:2:600", standard.body)
                    assertTrue(standard.headers.getValue("content-type").startsWith("text/plain"))
                    assertEquals("HTTP/1.1 200 OK", express.status)
                    assertEquals("quote:express:12:2200", express.body)
                    assertTrue(express.headers.getValue("content-type").startsWith("text/plain"))
                    assertEquals(-1, reader.read(), "The gateway requested closure after the last quote")
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }

    private fun quoteRequest(port: Int, service: String, weight: String, close: Boolean): String = buildString {
        append("POST /shipping/quotes/$service HTTP/1.1\r\n")
        append("Host: 127.0.0.1:$port\r\n")
        append("Content-Type: text/plain\r\n")
        append("Content-Length: ${weight.length}\r\n")
        append("Connection: ${if (close) "close" else "keep-alive"}\r\n")
        append("\r\n")
        append(weight)
    }

    private fun readResponse(reader: BufferedReader): QuoteResponse {
        val status: String = checkNotNull(reader.readLine()) { "Missing quote response" }
        val headers: MutableMap<String, String> = mutableMapOf()
        while (true) {
            val line: String = checkNotNull(reader.readLine()) { "Incomplete response headers" }
            if (line.isEmpty()) break
            val separator: Int = line.indexOf(':')
            check(separator > 0) { "Invalid response header: $line" }
            headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
        }
        val body: CharArray = CharArray(headers.getValue("content-length").toInt())
        var offset: Int = 0
        while (offset < body.size) {
            val count: Int = reader.read(body, offset, body.size - offset)
            check(count > 0) { "Quote response ended before its declared content length" }
            offset += count
        }
        return QuoteResponse(status, headers, String(body))
    }

    private data class QuoteResponse(
        val status: String,
        val headers: Map<String, String>,
        val body: String,
    )
}
