/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import java.io.BufferedReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises CIO Expect: 100-continue negotiation through an artifact uploader that waits before sending its bundle.
 */
class ContinueGatedArtifactUploadTest {
    @Test
    @Timeout(60)
    fun `waits for upload permission then stores the complete release bundle`(@TempDir directory: Path): Unit =
        runBlocking {
            val destination: Path = directory.resolve("release-bundle.bin")
            val bundle: ByteArray = ByteArray(32768) { index: Int -> (index * 31 + 7).toByte() }
            val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                routing {
                    post("/artifacts/release-bundle") {
                        val received: ByteArray = call.receiveChannel().readRemaining().readByteArray()
                        Files.write(destination, received)
                        call.respondText(
                            "stored:release-bundle:${received.size}:${sha256(received)}",
                            ContentType.Text.Plain,
                            HttpStatusCode.Created,
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
                        val headers: String = buildString {
                            append("POST /artifacts/release-bundle HTTP/1.1\r\n")
                            append("Host: 127.0.0.1:$port\r\n")
                            append("Content-Type: application/octet-stream\r\n")
                            append("Content-Length: ${bundle.size}\r\n")
                            append("Expect: 100-continue\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII))
                        socket.getOutputStream().flush()
                        val reader: BufferedReader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val permission: ResponseHead = readHead(reader)
                        assertEquals("HTTP/1.1 100 Continue", permission.status)

                        // A deployment client sends no bundle bytes until the server grants permission.
                        socket.getOutputStream().write(bundle)
                        socket.getOutputStream().flush()
                        val receipt: ResponseHead = readHead(reader)
                        assertEquals("HTTP/1.1 201 Created", receipt.status)
                        assertTrue(receipt.headers.getValue("content-type").startsWith("text/plain"))
                        val expected: String = "stored:release-bundle:${bundle.size}:${sha256(bundle)}"
                        assertEquals(expected.length.toString(), receipt.headers["content-length"])
                        val body: CharArray = CharArray(expected.length)
                        var offset: Int = 0
                        while (offset < body.size) {
                            val count: Int = reader.read(body, offset, body.size - offset)
                            check(count > 0) { "Artifact receipt ended before its declared length" }
                            offset += count
                        }
                        assertEquals(expected, String(body))
                        assertEquals(-1, reader.read(), "The upload requested connection closure")
                        assertArrayEquals(bundle, Files.readAllBytes(destination))
                    }
                }
            } finally {
                server.stop(0, 10_000)
            }
        }

    private fun readHead(reader: BufferedReader): ResponseHead {
        val status: String = checkNotNull(reader.readLine()) { "Missing upload response" }
        val headers: MutableMap<String, String> = mutableMapOf()
        while (true) {
            val line: String = checkNotNull(reader.readLine()) { "Incomplete upload response headers" }
            if (line.isEmpty()) break
            val separator: Int = line.indexOf(':')
            check(separator > 0) { "Invalid response header: $line" }
            headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
        }
        return ResponseHead(status, headers)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte: Byte -> "%02x".format(byte) }

    private data class ResponseHead(val status: String, val headers: Map<String, String>)
}
