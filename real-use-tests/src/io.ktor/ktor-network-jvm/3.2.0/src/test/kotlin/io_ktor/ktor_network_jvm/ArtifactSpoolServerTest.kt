/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.util.DefaultByteBufferPool
import io.ktor.network.util.DefaultDatagramByteBufferPool
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.pool.useInstance
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32

/**
 * Streams artifacts between ktor sockets and NIO file channels through the shared network
 * buffer pools, `DefaultByteBufferPool` for TCP transfers and `DefaultDatagramByteBufferPool`
 * for UDP announcements.
 *
 * Like a build cache node, the server spools each uploaded artifact to disk chunk by chunk,
 * announces it to a subscriber with a datagram that carries a preview of its first bytes, and
 * serves it back to any client that asks for it.
 */
public class ArtifactSpoolServerTest {

    /** What a subscriber learns from one announcement datagram. */
    private data class Announcement(val header: String, val preview: ByteArray)

    /**
     * A spool speaking `PUT name size` followed by `size` raw bytes, `GET name` answered with
     * `SIZE n` and the bytes, and `QUIT`.
     */
    private class SpoolServer(
        private val serverSocket: ServerSocket,
        private val announcer: BoundDatagramSocket,
        private val subscriber: SocketAddress,
        private val spoolDirectory: Path,
    ) {
        val port: Int
            get() = (serverSocket.localAddress as InetSocketAddress).port

        fun start(scope: CoroutineScope): Job = scope.launch {
            while (isActive) {
                val connection: Socket = serverSocket.accept()
                launch { serve(connection) }
            }
        }

        private suspend fun serve(connection: Socket) {
            connection.use { socket: Socket ->
                val input: ByteReadChannel = socket.openReadChannel()
                val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
                while (true) {
                    val parts: List<String> = (input.readUTF8Line() ?: break).split(' ')
                    when (parts[0]) {
                        "PUT" -> {
                            val name: String = parts[1]
                            val size: Long = parts[2].toLong()
                            val crc: Long = spool(input, name, size)
                            announce(name, size, crc)
                            output.writeStringUtf8("STORED $name $size $crc\n")
                        }
                        "GET" -> serveDownload(output, parts[1])
                        "QUIT" -> {
                            output.writeStringUtf8("BYE\n")
                            break
                        }
                        else -> output.writeStringUtf8("ERROR unknown command ${parts[0]}\n")
                    }
                }
            }
        }

        /** Copies `size` socket bytes into the spool file through one pooled buffer. */
        private suspend fun spool(input: ByteReadChannel, name: String, size: Long): Long {
            val crc: CRC32 = CRC32()
            FileChannel.open(spoolDirectory.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                .use { file: FileChannel ->
                    DefaultByteBufferPool.useInstance { buffer: ByteBuffer ->
                        var remaining: Long = size
                        while (remaining > 0) {
                            buffer.clear()
                            buffer.limit(minOf(remaining, buffer.capacity().toLong()).toInt())
                            input.readFully(buffer)
                            buffer.flip()
                            remaining -= buffer.remaining()
                            crc.update(buffer.duplicate())
                            while (buffer.hasRemaining()) {
                                file.write(buffer)
                            }
                        }
                    }
                }
            return crc.value
        }

        /** Sends the subscriber a header line and the artifact's first bytes in one datagram. */
        private suspend fun announce(name: String, size: Long, crc: Long) {
            FileChannel.open(spoolDirectory.resolve(name), StandardOpenOption.READ).use { file: FileChannel ->
                DefaultDatagramByteBufferPool.useInstance { buffer: ByteBuffer ->
                    buffer.clear()
                    buffer.put("ARTIFACT $name $size $crc\n".toByteArray(Charsets.US_ASCII))
                    buffer.limit(buffer.position() + minOf(size, PREVIEW_BYTES.toLong()).toInt())
                    var position: Long = 0
                    while (buffer.hasRemaining()) {
                        position += file.read(buffer, position)
                    }
                    buffer.flip()
                    announcer.send(Datagram(buildPacket { writeFully(buffer) }, subscriber))
                }
            }
        }

        /** Streams a spooled artifact back to the client through one pooled buffer. */
        private suspend fun serveDownload(output: ByteWriteChannel, name: String) {
            val path: Path = spoolDirectory.resolve(name)
            if (!Files.exists(path)) {
                output.writeStringUtf8("MISSING $name\n")
                return
            }
            FileChannel.open(path, StandardOpenOption.READ).use { file: FileChannel ->
                output.writeStringUtf8("SIZE ${file.size()}\n")
                DefaultByteBufferPool.useInstance { buffer: ByteBuffer ->
                    while (true) {
                        buffer.clear()
                        if (file.read(buffer) < 0) {
                            break
                        }
                        buffer.flip()
                        output.writeFully(buffer)
                    }
                }
            }
        }
    }

    /** One build agent talking to the spool. */
    private class SpoolClient(val socket: Socket) {
        private val input: ByteReadChannel = socket.openReadChannel()
        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

        suspend fun put(name: String, content: ByteArray): String? {
            output.writeStringUtf8("PUT $name ${content.size}\n")
            output.writeFully(content)
            return input.readUTF8Line()
        }

        suspend fun get(name: String): ByteArray? {
            output.writeStringUtf8("GET $name\n")
            val status: String = input.readUTF8Line() ?: return null
            if (!status.startsWith("SIZE ")) {
                return null
            }
            return input.readByteArray(status.removePrefix("SIZE ").toInt())
        }

        suspend fun quit(): String? {
            output.writeStringUtf8("QUIT\n")
            return input.readUTF8Line()
        }
    }

    @Test
    fun spoolsAnnouncesAndServesArtifactsThroughPooledBuffers(@TempDir spoolDirectory: Path): Unit = runBlocking {
        val artifacts: Map<String, ByteArray> = mapOf(
            "app.jar" to ByteArray(100_000) { index: Int -> (index * 31 % 251).toByte() },
            "lib.jar" to ByteArray(4_096 * 3) { index: Int -> (index % 7 + index / 4_096).toByte() },
            "manifest.txt" to "Main-Class: org.example.Main\nBuild: 42\n".toByteArray(Charsets.US_ASCII),
        )
        SelectorManager(Dispatchers.IO).use { selector: SelectorManager ->
            val subscriber: BoundDatagramSocket = aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0))
            val announcer: BoundDatagramSocket = aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0))
            val serverSocket: ServerSocket = aSocket(selector).tcp().bind("127.0.0.1", 0)
            val server: SpoolServer = SpoolServer(serverSocket, announcer, subscriber.localAddress, spoolDirectory)
            val serverJob: Job = server.start(CoroutineScope(Dispatchers.IO))
            try {
                withTimeout(30_000L) {
                    val uploads: List<Deferred<String?>> = artifacts.map { (name: String, content: ByteArray) ->
                        async(Dispatchers.IO) {
                            val agent: SpoolClient = SpoolClient(aSocket(selector).tcp().connect("127.0.0.1", server.port))
                            agent.socket.use {
                                val reply: String? = agent.put(name, content)
                                assertThat(agent.quit()).isEqualTo("BYE")
                                reply
                            }
                        }
                    }
                    assertThat(uploads.awaitAll()).containsExactlyElementsOf(
                        artifacts.map { (name: String, content: ByteArray) ->
                            "STORED $name ${content.size} ${crcOf(content)}"
                        },
                    )

                    val announcements: Map<String, Announcement> = (1..artifacts.size).associate {
                        val announcement: Announcement = parseAnnouncement(subscriber.receive())
                        announcement.header.split(' ')[1] to announcement
                    }
                    artifacts.forEach { (name: String, content: ByteArray) ->
                        val announcement: Announcement = announcements.getValue(name)
                        assertThat(announcement.header).isEqualTo("ARTIFACT $name ${content.size} ${crcOf(content)}")
                        assertThat(announcement.preview).isEqualTo(content.copyOf(minOf(content.size, PREVIEW_BYTES)))
                        assertThat(Files.readAllBytes(spoolDirectory.resolve(name))).isEqualTo(content)
                    }

                    val consumer: SpoolClient = SpoolClient(aSocket(selector).tcp().connect("127.0.0.1", server.port))
                    consumer.socket.use {
                        artifacts.forEach { (name: String, content: ByteArray) ->
                            assertThat(consumer.get(name)).isEqualTo(content)
                        }
                        assertThat(consumer.get("missing.jar")).isNull()
                        assertThat(consumer.quit()).isEqualTo("BYE")
                    }
                }
            } finally {
                serverJob.cancelAndJoin()
                serverSocket.close()
                announcer.close()
                subscriber.close()
            }
        }
    }

    private companion object {
        /** How many leading artifact bytes an announcement carries. */
        const val PREVIEW_BYTES: Int = 1_024

        fun crcOf(content: ByteArray): Long = CRC32().apply { update(content) }.value

        fun parseAnnouncement(datagram: Datagram): Announcement {
            val bytes: ByteArray = datagram.packet.readByteArray()
            val newline: Int = bytes.indexOf('\n'.code.toByte())
            return Announcement(
                header = String(bytes, 0, newline, Charsets.US_ASCII),
                preview = bytes.copyOfRange(newline + 1, bytes.size),
            )
        }
    }
}
