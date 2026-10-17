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
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.network.sockets.TypeOfService
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.port
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.BindException

/**
 * Applies TCP socket options through ktor's builder `configure` and per-socket option blocks:
 * `reusePort`, `reuseAddress` and `backlogSize` on acceptors, and `noDelay`, `keepAlive`,
 * buffer sizes, `lingerSeconds`, `typeOfService` and `socketTimeout` on client sockets.
 *
 * A service is restarted without dropping its port: the new worker binds next to the old one
 * with `SO_REUSEPORT`, the old worker stops accepting and drains its open connection, and a
 * client with a read timeout detects a backend that stalls.
 */
public class TcpSocketOptionsRollingRestartTest {

    /**
     * A line-echo worker that tags each reply with its name and stays silent on `STALL`.
     *
     * Connections are served in [connectionScope], not under the accept loop, so closing the
     * listening socket stops new connections while the open ones keep being served.
     */
    private class Worker(
        private val name: String,
        val serverSocket: ServerSocket,
        private val connectionScope: CoroutineScope,
    ) {
        fun start(): Job = connectionScope.launch {
            while (!serverSocket.isClosed) {
                val connection: Socket = try {
                    serverSocket.accept()
                } catch (closed: Exception) {
                    break
                }
                connectionScope.launch { serve(connection) }
            }
        }

        private suspend fun serve(connection: Socket) {
            connection.use { socket: Socket ->
                val input: ByteReadChannel = socket.openReadChannel()
                val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
                while (true) {
                    val line: String = input.readUTF8Line() ?: break
                    if (line != "STALL") {
                        output.writeStringUtf8("$name: $line\n")
                    }
                }
            }
        }
    }

    /** A client session that sends one line and reads one reply line. */
    private class Client(val socket: Socket) {
        private val input: ByteReadChannel = socket.openReadChannel()
        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

        suspend fun send(line: String): String? {
            output.writeStringUtf8("$line\n")
            return input.readUTF8Line()
        }
    }

    @Test
    public fun workerIsReplacedOnTheSamePortWhileOpenConnectionsDrain(): Unit = runBlocking {
        withTimeout(30_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val connectionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val clients: MutableList<Socket> = mutableListOf()
            try {
                val oldServer: ServerSocket = aSocket(selector).tcp()
                    .bind(InetSocketAddress("127.0.0.1", 0)) {
                        reuseAddress = true
                        reusePort = true
                        backlogSize = 16
                    }
                val oldWorker: Worker = Worker("v1", oldServer, connectionScope)
                val oldAcceptor: Job = oldWorker.start()
                val port: Int = oldServer.port
                assertThat(port).isPositive()

                val longLived: Client = Client(
                    aSocket(selector).tcp()
                        .configure {
                            sendBufferSize = 32 * 1024
                            receiveBufferSize = 32 * 1024
                            typeOfService = TypeOfService.IPTOS_LOWDELAY
                        }
                        .connect(InetSocketAddress("127.0.0.1", port)) {
                            noDelay = true
                            keepAlive = true
                        }
                )
                clients += longLived.socket
                assertThat(longLived.send("hello")).isEqualTo("v1: hello")

                val conflict: Throwable? = runCatching {
                    aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", port))
                }.exceptionOrNull()
                assertThat(conflict).isInstanceOf(BindException::class.java)

                val newServer: ServerSocket = aSocket(selector).tcp()
                    .bind(InetSocketAddress("127.0.0.1", port)) {
                        reuseAddress = true
                        reusePort = true
                    }
                assertThat(newServer.port).isEqualTo(port)

                oldServer.close()
                oldAcceptor.join()
                assertThat(oldServer.isClosed).isTrue()
                // NIO keeps the old listener open until the selector deregisters it, so a client
                // could still be routed to it; this round trip runs the selector past that point.
                assertThat(longLived.send("still here")).isEqualTo("v1: still here")
                val newWorker: Worker = Worker("v2", newServer, connectionScope)
                newWorker.start()

                val fresh: Client = Client(
                    aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port)) {
                        noDelay = true
                        lingerSeconds = 0
                    }
                )
                clients += fresh.socket
                assertThat(fresh.send("hello")).isEqualTo("v2: hello")

                val probe: Client = Client(
                    aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port)) {
                        socketTimeout = 500
                        keepAlive = false
                    }
                )
                clients += probe.socket
                val stalled: Throwable? = runCatching { probe.send("STALL") }.exceptionOrNull()
                assertThat(stalled).isNotNull()
                assertThat(causeChain(stalled!!)).anyMatch { cause: Throwable -> cause is SocketTimeoutException }

                newServer.close()
            } finally {
                clients.forEach { socket: Socket -> socket.close() }
                connectionScope.cancel()
                selector.close()
            }
        }
    }

    private fun causeChain(error: Throwable): List<Throwable> =
        generateSequence(error) { current: Throwable -> current.cause?.takeIf { it !== current } }.toList()
}
