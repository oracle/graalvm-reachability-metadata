/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.ConnectionOptions
import io.ktor.http.cio.Request
import io.ktor.http.cio.RequestResponseBuilder
import io.ktor.http.cio.expectHttpBody
import io.ktor.http.cio.parseHttpBody
import io.ktor.http.cio.parseRequest
import io.ktor.http.contentType
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writePacket
import java.util.zip.CRC32
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO

/**
 * Covers `Expect: 100-continue` upload negotiation: an upload gateway built on the CIO parser
 * answers the request head with an interim `100 Continue` before reading the body, refuses an
 * over-quota upload with a final status before any body byte is read, and answers `417` on a
 * route without expectation support so the CIO client resends the request without `Expect`.
 */
class ExpectContinueUploadTest {

    /** What the gateway did with one request it parsed. */
    private data class Handled(
        val requestLine: String,
        val expect: String?,
        val sentContinue: Boolean,
        val bodyBytesRead: Int,
        val status: Int,
    )

    /** One response of the gateway: its status, body, and whether the connection stays open. */
    private data class Reply(val status: HttpStatusCode, val body: String, val keepAlive: Boolean)

    /**
     * An HTTP/1.1 upload gateway: `/uploads/` takes bodies up to [QUOTA] bytes and honours
     * `Expect: 100-continue`; `/archive` is a legacy route that rejects every expectation.
     */
    private class UploadGateway(private val server: ServerSocket, scope: CoroutineScope) {

        val handled: Channel<Handled> = Channel(Channel.UNLIMITED)

        val port: Int = (server.localAddress as InetSocketAddress).port

        private val acceptor: Job = scope.launch {
            while (true) {
                val socket: Socket = server.accept()
                launch { serve(socket) }
            }
        }

        fun stop() {
            acceptor.cancel()
            server.close()
        }

        private suspend fun serve(socket: Socket) {
            val input: ByteReadChannel = socket.openReadChannel()
            val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)
            try {
                while (true) {
                    val request: Request = parseRequest(input) ?: break
                    val keepAlive: Boolean = request.use { handle(it, input, output) }
                    if (!keepAlive) {
                        break
                    }
                }
            } finally {
                output.flushAndClose()
                socket.close()
            }
        }

        private suspend fun handle(request: Request, input: ByteReadChannel, output: ByteWriteChannel): Boolean {
            val path: String = request.uri.toString()
            val expect: String? = request.headers[HttpHeaders.Expect]?.toString()
            val declaredLength: Long = request.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
            val requestLine = "${request.method.value} $path"
            var sentContinue = false
            var bodyBytesRead = 0

            val reply: Reply = when {
                expect != null && path == "/archive" ->
                    Reply(HttpStatusCode.ExpectationFailed, "", keepAlive = true)
                expect != null && !expect.equals("100-continue", ignoreCase = true) ->
                    Reply(HttpStatusCode.ExpectationFailed, "", keepAlive = true)
                path.startsWith("/uploads/") && declaredLength > QUOTA ->
                    Reply(HttpStatusCode.PayloadTooLarge, "upload of $declaredLength bytes exceeds $QUOTA", false)
                else -> {
                    if (expect != null) {
                        writeHead(output, HttpStatusCode.Continue, emptyList())
                        output.flush()
                        sentContinue = true
                    }
                    val body: ByteArray = if (expectHttpBody(request)) readBody(request, input) else ByteArray(0)
                    bodyBytesRead = body.size
                    if (path == "/archive") {
                        Reply(HttpStatusCode.OK, "archived ${body.size} bytes", keepAlive = true)
                    } else {
                        Reply(HttpStatusCode.Created, "stored ${body.size} bytes crc32=${crc32(body)}", true)
                    }
                }
            }

            val bytes: ByteArray = reply.body.encodeToByteArray()
            val headers: MutableList<Pair<String, String>> = mutableListOf(
                HttpHeaders.ContentType to "text/plain",
                HttpHeaders.ContentLength to bytes.size.toString(),
            )
            if (!reply.keepAlive) {
                headers += HttpHeaders.Connection to "close"
            }
            writeHead(output, reply.status, headers, bytes)
            output.flush()
            handled.send(Handled(requestLine, expect, sentContinue, bodyBytesRead, reply.status.value))
            return reply.keepAlive
        }

        private suspend fun writeHead(
            output: ByteWriteChannel,
            status: HttpStatusCode,
            headers: List<Pair<String, String>>,
            body: ByteArray = ByteArray(0),
        ) {
            val head = RequestResponseBuilder()
            try {
                head.responseLine("HTTP/1.1", status.value, status.description)
                for ((name: String, value: String) in headers) {
                    head.headerLine(name, value)
                }
                head.emptyLine()
                head.bytes(body, 0, body.size)
                output.writePacket(head.build())
            } finally {
                head.release()
            }
        }

