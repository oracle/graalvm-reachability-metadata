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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fails a TCP client over between upstream endpoints through ktor's selector-driven connect path,
 * where a refused connection surfaces as `ConnectException`.
 *
 * A quote client walks its endpoint list in order, skipping endpoints that refuse the
 * connection, and reuses its session until the upstream goes away; the lost session is noticed
 * on the next request, which is retried on the next endpoint that accepts.
 */
public class TcpUpstreamFailoverTest {

    /** A quote service answering `QUOTE <symbol>` with a price tagged by the serving instance. */
    private class QuoteServer(
        private val name: String,
        private val listener: ServerSocket,
        private val prices: Map<String, Int>,
    ) {
        private val sessionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val sessions: MutableList<Socket> = CopyOnWriteArrayList()
        private lateinit var acceptLoop: Job

        val accepted: Int
            get() = sessions.size

        fun start() {
            acceptLoop = sessionScope.launch {
                while (!listener.isClosed) {
                    val session: Socket = try {
                        listener.accept()
                    } catch (closed: Exception) {
                        break
                    }
                    sessions.add(session)
                    sessionScope.launch { serve(session) }
                }
            }
        }

        private suspend fun serve(session: Socket) {
            session.use { socket: Socket ->
                val input: ByteReadChannel = socket.openReadChannel()
                val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
                while (true) {
                    val request: List<String> = (input.readUTF8Line() ?: break).split(' ')
                    val price: Int? = prices[request.getOrNull(1)]
                    val reply: String = if (request[0] == "QUOTE" && price != null) {
                        "${request[1]} $price via $name"
                    } else {
                        "UNKNOWN ${request.drop(1).joinToString(" ")}"
                    }
                    output.writeStringUtf8("$reply\n")
                }
            }
        }

        /** Stops listening, then closes every open session and waits until its channel is closed. */
        suspend fun shutdown() {
            listener.close()
            listener.awaitClosed()
            acceptLoop.cancelAndJoin()
            sessions.forEach { session: Socket ->
                session.close()
                session.awaitClosed()
            }
            sessionScope.cancel()
        }
    }

    /** One live connection to an upstream endpoint. */
    private class UpstreamSession(val endpoint: String, private val socket: Socket) {
        private val input: ByteReadChannel = socket.openReadChannel()
        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

        /** Sends one request and returns its reply, or `null` once the upstream has gone away. */
        suspend fun exchange(request: String): String? = try {
            output.writeStringUtf8("$request\n")
            input.readUTF8Line()
        } catch (lost: IOException) {
            null
        }

        fun close() {
            socket.close()
        }
    }

    /** A client that connects to the first endpoint accepting it and fails over when it is lost. */
    private class FailoverClient(
        private val selector: SelectorManager,
        private val endpoints: Map<String, InetSocketAddress>,
    ) {
        val refusals: MutableList<String> = mutableListOf()
        val connections: MutableList<String> = mutableListOf()
        private var session: UpstreamSession? = null

        suspend fun quote(symbol: String): String {
            repeat(endpoints.size) {
                val current: UpstreamSession = session ?: connect()
                session = current
                val reply: String? = current.exchange("QUOTE $symbol")
                if (reply != null) {
                    return reply
                }
                current.close()
                session = null
            }
            error("No upstream answered QUOTE $symbol")
        }

        private suspend fun connect(): UpstreamSession {
            for ((name: String, address: InetSocketAddress) in endpoints) {
                try {
                    val socket: Socket = aSocket(selector).tcp().connect(address)
                    connections.add(name)
                    return UpstreamSession(name, socket)
                } catch (refused: ConnectException) {
                    refusals.add(name)
                }
            }
            throw ConnectException("Every upstream refused the connection: ${endpoints.keys}")
        }

        fun close() {
            session?.close()
        }
    }

    @Test
    public fun clientFailsOverWhenTheUpstreamRefusesOrGoesAway(): Unit = runBlocking {
        withTimeout(30_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val retired: ServerSocket = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            val retiredPort: Int = retired.port
            retired.close()
            retired.awaitClosed()

            val prices: Map<String, Int> = mapOf("ACME" to 101, "INIT" to 7)
            val primaryListener: ServerSocket = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            val backupListener: ServerSocket = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            val primary: QuoteServer = QuoteServer("primary", primaryListener, prices)
            val backup: QuoteServer = QuoteServer("backup", backupListener, prices)
            primary.start()
            backup.start()
            val client: FailoverClient = FailoverClient(
                selector,
                linkedMapOf(
                    "retired" to InetSocketAddress("127.0.0.1", retiredPort),
                    "primary" to InetSocketAddress("127.0.0.1", primaryListener.port),
                    "backup" to InetSocketAddress("127.0.0.1", backupListener.port),
                ),
            )
            try {
                assertThat(client.quote("ACME")).isEqualTo("ACME 101 via primary")
                assertThat(client.quote("INIT")).isEqualTo("INIT 7 via primary")
                assertThat(client.quote("NOPE")).isEqualTo("UNKNOWN NOPE")
                assertThat(client.refusals).containsExactly("retired")
                assertThat(client.connections).containsExactly("primary")
                assertThat(primary.accepted).isEqualTo(1)

                primary.shutdown()

                assertThat(client.quote("ACME")).isEqualTo("ACME 101 via backup")
                assertThat(client.quote("INIT")).isEqualTo("INIT 7 via backup")
                assertThat(client.refusals).containsExactly("retired", "retired", "primary")
                assertThat(client.connections).containsExactly("primary", "backup")
                assertThat(backup.accepted).isEqualTo(1)

                backup.shutdown()

                var outage: Throwable? = null
                try {
                    client.quote("ACME")
                } catch (failure: ConnectException) {
                    outage = failure
                }
                assertThat(outage).hasMessageContaining("Every upstream refused the connection")
                assertThat(client.refusals).endsWith("retired", "primary", "backup")
            } finally {
                client.close()
                primary.shutdown()
                backup.shutdown()
                selector.close()
            }
        }
    }
}
