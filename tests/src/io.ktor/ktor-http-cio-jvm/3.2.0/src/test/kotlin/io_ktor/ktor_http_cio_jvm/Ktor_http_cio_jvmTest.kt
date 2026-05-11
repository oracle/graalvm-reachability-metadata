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
import io.ktor.http.cio.CIOHeaders
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.MultipartEvent
import io.ktor.http.cio.ParserException
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.decodeChunked
import io.ktor.http.cio.encodeChunked
import io.ktor.http.cio.expectHttpBody
import io.ktor.http.cio.expectHttpUpgrade
import io.ktor.http.cio.parseHeaders
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseMultipart
import io.ktor.http.cio.parseRequest
import io.ktor.http.cio.parseResponse
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.Source
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

public class Ktor_http_cio_jvmTest {
    @Test
    fun parsesRequestsHeadersAndBodyExpectations(): Unit = runBlocking {
        withTimeout(10_000) {
            val rawRequest: String = """

                GET /submit?name=alice HTTP/1.1
                Host: example.com
                Connection: keep-alive, Upgrade, X-Hop
                Upgrade: websocket
                Content-Length: 7
                X-Custom: first
                x-custom: second

                payload-extra
            """.trimIndent().replace("\n", "\r\n")
            val channel = ByteReadChannel(rawRequest)
            val request = parseRequest(channel) ?: error("Expected a request")

            try {
                assertThat(request.method).isEqualTo(HttpMethod.Get)
                assertThat(request.uri.toString()).isEqualTo("/submit?name=alice")
                assertThat(request.version.toString()).isEqualTo("HTTP/1.1")
                assertThat(request.headers[HttpHeaders.Host].toString()).isEqualTo("example.com")
                assertThat(request.headers.getAll("X-Custom").map { it.toString() }.toList())
                    .containsExactly("first", "second")

                val cioHeaders = CIOHeaders(request.headers)
                assertThat(cioHeaders.caseInsensitiveName).isTrue()
                assertThat(cioHeaders.get("x-custom")).isEqualTo("first")
                assertThat(cioHeaders.getAll("X-CUSTOM")).containsExactly("first", "second")
                assertThat(cioHeaders.contains("host", "example.com")).isTrue()
                assertThat(cioHeaders.names()).contains("Host", "Connection", "Upgrade", "Content-Length", "X-Custom")

                val connection = ConnectionOptions.parse(request.headers[HttpHeaders.Connection])
                assertThat(connection).isEqualTo(
                    ConnectionOptions(keepAlive = true, upgrade = true, extraOptions = listOf("X-Hop"))
                )
                assertThat(connection.toString()).isEqualTo("keep-alive, Upgrade, X-Hop")
                assertThat(expectHttpUpgrade(request)).isTrue()
                assertThat(expectHttpBody(request)).isTrue()

                val body = ByteChannel(autoFlush = true)
                parseHttpBody(request.headers, channel, body)
                body.flushAndClose()
                assertThat(body.toByteArray().decodeToString()).isEqualTo("payload")
            } finally {
                request.release()
            }
        }
    }

    @Test
    fun parsesResponsesBuiltWithRequestResponseBuilder(): Unit = runBlocking {
        withTimeout(10_000) {
            val builder = RequestResponseBuilder()
            try {
                builder.responseLine("HTTP/1.1", 201, "Created")
                builder.headerLine("Content-Type", "text/plain")
                builder.headerLine("Set-Cookie", "a=1")
                builder.headerLine("Set-Cookie", "b=2")
                builder.emptyLine()
                builder.bytes("accepted".encodeToByteArray())

                val response = parseResponse(ByteReadChannel(builder.build())) ?: error("Expected a response")
                try {
                    assertThat(response.version.toString()).isEqualTo("HTTP/1.1")
                    assertThat(response.status).isEqualTo(201)
                    assertThat(response.statusText.toString()).isEqualTo("Created")
                    assertThat(response.headers["content-type"].toString()).isEqualTo("text/plain")
                    assertThat(response.headers.getAll("set-cookie").map { it.toString() }.toList())
                        .containsExactly("a=1", "b=2")
                } finally {
                    response.release()
                }
            } finally {
                builder.release()
            }
        }
    }

    @Test
    fun requestResponseBuilderWritesLinesByteArraysAndByteBuffers(): Unit {
        val builder = RequestResponseBuilder()
        try {
            builder.requestLine(HttpMethod.Put, "/resource/1", "HTTP/1.0")
            builder.headerLine("Host", "localhost")
            builder.line("X-Trace: abc")
            builder.emptyLine()
            builder.bytes("alpha".encodeToByteArray())
            builder.bytes("0123456789".encodeToByteArray(), offset = 2, length = 4)
            builder.bytes(ByteBuffer.wrap("omega".encodeToByteArray()))

            val packet: Source = builder.build()
            packet.use {
                assertThat(it.readText()).isEqualTo(
                    "PUT /resource/1 HTTP/1.0\r\n" +
                        "Host: localhost\r\n" +
                        "X-Trace: abc\r\n" +
                        "\r\n" +
                        "alpha2345omega"
                )
            }
        } finally {
            builder.release()
        }
    }