        private suspend fun readBody(request: Request, input: ByteReadChannel): ByteArray = coroutineScope {
            val sink = ByteChannel()
            val collected = async { sink.readRemaining().readByteArray() }
            try {
                parseHttpBody(
                    HttpProtocolVersion.parse(request.version),
                    request.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L,
                    request.headers[HttpHeaders.TransferEncoding],
                    ConnectionOptions.parse(request.headers[HttpHeaders.Connection]),
                    input,
                    sink,
                )
            } finally {
                sink.flushAndClose()
            }
            collected.await()
        }
    }

    @Test
    fun uploadWithinQuotaIsInvitedWithContinueBeforeItsBodyIsRead(): Unit = runBlocking {
        withGateway { gateway: UploadGateway, client: HttpClient ->
            val report: ByteArray = payload(3_000)
            val response: HttpResponse = client.post("http://127.0.0.1:${gateway.port}/uploads/report.bin") {
                header(HttpHeaders.Expect, "100-continue")
                contentType(ContentType.Application.OctetStream)
                setBody(report)
            }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("stored 3000 bytes crc32=${crc32(report)}", response.bodyAsText())
            assertEquals(
                Handled("POST /uploads/report.bin", "100-continue", sentContinue = true, 3_000, 201),
                gateway.handled.receive(),
            )
        }
    }

    @Test
    fun overQuotaUploadIsRefusedWithoutReadingItsBody(): Unit = runBlocking {
        withGateway { gateway: UploadGateway, client: HttpClient ->
            val tooLarge: HttpResponse = client.post("http://127.0.0.1:${gateway.port}/uploads/backup.tar") {
                header(HttpHeaders.Expect, "100-continue")
                contentType(ContentType.Application.OctetStream)
                setBody(payload(QUOTA + 1))
            }

            assertEquals(HttpStatusCode.PayloadTooLarge, tooLarge.status)
            assertEquals("close", tooLarge.headers[HttpHeaders.Connection])
            assertEquals("upload of ${QUOTA + 1} bytes exceeds $QUOTA", tooLarge.bodyAsText())
            assertEquals(
                Handled("POST /uploads/backup.tar", "100-continue", sentContinue = false, 0, 413),
                gateway.handled.receive(),
            )

            val small: ByteArray = payload(512)
            val retry: HttpResponse = client.post("http://127.0.0.1:${gateway.port}/uploads/backup-index.txt") {
                header(HttpHeaders.Expect, "100-continue")
                contentType(ContentType.Application.OctetStream)
                setBody(small)
            }

            assertEquals(HttpStatusCode.Created, retry.status)
            assertEquals("stored 512 bytes crc32=${crc32(small)}", retry.bodyAsText())
            assertEquals(
                Handled("POST /uploads/backup-index.txt", "100-continue", sentContinue = true, 512, 201),
                gateway.handled.receive(),
            )
        }
    }

    @Test
    fun rejectedExpectationIsRetriedWithoutExpectOnTheLegacyRoute(): Unit = runBlocking {
        withGateway { gateway: UploadGateway, client: HttpClient ->
            val ledger: ByteArray = payload(1_024)
            val response: HttpResponse = client.post("http://127.0.0.1:${gateway.port}/archive") {
                header(HttpHeaders.Expect, "100-continue")
                contentType(ContentType.Application.OctetStream)
                setBody(ledger)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("archived 1024 bytes", response.bodyAsText())
            assertEquals(
                Handled("POST /archive", "100-continue", sentContinue = false, 0, 417),
                gateway.handled.receive(),
            )
            assertEquals(
                Handled("POST /archive", null, sentContinue = false, 1_024, 200),
                gateway.handled.receive(),
            )
        }
    }

    private companion object {

        /** The largest upload body the gateway accepts, in bytes. */
        const val QUOTA: Long = 4_096L

        fun payload(size: Int): ByteArray = ByteArray(size) { index: Int -> (index * 31 % 251).toByte() }

        fun payload(size: Long): ByteArray = payload(size.toInt())

        fun crc32(bytes: ByteArray): String {
            val crc = CRC32()
            crc.update(bytes)
            return crc.value.toString(16)
        }

        suspend fun withGateway(block: suspend (UploadGateway, HttpClient) -> Unit) {
            val selector = SelectorManager(Dispatchers.IO)
            try {
                withTimeout(30_000) {
                    val gateway = UploadGateway(aSocket(selector).tcp().bind("127.0.0.1", 0), this)
                    val client = HttpClient(ClientCIO)
                    try {
                        block(gateway, client)
                    } finally {
                        client.close()
                        gateway.stop()
                    }
                }
            } finally {
                selector.close()
            }
        }
    }
}
