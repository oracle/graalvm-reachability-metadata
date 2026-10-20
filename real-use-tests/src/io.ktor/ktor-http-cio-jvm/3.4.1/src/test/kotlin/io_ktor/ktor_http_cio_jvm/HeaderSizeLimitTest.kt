/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.CIOHeaders
import io.ktor.http.cio.ParserException
import io.ktor.http.cio.Request
import io.ktor.http.cio.Response
import io.ktor.http.cio.parseRequest
import io.ktor.http.cio.parseResponse
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.TooLongLineException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the 8 KiB line-length limit and header-block growth of the HTTP/1.1 parser: an edge
 * gateway forwards near-limit cookies and large header blocks, and refuses over-long lines.
 */
class HeaderSizeLimitTest {

    /** What the gateway does with one inbound request. */
    private sealed class Outcome {

        /** The request reached the application with these routing facts. */
        data class Forwarded(
            val uri: String,
            val cookieLength: Int,
            val headerNames: Int,
            val tags: Set<String>,
        ) : Outcome()

        /** The request was refused with [status]; [reason] is what the gateway logs. */
        data class Refused(val status: HttpStatusCode, val reason: String) : Outcome()
    }

    /** Parses one request and maps input the parser refuses to the status an edge proxy answers. */
    private class EdgeGateway {

        suspend fun accept(connection: ByteReadChannel): Outcome {
            val request: Request = try {
                parseRequest(connection) ?: return Outcome.Refused(HttpStatusCode.BadRequest, "empty connection")
            } catch (overLong: TooLongLineException) {
                return Outcome.Refused(HttpStatusCode.RequestHeaderFieldTooLarge, overLong.message.orEmpty())
            } catch (atLimit: IllegalStateException) {
                return Outcome.Refused(HttpStatusCode.RequestHeaderFieldTooLarge, atLimit.message.orEmpty())
            } catch (malformed: ParserException) {
                return Outcome.Refused(HttpStatusCode.BadRequest, malformed.message.orEmpty())
            }
            return request.use {
                val headers: Headers = CIOHeaders(it.headers)
                Outcome.Forwarded(
                    uri = it.uri.toString(),
                    cookieLength = headers[HttpHeaders.Cookie]?.length ?: 0,
                    headerNames = headers.names().size,
                    tags = headers.getAll(TAG_HEADER).orEmpty().toSet(),
                )
            }
        }

        companion object {
            const val TAG_HEADER: String = "X-Tag"
        }
    }

    private fun accept(vararg lines: String): Outcome = runBlocking {
        val wire: String = lines.joinToString(separator = "\r\n", postfix = "\r\n")
        withTimeout(10_000) { EdgeGateway().accept(ByteReadChannel(wire.encodeToByteArray())) }
    }

    private fun assertRefused(outcome: Outcome, reasonFragment: String) {
        val refused: Outcome.Refused = outcome as Outcome.Refused
        assertEquals(HttpStatusCode.RequestHeaderFieldTooLarge, refused.status)
        assertTrue(refused.reason.contains(reasonFragment), "unexpected reason: ${refused.reason}")
    }

    @Test
    fun nearLimitCookieAndLargeHeaderBlockAreForwarded() {
        val cookieLine: String = "Cookie: session=" + "a".repeat(LINE_LIMIT - 1 - "Cookie: session=".length)
        val tags: List<String> = (1..120).map { index: Int -> "tag-$index" }
        val traces: List<String> = (0 until 60).map { index: Int -> "X-Trace-$index: span-$index" }
        val outcome: Outcome = accept(
            "GET /dashboard HTTP/1.1",
            "Host: app.example",
            cookieLine,
            *tags.map { tag: String -> "${EdgeGateway.TAG_HEADER}: $tag" }.toTypedArray(),
            *traces.toTypedArray(),
            "",
        )
        assertEquals(LINE_LIMIT - 1, cookieLine.length)
        assertEquals(
            Outcome.Forwarded(
                uri = "/dashboard",
                cookieLength = cookieLine.length - "Cookie: ".length,
                headerNames = 1 + 1 + 1 + traces.size,
                tags = tags.toSet(),
            ),
            outcome,
        )
    }

    @Test
    fun headerLineAtTheLimitIsRefused() {
        val blobLine: String = "X-Blob: " + "b".repeat(LINE_LIMIT - "X-Blob: ".length)
        val outcome: Outcome = accept("GET / HTTP/1.1", "Host: app.example", blobLine, "")
        assertEquals(LINE_LIMIT, blobLine.length)
        assertRefused(outcome, "Header line length limit exceeded")
    }

    @Test
    fun headerLineBeyondTheLimitIsRefused() {
        val outcome: Outcome = accept(
            "GET /dashboard HTTP/1.1",
            "Host: app.example",
            "Cookie: session=" + "c".repeat(9_000),
            "",
        )
        assertRefused(outcome, "Line exceeds limit of $LINE_LIMIT characters")
    }

    @Test
    fun requestLineBeyondTheLimitIsRefused() {
        val outcome: Outcome = accept(
            "GET /search?q=" + "d".repeat(9_000) + " HTTP/1.1",
            "Host: app.example",
            "",
        )
        assertRefused(outcome, "Line exceeds limit of $LINE_LIMIT characters")
    }

    @Test
    fun upstreamResponseWithOverLongHeaderIsRefusedByTheClient(): Unit = runBlocking {
        val accepted: String = "HTTP/1.1 200 OK\r\nSet-Cookie: id=" + "e".repeat(4_000) +
            "\r\nContent-Length: 0\r\n\r\n"
        val status: Int = withTimeout(10_000) {
            parseResponse(ByteReadChannel(accepted.encodeToByteArray()))!!.use { response: Response ->
                assertEquals(4_003, CIOHeaders(response.headers)[HttpHeaders.SetCookie]!!.length)
                response.status
            }
        }
        assertEquals(200, status)

        val oversized: String = "HTTP/1.1 200 OK\r\nSet-Cookie: id=" + "f".repeat(9_000) +
            "\r\nContent-Length: 0\r\n\r\n"
        val failure: Throwable? = withTimeout(10_000) {
            runCatching { parseResponse(ByteReadChannel(oversized.encodeToByteArray()))?.close() }.exceptionOrNull()
        }
        assertTrue(failure is TooLongLineException, "unexpected failure: $failure")
    }

    private companion object {
        const val LINE_LIMIT: Int = 8192
    }
}
