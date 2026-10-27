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
import io.ktor.network.sockets.awaitClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
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

/**
 * Forwards TCP connections through application-owned byte channels attached to ktor sockets with
 * `attachForReading` and `attachForWriting`.
 *
 * A port-forwarding relay pairs every accepted client with an upstream connection and wires each
 * direction through one [ByteChannel]: the channel one socket reads into is the channel the other
 * socket writes from. A client half-close therefore travels through the relay to the backend, and
 * the backend's close travels back, as in an SSH-style local port forward.
 */
public class TcpPortForwardingRelayTest {

    /** Backend answering every line in upper case and reporting a line count once the peer is done. */
    private class ShoutingBackend(private val serverSocket: ServerSocket) {
        val port: Int
            get() = (serverSocket.localAddress as InetSocketAddress).port

        fun start(scope: CoroutineScope): Job = scope.launch {
            while (isActive) {
                val connection: Socket = serverSocket.accept()
                launch {
                    connection.use { socket: Socket ->
                        val input: ByteReadChannel = socket.openReadChannel()
                        val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
                        var lines: Int = 0
                        while (true) {
                            val line: String = input.readUTF8Line() ?: break
                            lines++
                            output.writeStringUtf8("${line.uppercase()}\n")
                        }
                        output.writeStringUtf8("END $lines\n")
                        output.flushAndClose()
                    }
                }
            }
        }
    }

    /** One forwarded connection: both relayed directions and the two sockets they join. */
    private class Tunnel(
        val downstream: Socket,
        val upstream: Socket,
        val toUpstream: List<Job>,
        val toDownstream: List<Job>,
    ) {
        suspend fun join() {
            (toUpstream + toDownstream).forEach { job: Job -> job.join() }
        }
    }

    /** Local port forward from its own listening socket to a fixed upstream address. */
    private class PortForwarder(
        private val selector: SelectorManager,
        private val listener: ServerSocket,
        private val upstreamAddress: InetSocketAddress,
    ) {
        val tunnels: MutableList<CompletableDeferred<Tunnel>> = mutableListOf()

        val port: Int
            get() = (listener.localAddress as InetSocketAddress).port

        fun expectTunnels(count: Int) {
            repeat(count) { tunnels.add(CompletableDeferred()) }
        }

        fun start(scope: CoroutineScope): Job = scope.launch {
            var accepted: Int = 0
            while (isActive) {
                val downstream: Socket = listener.accept()
                val upstream: Socket = aSocket(selector).tcp().connect(upstreamAddress)
                tunnels[accepted++].complete(forward(downstream, upstream))
            }
        }

        private fun forward(downstream: Socket, upstream: Socket): Tunnel {
            val clientToServer: ByteChannel = ByteChannel(autoFlush = true)
            val clientReader: WriterJob = downstream.attachForReading(clientToServer)
            val serverWriter: ReaderJob = upstream.attachForWriting(clientToServer)

            val serverToClient: ByteChannel = ByteChannel(autoFlush = true)
            val serverReader: WriterJob = upstream.attachForReading(serverToClient)
            val clientWriter: ReaderJob = downstream.attachForWriting(serverToClient)

            return Tunnel(
                downstream = downstream,
                upstream = upstream,
                toUpstream = listOf(clientReader.job, serverWriter.job),
                toDownstream = listOf(serverReader.job, clientWriter.job),
            )
        }
    }

    @Test
    fun forwardsConversationsAndPropagatesHalfCloseThroughAttachedChannels(): Unit = runBlocking {
        withTimeout(30_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val backendSocket: ServerSocket = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            val backend: ShoutingBackend = ShoutingBackend(backendSocket)
            val backendJob: Job = backend.start(this)

            val forwarderSocket: ServerSocket = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            val forwarder: PortForwarder =
                PortForwarder(selector, forwarderSocket, InetSocketAddress("127.0.0.1", backend.port))
            forwarder.expectTunnels(2)
            val forwarderJob: Job = forwarder.start(this)

            val sessions: List<List<String>> = listOf(
                listOf("hello relay", "forward me"),
                listOf("second client", "three lines", "done"),
            )
            val transcripts: List<List<String>> = sessions.mapIndexed { index: Int, lines: List<String> ->
                aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", forwarder.port)).use { client: Socket ->
                    val input: ByteReadChannel = client.openReadChannel()
                    val output: ByteWriteChannel = client.openWriteChannel(autoFlush = true)
                    val replies: MutableList<String> = mutableListOf()
                    for (line: String in lines) {
                        output.writeStringUtf8("$line\n")
                        replies.add(input.readUTF8Line()!!)
                    }
                    output.flushAndClose()
                    while (true) {
                        replies.add(input.readUTF8Line() ?: break)
                    }

                    val tunnel: Tunnel = forwarder.tunnels[index].await()
                    tunnel.join()
                    assertThat(tunnel.toUpstream + tunnel.toDownstream)
                        .allMatch { job: Job -> job.isCompleted && !job.isCancelled }
                    tunnel.downstream.close()
                    tunnel.upstream.close()
                    tunnel.downstream.awaitClosed()
                    tunnel.upstream.awaitClosed()
                    assertThat(tunnel.downstream.socketContext.isCompleted).isTrue()
                    assertThat(tunnel.upstream.socketContext.isCompleted).isTrue()
                    replies
                }
            }

            assertThat(transcripts[0]).containsExactly("HELLO RELAY", "FORWARD ME", "END 2")
            assertThat(transcripts[1]).containsExactly("SECOND CLIENT", "THREE LINES", "DONE", "END 3")

            forwarderJob.cancelAndJoin()
            backendJob.cancelAndJoin()
            forwarderSocket.close()
            backendSocket.close()
            selector.close()
        }
    }
}
