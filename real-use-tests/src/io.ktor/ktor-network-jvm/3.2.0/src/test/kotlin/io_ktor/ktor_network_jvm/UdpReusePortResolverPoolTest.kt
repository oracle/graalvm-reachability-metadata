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
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.TypeOfService
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.writeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.BindException
import java.util.concurrent.atomic.AtomicInteger

/**
 * UDP socket options applied through the ktor UDP bind/connect option blocks: `reusePort` and
 * `reuseAddress` to bind a pool of worker sockets to one shared port, plus `broadcast`, buffer
 * sizes and `typeOfService`.
 *
 * A service-registry resolver answers `RESOLVE <service>` lookups. To spread load it binds several
 * worker sockets to the same port, and the kernel hands every client's flow to exactly one worker.
 * A process that binds the port without `reusePort` is refused, so a stray instance cannot steal
 * lookups from the pool.
 */
public class UdpReusePortResolverPoolTest {

    /** One resolver worker: answers lookups from the shared registry and counts what it served. */
    private class ResolverWorker(
        val name: String,
        private val socket: BoundDatagramSocket,
        private val registry: Map<String, String>,
    ) {
        val served: AtomicInteger = AtomicInteger()

        fun start(scope: CoroutineScope): Job = scope.launch {
            while (isActive) {
                val request: Datagram = socket.receive()
                val service: String = request.packet.readText().removePrefix("RESOLVE ")
                val endpoint: String = registry[service] ?: "NXDOMAIN"
                served.incrementAndGet()
                socket.send(Datagram(buildPacket { writeText("$name $service=$endpoint") }, request.address))
            }
        }
    }

    /** A client application that looks services up through one connected datagram socket. */
    private class RegistryClient(private val socket: ConnectedDatagramSocket) {
        val workersSeen: MutableSet<String> = mutableSetOf()

        suspend fun resolve(service: String): String {
            socket.send(Datagram(buildPacket { writeText("RESOLVE $service") }, socket.remoteAddress))
            val answer: String = socket.receive().packet.readText()
            workersSeen += answer.substringBefore(' ')
            return answer.substringAfter(' ')
        }
    }

    /** Socket options every resolver worker binds with, so all of them can join the same port. */
    private val pooledWorker: SocketOptions.UDPSocketOptions.() -> Unit = {
        reuseAddress = true
        reusePort = true
        receiveBufferSize = 64 * 1024
        sendBufferSize = 64 * 1024
        typeOfService = TypeOfService.IPTOS_LOWDELAY
    }

    @Test
    public fun pooledWorkersShareOnePortAndServeEveryLookupExactlyOnce(): Unit = runBlocking {
        withTimeout(30_000) {
            val registry: Map<String, String> = mapOf(
                "billing" to "10.0.0.7:8443",
                "catalog" to "10.0.0.12:9090",
                "search" to "10.0.0.21:9200",
            )
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val sockets: MutableList<BoundDatagramSocket> = mutableListOf()
            val clients: MutableList<ConnectedDatagramSocket> = mutableListOf()
            try {
                val first: BoundDatagramSocket =
                    aSocket(selector).udp().bind(InetSocketAddress("127.0.0.1", 0), pooledWorker)
                sockets += first
                val port: Int = (first.localAddress as InetSocketAddress).port
                val serviceAddress: InetSocketAddress = InetSocketAddress("127.0.0.1", port)
                repeat(2) {
                    sockets += aSocket(selector).udp().bind(serviceAddress, pooledWorker)
                }
                assertThat(sockets.map { socket: BoundDatagramSocket -> socket.localAddress })
                    .containsOnly(serviceAddress)

                val stray: Throwable? = runCatching { aSocket(selector).udp().bind(serviceAddress) }
                    .onSuccess { socket: BoundDatagramSocket -> sockets += socket }
                    .exceptionOrNull()
                assertThat(stray).isInstanceOf(BindException::class.java)

                val workers: List<ResolverWorker> = sockets.mapIndexed { index: Int, socket: BoundDatagramSocket ->
                    ResolverWorker("worker-$index", socket, registry)
                }
                val workerJobs: List<Job> = workers.map { worker: ResolverWorker -> worker.start(workerScope) }

                val lookups: List<String> = listOf("billing", "catalog", "search", "ledger")
                val registryClients: List<RegistryClient> = List(6) {
                    val socket: ConnectedDatagramSocket = aSocket(selector).udp()
                        .connect(serviceAddress, InetSocketAddress("127.0.0.1", 0)) {
                            broadcast = true
                            receiveBufferSize = 16 * 1024
                        }
                    clients += socket
                    RegistryClient(socket)
                }
                for (client: RegistryClient in registryClients) {
                    assertThat(lookups.map { service: String -> client.resolve(service) }).containsExactly(
                        "billing=10.0.0.7:8443",
                        "catalog=10.0.0.12:9090",
                        "search=10.0.0.21:9200",
                        "ledger=NXDOMAIN",
                    )
                }

                assertThat(registryClients).allSatisfy { client: RegistryClient ->
                    assertThat(client.workersSeen).hasSize(1)
                }
                val servedTotal: Int = workers.sumOf { worker: ResolverWorker -> worker.served.get() }
                assertThat(servedTotal).isEqualTo(registryClients.size * lookups.size)
                workerJobs.forEach { job: Job -> job.cancelAndJoin() }
            } finally {
                workerScope.coroutineContext[Job]!!.cancelAndJoin()
                clients.forEach { socket: ConnectedDatagramSocket -> socket.close() }
                sockets.forEach { socket: BoundDatagramSocket -> socket.close() }
                selector.close()
            }
        }
    }
}
