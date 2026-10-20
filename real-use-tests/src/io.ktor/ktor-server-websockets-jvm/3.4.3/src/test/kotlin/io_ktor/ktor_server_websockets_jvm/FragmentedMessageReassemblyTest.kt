/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.cio.webSocketRaw
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameType
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers message reassembly in the plugin's default sessions: a document-sync endpoint receives
 * documents an editor streams as fragments, with a keep-alive ping between them, and its handler
 * reads each one as a single whole frame, while `maxFrameSize` bounds the reassembled message.
 *
 * The editor talks over a raw client session, so it controls every fragment and every control
 * frame on the wire and sees the server's fragmented export exactly as it is sent.
 */
class FragmentedMessageReassemblyTest {

    /** One whole data frame as the sync handler received it. */
    private data class ReceivedFrame(val type: FrameType, val fin: Boolean, val size: Int)

    /** What the sync handler saw during one session; read it once [finished] completes. */
    private class SyncLog {

        val frames: MutableList<ReceivedFrame> = mutableListOf()

        val documents: MutableMap<String, String> = linkedMapOf()

        val attachmentSizes: MutableList<Int> = mutableListOf()

        val finished: CompletableDeferred<Unit> = CompletableDeferred()
    }

    @Test
    fun handsTheHandlerEachFragmentedDocumentAsOneFrame(): Unit = withSyncServer { port: Int, log: SyncLog ->
        val client: HttpClient = rawClient()
        val document: ByteArray = NOTES_DOCUMENT.encodeToByteArray()
        val euroStart: Int = document.indexOf(EURO_FIRST_BYTE)
        val textFragments: List<ByteArray> = split(document, euroStart + 1, euroStart + 120)
        val attachment = ByteArray(ATTACHMENT_SIZE) { index: Int -> (index % 251).toByte() }
        val attachmentFragments: List<ByteArray> = split(attachment, 300, 610)

        var pongPayload: ByteArray? = null
        val replies: MutableList<String> = mutableListOf()
        val exportFinFlags: MutableList<Boolean> = mutableListOf()
        val exportTypes: MutableList<FrameType> = mutableListOf()
        val exported = ByteArrayOutputStream()
        var serverClose: CloseReason? = null

        client.webSocketRaw(host = "127.0.0.1", port = port, path = "/sync") {
            send(Frame.Text(false, textFragments[0]))
            send(Frame.Ping("still-typing".encodeToByteArray()))
            val pong: Frame = incoming.receive()
            assertEquals(FrameType.PONG, pong.frameType)
            pongPayload = pong.data
            send(Frame.Text(false, textFragments[1]))
            send(Frame.Text(true, textFragments[2]))
            replies += (incoming.receive() as Frame.Text).readText()

            send(Frame.Binary(false, attachmentFragments[0]))
            send(Frame.Binary(false, attachmentFragments[1]))
            send(Frame.Binary(true, attachmentFragments[2]))
            replies += (incoming.receive() as Frame.Text).readText()

            send(Frame.Text("export notes.md"))
            do {
                val fragment: Frame = incoming.receive()
                exportTypes += fragment.frameType
                exportFinFlags += fragment.fin
                exported.write(fragment.data)
            } while (!fragment.fin)

            send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "editor closed")))
            serverClose = (incoming.receive() as Frame.Close).readReason()
        }
        client.close()

        assertArrayEquals("still-typing".encodeToByteArray(), pongPayload)
        assertEquals(
            listOf("saved notes.md (${NOTES_BODY.length} chars)", "saved attachment ($ATTACHMENT_SIZE bytes)"),
            replies,
        )
        assertEquals(listOf(FrameType.TEXT, FrameType.TEXT, FrameType.TEXT), exportTypes)
        assertEquals(listOf(false, false, true), exportFinFlags)
        assertEquals(NOTES_BODY, exported.toByteArray().decodeToString())
        assertEquals(CloseReason.Codes.NORMAL, serverClose?.knownReason)

        withTimeout(AWAIT_MILLIS) { log.finished.await() }
        assertEquals(
            listOf(
                ReceivedFrame(FrameType.TEXT, true, document.size),
                ReceivedFrame(FrameType.BINARY, true, ATTACHMENT_SIZE),
                ReceivedFrame(FrameType.TEXT, true, "export notes.md".length),
            ),
            log.frames,
        )
        assertEquals(mapOf("notes.md" to NOTES_BODY), log.documents)
        assertEquals(listOf(ATTACHMENT_SIZE), log.attachmentSizes)
    }

    @Test
    fun closesWithTooBigOnceFragmentsOutgrowTheFrameLimit(): Unit = withSyncServer { port: Int, log: SyncLog ->
        val client: HttpClient = rawClient()
        val draft: ByteArray = "draft.md\n${"d".repeat(DRAFT_BODY_LENGTH)}".encodeToByteArray()
        val draftFragments: List<ByteArray> = split(draft, draft.size / 2)
        val oversizedFragment: ByteArray = "o".repeat(OVERSIZED_FRAGMENT_SIZE).encodeToByteArray()
        var draftReply: String? = null
        var tooBig: CloseReason? = null

        client.webSocketRaw(host = "127.0.0.1", port = port, path = "/sync") {
            send(Frame.Text(false, draftFragments[0]))
            send(Frame.Text(true, draftFragments[1]))
            draftReply = (incoming.receive() as Frame.Text).readText()

            send(Frame.Text(false, "huge.md\n".encodeToByteArray() + oversizedFragment))
            send(Frame.Text(false, oversizedFragment))
            tooBig = (incoming.receive() as Frame.Close).readReason()
        }
        client.close()

        val reassembledSize: Int = "huge.md\n".length + 2 * OVERSIZED_FRAGMENT_SIZE
        assertTrue(OVERSIZED_FRAGMENT_SIZE + "huge.md\n".length < MAX_FRAME_SIZE)
        assertEquals("saved draft.md ($DRAFT_BODY_LENGTH chars)", draftReply)
        assertEquals(CloseReason.Codes.TOO_BIG, tooBig?.knownReason)
        assertEquals("Frame is too big: $reassembledSize. Max size is $MAX_FRAME_SIZE", tooBig?.message)

        withTimeout(AWAIT_MILLIS) { log.finished.await() }
        assertEquals(listOf(ReceivedFrame(FrameType.TEXT, true, draft.size)), log.frames)
        assertEquals(listOf("draft.md"), log.documents.keys.toList())
    }

    private fun rawClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets)
    }

    /** Starts the sync server on an ephemeral port, runs [block] against it, and stops it. */
    private fun withSyncServer(block: suspend (Int, SyncLog) -> Unit) {
        val log = SyncLog()
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            documentSync(log)
        }
        server.start(wait = false)
        try {
            runBlocking {
                val port: Int = server.engine.resolvedConnectors().first().port
                block(port, log)
            }
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
        }
    }

    private fun Application.documentSync(log: SyncLog) {
        install(WebSockets) {
            maxFrameSize = MAX_FRAME_SIZE.toLong()
        }
        routing {
            webSocket("/sync") {
                try {
                    for (frame: Frame in incoming) {
                        log.frames += ReceivedFrame(frame.frameType, frame.fin, frame.data.size)
                        when (frame) {
                            is Frame.Text -> handleText(frame.readText(), log)
                            is Frame.Binary -> {
                                val bytes: ByteArray = frame.readBytes()
                                log.attachmentSizes += bytes.size
                                send(Frame.Text("saved attachment (${bytes.size} bytes)"))
                            }
                            else -> Unit
                        }
                    }
                } finally {
                    log.finished.complete(Unit)
                }
            }
        }
    }

    /** Stores a `name\nbody` document, or streams a stored one back for `export name`. */
    private suspend fun WebSocketSession.handleText(text: String, log: SyncLog) {
        if (text.startsWith("export ")) {
            val body: ByteArray = log.documents.getValue(text.removePrefix("export ")).encodeToByteArray()
            val parts: List<ByteArray> = split(body, body.size / 3, 2 * body.size / 3)
            parts.forEachIndexed { index: Int, part: ByteArray ->
                outgoing.send(Frame.Text(index == parts.lastIndex, part))
            }
            return
        }
        val name: String = text.substringBefore('\n')
        val body: String = text.substringAfter('\n')
        log.documents[name] = body
        send(Frame.Text("saved $name (${body.length} chars)"))
    }

    private companion object {

        const val AWAIT_MILLIS: Long = 10_000L

        const val STOP_TIMEOUT_MILLIS: Long = 5_000L

        const val MAX_FRAME_SIZE: Int = 1_024

        const val ATTACHMENT_SIZE: Int = 900

        const val DRAFT_BODY_LENGTH: Int = 700

        const val OVERSIZED_FRAGMENT_SIZE: Int = 600

        const val EURO_FIRST_BYTE: Byte = 0xE2.toByte()

        val NOTES_BODY: String = buildString {
            append("# Release notes\n")
            append("Seats now cost 12\u20ac each.\n")
            repeat(12) { line: Int -> append("- fixed issue ${100 + line} in the sync engine\n") }
        }

        val NOTES_DOCUMENT: String = "notes.md\n$NOTES_BODY"

        /** Cuts [bytes] at the ascending [cuts] into consecutive non-empty fragments. */
        fun split(bytes: ByteArray, vararg cuts: Int): List<ByteArray> {
            val bounds: List<Int> = listOf(0) + cuts.toList() + bytes.size
            return bounds.zipWithNext { from: Int, to: Int -> bytes.copyOfRange(from, to) }
        }
    }
}
