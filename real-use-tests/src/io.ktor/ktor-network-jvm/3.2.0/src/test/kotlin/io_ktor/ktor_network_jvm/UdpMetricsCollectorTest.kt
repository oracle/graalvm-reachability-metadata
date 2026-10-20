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
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.writeText
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
 * Collects StatsD-style metrics over UDP datagram sockets bound and connected through a ktor
 * selector.
 *
 * Agents report counters and gauges in datagrams that may batch several lines; the collector
 * acknowledges every datagram to its sender address, so each agent knows its batch arrived
 * before it sends the next one, and a `DUMP` request returns the aggregated snapshot.
 */
public class UdpMetricsCollectorTest {

    /** Aggregates `name:value|c` counters and `name:value|g` gauges from every sender. */
    private class MetricsCollector(private val socket: BoundDatagramSocket) {
        private val counters: MutableMap<String, Long> = sortedMapOf()
        private val gauges: MutableMap<String, Double> = sortedMapOf()

        val port: Int
            get() = (socket.localAddress as InetSocketAddress).port

        fun start(scope: CoroutineScope): Job = scope.launch {
            while (isActive) {
                val datagram: Datagram = socket.receive()
                val payload: String = datagram.packet.readText()
                val reply: String = if (payload == "DUMP") snapshot() else "ACK ${record(payload)}"
                socket.send(Datagram(buildPacket { writeText(reply) }, datagram.address))
            }
        }

        private fun record(payload: String): Int {
            val lines: List<String> = payload.lines().filter { line: String -> line.isNotBlank() }
            var accepted: Int = 0
            for (line: String in lines) {
                val name: String = line.substringBefore(':')
                val value: String = line.substringAfter(':').substringBefore('|')
                when (line.substringAfter('|')) {
                    "c" -> counters.merge(name, value.toLong(), Long::plus)
                    "g" -> gauges[name] = value.toDouble()
                    else -> continue
                }
                accepted++
            }
            return accepted
        }

        private fun snapshot(): String =
            (counters.map { (name: String, total: Long) -> "$name=$total" } +
                gauges.map { (name: String, level: Double) -> "$name~$level" }).joinToString(",")
    }

    /** An application agent bound to one collector, sending a batch and awaiting its ack. */
    private class MetricsAgent(private val socket: ConnectedDatagramSocket) {
        suspend fun report(vararg lines: String): String = request(lines.joinToString("\n"))

        suspend fun request(payload: String): String {
            socket.send(Datagram(buildPacket { writeText(payload) }, socket.remoteAddress))
            return socket.receive().packet.readText()
        }
    }

    @Test
    public fun agentsReportMetricsThatTheCollectorAggregates(): Unit = runBlocking {
        withTimeout(30_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val collectorSocket: BoundDatagramSocket =
                aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0))
            val collector: MetricsCollector = MetricsCollector(collectorSocket)
            val collectorJob: Job = collector.start(CoroutineScope(Dispatchers.IO))
            val collectorAddress: SocketAddress = InetSocketAddress("127.0.0.1", collector.port)
            try {
                assertThat(collector.port).isPositive()

                val web: ConnectedDatagramSocket = aSocket(selector).udp()
                    .connect(collectorAddress, InetSocketAddress("127.0.0.1", 0))
                val webAgent: MetricsAgent = MetricsAgent(web)
                assertThat(web.remoteAddress).isEqualTo(collectorAddress)
                assertThat(webAgent.report("requests:3|c", "heap.used:512.5|g")).isEqualTo("ACK 2")
                assertThat(webAgent.report("requests:2|c", "bogus:1|x")).isEqualTo("ACK 1")
                web.close()

                val worker: BoundDatagramSocket =
                    aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0))
                val batch: String = "requests:5|c\njobs:7|c\nheap.used:128.0|g"
                worker.send(Datagram(buildPacket { writeText(batch) }, collectorAddress))
                val workerAck: Datagram = worker.receive()
                assertThat(workerAck.address).isEqualTo(collectorAddress)
                assertThat(workerAck.packet.readText()).isEqualTo("ACK 3")
                worker.close()

                val dashboard: ConnectedDatagramSocket = aSocket(selector).udp()
                    .connect(collectorAddress, InetSocketAddress("127.0.0.1", 0))
                assertThat(MetricsAgent(dashboard).request("DUMP"))
                    .isEqualTo("jobs=7,requests=10,heap.used~128.0")
                dashboard.close()
            } finally {
                collectorSocket.close()
                collectorJob.cancelAndJoin()
                selector.close()
            }
        }
    }
}
