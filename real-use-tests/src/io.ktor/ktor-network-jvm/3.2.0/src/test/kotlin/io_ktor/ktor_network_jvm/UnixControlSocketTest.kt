/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.UnixSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Controls a local daemon over Unix domain sockets bound and connected through a ktor selector
 * with [UnixSocketAddress].
 *
 * The daemon listens on a socket file, as Docker or systemd services do, and speaks
 * length-prefixed frames: a CLI connects for each command and reads back one framed reply.
 */
public class UnixControlSocketTest {

    /** A daemon whose configuration generation is bumped by `RELOAD` and read by `STATUS`. */
    private class Daemon(private val serverSocket: ServerSocket) {
        private val generation: AtomicInteger = AtomicInteger(1)
        private val handled: AtomicInteger = AtomicInteger(0)

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
                val command: String = readFrame(input)
                val reply: String = when (command) {
                    "STATUS" -> "running generation=${generation.get()} handled=${handled.get()}"
                    "RELOAD" -> "reloaded generation=${generation.incrementAndGet()}"
                    else -> "error unknown command '$command'"
                }
                handled.incrementAndGet()
                writeFrame(output, reply)
            }
        }
    }

    /** A CLI that opens a fresh connection to the daemon socket for every command. */
    private class ControlClient(
        private val selector: SelectorManager,
        private val address: UnixSocketAddress,
    ) {
        suspend fun call(command: String): String {
            val socket: Socket = aSocket(selector).tcp().connect(address)
            socket.use { connected: Socket ->
                assertThat(connected.remoteAddress).isEqualTo(address)
                writeFrame(connected.openWriteChannel(autoFlush = true), command)
                return readFrame(connected.openReadChannel())
            }
        }
    }

    @Test
    public fun cliControlsTheDaemonThroughItsSocketFile(): Unit = runBlocking {
        withTimeout(30_000) {
            assertThat(UnixSocketAddress.isSupported()).isTrue()
            val runtimeDir: Path = Files.createTempDirectory("ktor-uds")
            val socketFile: Path = runtimeDir.resolve("daemon.sock")
            val address: UnixSocketAddress = UnixSocketAddress(socketFile.toString())
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket: ServerSocket = aSocket(selector).tcp().bind(address)
            val daemonJob: Job = Daemon(serverSocket).start(CoroutineScope(Dispatchers.IO))
            try {
                val bound: UnixSocketAddress = serverSocket.localAddress as UnixSocketAddress
                assertThat(bound.path).isEqualTo(socketFile.toString())
                assertThat(Files.exists(socketFile)).isTrue()

                val cli: ControlClient = ControlClient(selector, address)
                assertThat(cli.call("STATUS")).isEqualTo("running generation=1 handled=0")
                assertThat(cli.call("RELOAD")).isEqualTo("reloaded generation=2")
                assertThat(cli.call("RELOAD")).isEqualTo("reloaded generation=3")
                assertThat(cli.call("SHUTDOWN")).isEqualTo("error unknown command 'SHUTDOWN'")
                assertThat(cli.call("STATUS")).isEqualTo("running generation=3 handled=4")
            } finally {
                serverSocket.close()
                daemonJob.cancelAndJoin()
                selector.close()
                Files.deleteIfExists(socketFile)
                Files.deleteIfExists(runtimeDir)
            }
        }
    }

    private companion object {
        /** Writes one frame: a big-endian length followed by the UTF-8 bytes. */
        suspend fun writeFrame(output: ByteWriteChannel, text: String) {
            val bytes: ByteArray = text.encodeToByteArray()
            output.writeInt(bytes.size)
            output.writeByteArray(bytes)
            output.flush()
        }

        /** Reads one frame written by [writeFrame]. */
        suspend fun readFrame(input: ByteReadChannel): String {
            val length: Int = input.readInt()
            return input.readByteArray(length).decodeToString()
        }
    }
}
