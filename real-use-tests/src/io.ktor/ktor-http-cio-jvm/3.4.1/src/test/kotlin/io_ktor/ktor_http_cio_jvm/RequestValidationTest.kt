/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.ParserException
import io.ktor.http.cio.Request
import io.ktor.http.cio.expectHttpBody
import io.ktor.http.cio.expectHttpUpgrade
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseRequest
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers request validation: malformed request lines, header names, Host values, and transfer
 * encodings rejected by the parser, bodies without a declared length refused, and upgrades detected.
 */
class RequestValidationTest {

    /** The decision the application's front door takes on one incoming connection. */
    private sealed class Verdict {

        /** The request is well-formed and is handed to the application. */
        data class Admitted(
            val method: HttpMethod,
            val uri: String,
            val upgrade: Boolean,
            val body: String,
        ) : Verdict()

        /** The request is answered with [status] and the connection is dropped. */
        data class Rejected(val status: HttpStatusCode, val reason: String) : Verdict()
    }

    /** Parses the first request on a connection and decides whether the application may see it. */
    private class Gatekeeper {

        suspend fun inspect(connection: ByteReadChannel): Verdict {
            val request: Request = try {
                parseRequest(connection) ?: return Verdict.Rejected(HttpStatusCode.BadRequest, "empty connection")
            } catch (malformed: ParserException) {
                return Verdict.Rejected(HttpStatusCode.BadRequest, malformed.message.orEmpty())
            }
            return request.use { admit(it, connection) }
        }

        private suspend fun admit(request: Request, connection: ByteReadChannel): Verdict {
            val hasBody: Boolean = try {
                expectHttpBody(request)
            } catch (unsupported: IllegalArgumentException) {
                return Verdict.Rejected(HttpStatusCode.NotImplemented, unsupported.message.orEmpty())
            }
            val carriesPayload: Boolean = request.method in PAYLOAD_METHODS
            val body: String = if (hasBody || carriesPayload) {
                try {
                    readBody(request, connection)
                } catch (refused: ClosedByteChannelException) {
                    return Verdict.Rejected(HttpStatusCode.LengthRequired, refused.message.orEmpty())
                }
            } else {
                ""
            }
            return Verdict.Admitted(
                method = request.method,
                uri = request.uri.toString(),
                upgrade = expectHttpUpgrade(request),
                body = body,
            )
        }

        /** Returns the body; reading it fails when the parser refused it for lack of a declared length. */
        private suspend fun readBody(request: Request, connection: ByteReadChannel): String {
            val sink = ByteChannel()
            parseHttpBody(
                HttpProtocolVersion.parse(request.version),
                request.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L,
                request.headers[HttpHeaders.TransferEncoding],
                ConnectionOptions.parse(request.headers[HttpHeaders.Connection]),
                connection,
                sink,
            )
            if (!sink.isClosedForWrite) {
                sink.flushAndClose()
            }
            return sink.readRemaining().readByteArray().decodeToString()
        }

        companion object {
            private val PAYLOAD_METHODS: Set<HttpMethod> = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)
        }
    }

    private fun inspect(vararg lines: String): Verdict = runBlocking {
        val wire: String = lines.joinToString(separator = "\r\n", postfix = "\r\n")
        withTimeout(10_000) { Gatekeeper().inspect(ByteReadChannel(wire.encodeToByteArray())) }
    }

    private fun assertRejected(verdict: Verdict, status: HttpStatusCode, reasonFragment: String) {
        val rejected: Verdict.Rejected = verdict as Verdict.Rejected
        assertEquals(status, rejected.status)
        assertTrue(rejected.reason.contains(reasonFragment), "unexpected reason: ${rejected.reason}")
    }

    @Test
    fun wellFormedRequestsAreAdmitted() {
        val upload: Verdict = inspect(
            "POST /api/notes HTTP/1.1",
            "Host: notes.example:8443",
            "Content-Length: 11",
            "",
            "hello world",
        )
        assertEquals(Verdict.Admitted(HttpMethod.Post, "/api/notes", upgrade = false, body = "hello world"), upload)

        val socket: Verdict = inspect(
            "GET /live HTTP/1.1",
            "Host: notes.example",
            "Connection: keep-alive, Upgrade",
            "Upgrade: websocket",
            "Sec-WebSocket-Version: 13",
            "",
        )
        assertEquals(Verdict.Admitted(HttpMethod.Get, "/live", upgrade = true, body = ""), socket)

        val plain: Verdict = inspect(
            "GET /live HTTP/1.1",
            "Host: notes.example",
            "Upgrade: websocket",
            "",
        )
        assertEquals(Verdict.Admitted(HttpMethod.Get, "/live", upgrade = false, body = ""), plain)
    }

    @Test
    fun malformedRequestLinesAreRejected() {
        assertRejected(
            inspect("GET / HTTP/2.0", "Host: notes.example", ""),
            HttpStatusCode.BadRequest,
            "Unsupported HTTP version: HTTP/2.0",
        )
        assertRejected(
            inspect("GET / HTTP/1.1 trailing", "Host: notes.example", ""),
            HttpStatusCode.BadRequest,
            "Extra characters in request line: trailing",
        )
    }

    @Test
    fun malformedHeadersAreRejected() {
        assertRejected(
            inspect("GET / HTTP/1.1", "Host: notes.example", "X-Note: first", " folded", ""),
            HttpStatusCode.BadRequest,
            "Multiline headers via line folding is not supported",
        )
        assertRejected(
            inspect("GET / HTTP/1.1", "Host: notes.example", "X Note: spaced", ""),
            HttpStatusCode.BadRequest,
            "Character with code 32 is not allowed in header names",
        )
        assertRejected(
            inspect("GET / HTTP/1.1", "Host: notes.example", ": anonymous", ""),
            HttpStatusCode.BadRequest,
            "Empty header names are not allowed",
        )
        assertRejected(
            inspect("GET / HTTP/1.1", "Host: notes.example", "NoColonHere", ""),
            HttpStatusCode.BadRequest,
            "No colon in HTTP header in NoColonHere",
        )
    }

    @Test
    fun suspiciousHostHeadersAreRejected() {
        assertRejected(
            inspect("GET / HTTP/1.1", "Host: notes.example/admin", ""),
            HttpStatusCode.BadRequest,
            "Host cannot contain any of the following symbols",
        )
        assertRejected(
            inspect("GET / HTTP/1.1", "Host: user@notes.example", ""),
            HttpStatusCode.BadRequest,
            "Host cannot contain any of the following symbols",
        )
        assertRejected(
            inspect("GET / HTTP/1.1", "Host: notes.example:", ""),
            HttpStatusCode.BadRequest,
            "Host header with ':' should contains port",
        )
    }

    @Test
    fun ambiguousBodiesAreRejected() {
        assertRejected(
            inspect("POST /api/notes HTTP/1.1", "Host: notes.example", "Transfer-Encoding: gzip", "", ""),
            HttpStatusCode.NotImplemented,
            "Unsupported transfer encoding gzip",
        )
        assertRejected(
            inspect("POST /api/notes HTTP/1.1", "Host: notes.example", "Transfer-Encoding: chunked, chunked", ""),
            HttpStatusCode.NotImplemented,
            "Double-chunked TE is not supported",
        )
        assertRejected(
            inspect("POST /api/notes HTTP/1.1", "Host: notes.example", "", "hello world"),
            HttpStatusCode.LengthRequired,
            "request body length should be specified",
        )
    }
}
