/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.port
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32

/**
 * Exercises TCP write backpressure through the ktor selector: a socket write channel whose
 * producer suspends once the channel and kernel buffers fill while the peer is not reading,
 * and resumes as the peer drains them.
 *
 * A telemetry agent uploads a large batch to an ingest server whose storage is not ready yet.
 * The server accepts the upload header and then stops reading, so the agent's upload stalls
 * far short of the batch size; meanwhile the same selector keeps answering status queries on
 * other connections. Once storage is ready the server drains the stream and acknowledges the
 * exact byte count and checksum the agent sent.
 */
public class TcpUploadBackpressureTest {

    /** An ingest server that stores `UPLOAD <name> <length>` streams once its storage is ready. */
    private class IngestServer(private val serverSocket: ServerSocket) {
        val storageReady: CompletableDeferred<Unit> = CompletableDeferred()
        val pendingUploads: AtomicInteger = AtomicInteger()
        val bytesStored: AtomicLong = AtomicLong()

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
                val header: List<String> = (input.readUTF8Line() ?: return).split(' ')
                when (header[0]) {
                    "STATUS" -> output.writeStringUtf8("PENDING ${pendingUploads.get()} STORED ${bytesStored.get()}\n")
                    "UPLOAD" -> {
                        val name: String = header[1]
                        val length: Long = header[2].toLong()
                        pendingUploads.incrementAndGet()
                        storageReady.await()
                        val checksum: CRC32 = CRC32()
                        val buffer: ByteArray = ByteArray(16 * 1024)
                        var remaining: Long = length
                        while (remaining > 0) {
                            val wanted: Int = minOf(buffer.size.toLong(), remaining).toInt()
                            val read: Int = input.readAvailable(buffer, 0, wanted)
                            check(read >= 0) { "Upload $name ended $remaining bytes early" }
                            checksum.update(buffer, 0, read)
                            bytesStored.addAndGet(read.toLong())
                            remaining -= read
                        }
                        pendingUploads.decrementAndGet()
                        output.writeStringUtf8("STORED $name $length ${checksum.value.toString(16)}\n")
                    }
                    else -> output.writeStringUtf8("ERROR unknown command ${header[0]}\n")
                }
            }
        }
    }

    /** The agent side of one upload: what it handed to the socket and the checksum it computed. */
    private class Upload(private val socket: Socket, private val name: String, private val length: Long) {
        val handedOff: AtomicLong = AtomicLong()
        val checksum: CRC32 = CRC32()

        suspend fun run(): String {
            val input: ByteReadChannel = socket.openReadChannel()
            val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)
            output.writeStringUtf8("UPLOAD $name $length\n")
            output.flush()
            val chunk: ByteArray = ByteArray(CHUNK_SIZE)
            var chunkIndex: Int = 0
            while (handedOff.get() < length) {
                for (i: Int in chunk.indices) {
                    chunk[i] = ((chunkIndex * 31 + i) and 0xFF).toByte()
                }
                output.writeFully(chunk)
                checksum.update(chunk)
                handedOff.addAndGet(chunk.size.toLong())
                chunkIndex++
            }
            output.flush()
            return checkNotNull(input.readUTF8Line()) { "Ingest server closed the upload of $name" }
        }
    }

    private suspend fun query(selector: SelectorManager, port: Int, command: String): String {
        aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port)).use { socket: Socket ->
            val input: ByteReadChannel = socket.openReadChannel()
            val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
            output.writeStringUtf8("$command\n")
            return checkNotNull(input.readUTF8Line()) { "No reply to $command" }
        }
    }

    /** Polls [condition]; the enclosing `withTimeout` bounds the wait. */
    private suspend fun awaitUntil(condition: () -> Boolean) {
        while (!condition()) {
            delay(10)
        }
    }

    @Test
    public fun uploadStallsWhileServerIsNotReadingAndCompletesOnceDrained(): Unit = runBlocking {
        withTimeout(60_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val serverScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val serverSocket: ServerSocket = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            val server: IngestServer = IngestServer(serverSocket)
            server.start(serverScope)
            try {
                val port: Int = serverSocket.port
                val socket: Socket = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
                val upload: Upload = Upload(socket, "telemetry-batch.bin", BATCH_SIZE)
                val reply: Deferred<String> = async(Dispatchers.IO) { socket.use { upload.run() } }

                awaitUntil { server.pendingUploads.get() == 1 }
                awaitUntil { upload.handedOff.get() >= 1024 * 1024 }

                // Nothing is read on the server, so buffers bound how far the agent can get.
                assertThat(upload.handedOff.get()).isLessThan(BATCH_SIZE)
                assertThat(reply.isCompleted).isFalse()
                assertThat(server.bytesStored.get()).isZero()
                assertThat(query(selector, port, "STATUS")).isEqualTo("PENDING 1 STORED 0")
                assertThat(query(selector, port, "PURGE")).isEqualTo("ERROR unknown command PURGE")

                server.storageReady.complete(Unit)
                val acknowledgement: String = reply.await()

                assertThat(upload.handedOff.get()).isEqualTo(BATCH_SIZE)
                assertThat(acknowledgement)
                    .isEqualTo("STORED telemetry-batch.bin $BATCH_SIZE ${upload.checksum.value.toString(16)}")
                assertThat(server.bytesStored.get()).isEqualTo(BATCH_SIZE)
                assertThat(query(selector, port, "STATUS")).isEqualTo("PENDING 0 STORED $BATCH_SIZE")
            } finally {
                serverSocket.close()
                serverScope.cancel()
                selector.close()
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE: Int = 64 * 1024

        /** Far more than the write channel and both kernel socket buffers can hold together. */
        const val BATCH_SIZE: Long = 64L * 1024 * 1024
    }
}