    @Test
    fun parsesStandaloneHeadersAndRejectsInvalidHeaderSyntax(): Unit = runBlocking {
        withTimeout(10_000) {
            val headers = parseHeaders(
                ByteReadChannel(
                    "Host: example.org\n" +
                        "Transfer-Encoding: identity, chunked\n" +
                        "Connection: close\n" +
                        "X-Whitespace: \t value with trimmed padding   \n" +
                        "\n"
                )
            )
            try {
                assertThat(headers.size).isEqualTo(4)
                assertThat(headers["host"].toString()).isEqualTo("example.org")
                assertThat(headers["X-Whitespace"].toString()).isEqualTo("value with trimmed padding")
                assertThat(expectHttpBody(HttpMethod.Post, -1, headers["Transfer-Encoding"], null, null)).isTrue()
                assertThat(expectHttpBody(HttpMethod.Get, -1, null, ConnectionOptions.Close, null)).isFalse()
                assertThat(expectHttpBody(HttpMethod.Post, -1, null, ConnectionOptions.Close, null)).isTrue()
                assertThat(expectHttpBody(HttpMethod.Head, 5, null, null, null)).isTrue()
                assertThat(expectHttpUpgrade(HttpMethod.Post, "websocket", ConnectionOptions.Upgrade)).isFalse()
            } finally {
                headers.release()
            }

            assertThatThrownBy {
                runBlocking {
                    parseHeaders(ByteReadChannel("Host: bad/path\r\n\r\n"))
                }
            }.isInstanceOf(ParserException::class.java)

            assertThatThrownBy {
                runBlocking {
                    parseHeaders(ByteReadChannel(": empty\r\n\r\n"))
                }
            }.isInstanceOf(ParserException::class.java)
        }
    }

    @Test
    fun encodesDecodesChunkedBodiesAndParsesChunkedHttpBody(): Unit = runBlocking {
        withTimeout(10_000) {
            val encodedOutput = ByteChannel(autoFlush = true)
            encodeChunked(encodedOutput, ByteReadChannel("Wikipedia"))
            encodedOutput.flushAndClose()
            val encoded = encodedOutput.toByteArray().decodeToString()

            assertThat(encoded).isEqualTo("9\r\nWikipedia\r\n0\r\n\r\n")

            val decodedOutput = ByteChannel(autoFlush = true)
            decodeChunked(ByteReadChannel("4\r\nWiki\r\n5\r\npedia\r\n0\r\n\r\n"), decodedOutput)
            assertThat(decodedOutput.toByteArray().decodeToString()).isEqualTo("Wikipedia")

            val parsedBody = ByteChannel(autoFlush = true)
            parseHttpBody(
                HttpProtocolVersion.HTTP_1_1,
                contentLength = -1,
                transferEncoding = "identity, chunked",
                connectionOptions = ConnectionOptions.KeepAlive,
                input = ByteReadChannel("5\r\nhello\r\n0\r\n\r\n"),
                out = parsedBody
            )
            assertThat(parsedBody.toByteArray().decodeToString()).isEqualTo("hello")

            assertThatThrownBy {
                runBlocking {
                    decodeChunked(ByteReadChannel("1\r\na\r\nnot-a-size\r\n"), ByteChannel(autoFlush = true))
                }
            }.isInstanceOf(NumberFormatException::class.java)
        }
    }

