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
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.channels.ClosedSelectorException
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Shuts down a ktor selector manager while sockets it drives are suspended on it, isolating
 * tenants of one gateway on separate selectors.
 *
 * Every tenant of the gateway gets its own `SelectorManager` running on a dedicated event-loop
 * thread. Evicting a tenant closes its selector: the accept pending on its listener and the
 * read pending in its live session fail, the session is torn down so its client sees the end
 * of the stream, a listener bound afterwards cannot accept through it, and its event-loop
 * thread is released, while the other tenant keeps serving old and new connections.
 */
public class TenantSelectorEvictionTest {

    /** One tenant: a line service whose listener and sessions are driven by its own selector. */
    private class Tenant(val name: String, private val scope: CoroutineScope) {
        val eventLoop: ExecutorService = Executors.newSingleThreadExecutor { task: Runnable ->
            Thread(task, "selector-$name").apply { isDaemon = true }
        }
        private val dispatcher: ExecutorCoroutineDispatcher = eventLoop.asCoroutineDispatcher()
        val selector: SelectorManager = SelectorManager(dispatcher)
        lateinit var listener: ServerSocket
            private set
        val acceptFailure: CompletableDeferred<Throwable> = CompletableDeferred()
        val sessionFailures: MutableMap<Int, Throwable> = ConcurrentHashMap()
        val sessions: MutableMap<Int, Job> = ConcurrentHashMap()

        val port: Int
            get() = (listener.localAddress as InetSocketAddress).port

        suspend fun start(): Job {
            listener = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            return scope.launch { acceptSessions() }
        }

        private suspend fun acceptSessions(): Unit = coroutineScope {
            var nextSession: Int = 0
            try {
                while (true) {
                    val connection: Socket = listener.accept()
                    val id: Int = ++nextSession
                    sessions[id] = launch { serve(id, connection) }
                }
            } catch (failure: Throwable) {
                // A closed selector surfaces as a cancellation exception, so it is caught explicitly.
                acceptFailure.complete(failure)
                listener.close()
            }
        }

        private suspend fun serve(id: Int, connection: Socket) {
            connection.use { socket: Socket ->
                val input: ByteReadChannel = socket.openReadChannel()
                val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
                try {
                    output.writeStringUtf8("WELCOME $name session $id\n")
                    while (true) {
                        val line: String = input.readUTF8Line() ?: break
                        output.writeStringUtf8("$name: $line\n")
                    }
                } catch (failure: Throwable) {
                    sessionFailures[id] = failure
                }
            }
        }

        /**
         * Closes the tenant's selector, waits until its listener and sessions have let go of it,
         * and only then stops the event-loop thread the selector still needs to fail them.
         * Returns whether that thread was released.
         */
        suspend fun evict(): Boolean {
            selector.close()
            acceptFailure.await()
            sessions.values.joinAll()
            dispatcher.close()
            return eventLoop.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    /** A gateway client connected through the gateway's shared client selector. */
    private class TenantClient(val socket: Socket) {
        private val input: ByteReadChannel = socket.openReadChannel()
        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

        suspend fun readLine(): String? = input.readUTF8Line()

        suspend fun request(line: String): String? {
            output.writeStringUtf8("$line\n")
            return readLine()
        }
    }

    @Test
    fun evictingTenantShutsDownItsSelectorWithoutAffectingOtherTenants(): Unit = runBlocking {
        val gatewayScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val clientSelector: SelectorManager = SelectorManager(Dispatchers.IO)
        val alpha: Tenant = Tenant("alpha", gatewayScope)
        val beta: Tenant = Tenant("beta", gatewayScope)
        try {
            alpha.start()
            beta.start()
            withTimeout(30_000) {
                val alphaClient: TenantClient = connect(clientSelector, alpha.port)
                val betaClient: TenantClient = connect(clientSelector, beta.port)
                assertThat(alphaClient.readLine()).isEqualTo("WELCOME alpha session 1")
                assertThat(betaClient.readLine()).isEqualTo("WELCOME beta session 1")
                assertThat(alphaClient.request("orders?limit=5")).isEqualTo("alpha: orders?limit=5")
                assertThat(betaClient.request("invoices")).isEqualTo("beta: invoices")

                assertThat(alpha.evict()).isTrue()

                assertThat(isClosedSelectorFailure(alpha.acceptFailure.await())).isTrue()
                assertThat(alpha.listener.isClosed).isTrue()
                assertThat(alphaClient.readLine()).isNull()
                assertThat(isClosedSelectorFailure(alpha.sessionFailures.getValue(1))).isTrue()

                // A replacement listener can still be bound, but the closed selector cannot wait on it.
                val replacement: ServerSocket =
                    aSocket(alpha.selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
                val refused: Throwable? = runCatching { replacement.accept() }.exceptionOrNull()
                replacement.close()
                assertThat(refused).isInstanceOf(ClosedSelectorException::class.java)

                assertThat(betaClient.request("invoices/42")).isEqualTo("beta: invoices/42")
                val lateBetaClient: TenantClient = connect(clientSelector, beta.port)
                assertThat(lateBetaClient.readLine()).isEqualTo("WELCOME beta session 2")
                assertThat(lateBetaClient.request("health")).isEqualTo("beta: health")
                assertThat(beta.acceptFailure.isCompleted).isFalse()
                assertThat(beta.sessionFailures).isEmpty()

                listOf(alphaClient, betaClient, lateBetaClient).forEach { client: TenantClient ->
                    client.socket.close()
                }
            }
        } finally {
            gatewayScope.cancel()
            listOf(alpha, beta).forEach { tenant: Tenant ->
                tenant.selector.close()
                tenant.eventLoop.shutdown()
            }
            clientSelector.close()
        }
    }

    private suspend fun connect(selector: SelectorManager, port: Int): TenantClient =
        TenantClient(aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port)))

    /**
     * Whether [failure] reports the closed selector: suspensions pending at close are cancelled
     * with "Closed selector", while interest registered after close is refused by NIO.
     */
    private fun isClosedSelectorFailure(failure: Throwable): Boolean =
        generateSequence(failure) { cause: Throwable -> cause.cause }.any { cause: Throwable ->
            cause is ClosedSelectorException ||
                (cause is CancellationException && cause.message == "Closed selector")
        }
}
