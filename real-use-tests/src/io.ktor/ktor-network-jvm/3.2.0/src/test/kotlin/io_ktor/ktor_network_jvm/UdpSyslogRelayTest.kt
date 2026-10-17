/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.ConnectedDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.awaitClosed
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.port
import io.ktor.network.sockets.toJavaAddress
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.writeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress

/**
 * Streams datagrams through the `incoming` and `outgoing` coroutine channels of ktor UDP sockets,
 * converting peer addresses to `java.net` socket addresses with `toJavaAddress`.
 *
 * A syslog relay iterates its socket's `incoming` channel, tags every line with the port of the
 * application that sent it, and pushes it to a collector through `outgoing`; the collector's
 * address comes from a `java.net.InetSocketAddress`, as it would from application configuration.
 */
public class UdpSyslogRelayTest {

    /** Forwards every received line to [sink], prefixed with the sender's port. */
    private class SyslogRelay(private val socket: BoundDatagramSocket, private val sink: SocketAddress) {
        var forwarded: Int = 0
            private set

        fun start(scope: CoroutineScope): Job = scope.launch {
            for (datagram: Datagram in socket.incoming) {
                val sender: java.net.InetSocketAddress =
                    datagram.address.toJavaAddress() as java.net.InetSocketAddress
                assertThat(sender.address.isLoopbackAddress).isTrue()
                val line: String = "<${sender.port}> ${datagram.packet.readText()}"
                socket.outgoing.send(Datagram(buildPacket { writeText(line) }, sink))
                forwarded++
            }
        }
    }

    /** An application that logs through a socket connected to the relay. */
    private class SyslogClient(private val socket: ConnectedDatagramSocket) {
        val port: Int
            get() = socket.localAddress.port()

        suspend fun log(message: String) {
            socket.outgoing.send(Datagram(buildPacket { writeText(message) }, socket.remoteAddress))
        }
    }

    @Test
    public fun relayForwardsTaggedLinesFromEveryApplicationToTheCollector(): Unit = runBlocking {
        withTimeout(30_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val collector: BoundDatagramSocket = aSocket(selector).udp().bind("127.0.0.1", 0)
            val relaySocket: BoundDatagramSocket = aSocket(selector).udp().bind("127.0.0.1", 0)
            try {
                val configuredSink: java.net.InetSocketAddress =
                    java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), collector.localAddress.port())
                val sink: SocketAddress = InetSocketAddress(configuredSink.hostString, configuredSink.port)
                assertThat(sink.toJavaAddress()).isEqualTo(configuredSink)

                val relay: SyslogRelay = SyslogRelay(relaySocket, sink)
                val relayJob: Job = relay.start(CoroutineScope(Dispatchers.IO))

                val billing: SyslogClient = SyslogClient(
                    aSocket(selector).udp().connect(relaySocket.localAddress, InetSocketAddress("127.0.0.1", 0)),
                )
                val search: SyslogClient = SyslogClient(
                    aSocket(selector).udp().connect(relaySocket.localAddress, InetSocketAddress("127.0.0.1", 0)),
                )
                billing.log("invoice 17 issued")
                search.log("index rebuilt in 42ms")
                billing.log("invoice 17 paid")

                val received: MutableList<String> = mutableListOf()
                repeat(3) {
                    val datagram: Datagram = collector.incoming.receive()
                    assertThat(datagram.address.port()).isEqualTo(relaySocket.localAddress.port())
                    received += datagram.packet.readText()
                }
                assertThat(received).containsExactlyInAnyOrder(
                    "<${billing.port}> invoice 17 issued",
                    "<${search.port}> index rebuilt in 42ms",
                    "<${billing.port}> invoice 17 paid",
                )

                relaySocket.close()
                relaySocket.awaitClosed()
                relayJob.join()
                assertThat(relay.forwarded).isEqualTo(3)
                assertThat(relaySocket.isClosed).isTrue()
                assertThat(relayJob.isCompleted).isTrue()
            } finally {
                relaySocket.close()
                collector.close()
                selector.close()
            }
        }
    }
}