    @Test
    fun parsesMultipartEventsWithPreambleHeadersFilePartAndEpilogue(): Unit = runBlocking {
        withTimeout(10_000) {
            val boundary = "NativeBoundary"
            val body = listOf(
                "POST /upload HTTP/1.1",
                "Host: upload.example",
                "Content-Type: multipart/form-data; boundary=$boundary",
                "Connection: close",
                "",
                "preamble text",
                "--$boundary",
                "Content-Disposition: form-data; name=\"field\"",
                "X-Part: one",
                "",
                "field value",
                "--$boundary",
                "Content-Disposition: form-data; name=\"file\"; filename=\"notes.txt\"",
                "Content-Type: text/plain",
                "Content-Length: 11",
                "",
                "hello file!",
                "--$boundary--",
                "epilogue text"
            ).joinToString("\r\n")

            val input = ByteReadChannel(body)
            val request = parseRequest(input) ?: error("Expected multipart request")
            try {
                val events = parseMultipart(input, request.headers)

                val preamble = events.receive() as MultipartEvent.Preamble
                try {
                    assertThat(preamble.body.readText()).isEqualTo("preamble text\r\n")
                } finally {
                    preamble.release()
                }

                val field = events.receive() as MultipartEvent.MultipartPart
                val fieldHeaders = field.headers.await()
                try {
                    assertThat(fieldHeaders["Content-Disposition"].toString())
                        .isEqualTo("form-data; name=\"field\"")
                    assertThat(fieldHeaders["x-part"].toString()).isEqualTo("one")
                    assertThat(field.body.readRemaining().readText()).isEqualTo("field value")
                } finally {
                    fieldHeaders.release()
                    field.release()
                }

                val file = events.receive() as MultipartEvent.MultipartPart
                val fileHeaders = file.headers.await()
                try {
                    val headers = CIOHeaders(fileHeaders)
                    assertThat(headers.get("content-type")).isEqualTo("text/plain")
                    assertThat(headers.get("content-length")).isEqualTo("11")
                    assertThat(file.body.readRemaining().readText()).isEqualTo("hello file!")
                } finally {
                    fileHeaders.release()
                    file.release()
                }

                val epilogue = events.receive() as MultipartEvent.Epilogue
                try {
                    assertThat(epilogue.body.readText()).isEqualTo("epilogue text")
                } finally {
                    epilogue.release()
                }

                assertThat(events.receiveCatching().isClosed).isTrue()
            } finally {
                request.release()
            }
        }
    }

    @Test
    fun exposesParsedHeadersThroughIndexedAndOffsetAccessors(): Unit = runBlocking {
        withTimeout(10_000) {
            val headers = parseHeaders(
                ByteReadChannel(
                    "Alpha: one\r\n" +
                        "Beta: two\r\n" +
                        "Alpha: three\r\n" +
                        "\r\n"
                )
            )
            try {
                val indexedPairs: List<String> = (0 until headers.size)
                    .map { "${headers.nameAt(it)}: ${headers.valueAt(it)}" }
                val offsets: List<Int> = headers.offsets().toList()
                val offsetPairs: List<String> = offsets
                    .map { "${headers.nameAtOffset(it)}: ${headers.valueAtOffset(it)}" }

                assertThat(offsets).hasSize(headers.size)
                assertThat(indexedPairs).containsExactlyInAnyOrder("Alpha: one", "Beta: two", "Alpha: three")
                assertThat(offsetPairs).containsExactlyElementsOf(indexedPairs)
            } finally {
                headers.release()
            }
        }
    }

    @Test
    fun parsesBodyDelimitedByConnectionClose(): Unit = runBlocking {
        withTimeout(10_000) {
            val output = ByteChannel(autoFlush = true)
            parseHttpBody(
                HttpProtocolVersion.HTTP_1_1,
                contentLength = -1,
                transferEncoding = null,
                connectionOptions = ConnectionOptions.Close,
                input = ByteReadChannel("streamed until close"),
                out = output
            )
            output.flushAndClose()

            assertThat(output.toByteArray().decodeToString()).isEqualTo("streamed until close")
        }
    }

    @Test
    fun rejectsMalformedRequestsResponsesAndUnsupportedBodyEncodings(): Unit = runBlocking {
        withTimeout(10_000) {
            assertThat(parseRequest(ByteReadChannel("\r\n\r\n"))).isNull()
            assertThat(parseResponse(ByteReadChannel(""))).isNull()

            assertThatThrownBy {
                runBlocking {
                    parseRequest(ByteReadChannel("GET / HTTP/2.0\r\n\r\n"))
                }
            }.isInstanceOf(ParserException::class.java)
                .hasMessageContaining("Unsupported HTTP version")

            assertThatThrownBy {
                runBlocking {
                    parseResponse(ByteReadChannel("HTTP/1.1 99 TooLow\r\n\r\n"))
                }
            }.isInstanceOf(ParserException::class.java)
                .hasMessageContaining("Status-code must be 3-digit")

            assertThatThrownBy {
                expectHttpBody(HttpMethod.Post, -1, "gzip", null, "text/plain")
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unsupported transfer encoding")

            val noLengthOutput = ByteChannel(autoFlush = true)
            parseHttpBody(
                HttpProtocolVersion.HTTP_1_1,
                contentLength = -1,
                transferEncoding = null,
                connectionOptions = ConnectionOptions.KeepAlive,
                input = ByteReadChannel("body"),
                out = noLengthOutput
            )
            assertThat(noLengthOutput.closedCause)
                .hasMessageContaining("Failed to parse request body")
        }
    }
}
