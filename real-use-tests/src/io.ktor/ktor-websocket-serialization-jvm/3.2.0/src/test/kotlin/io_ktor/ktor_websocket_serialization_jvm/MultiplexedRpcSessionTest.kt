/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Correlated request-response multiplexing on one WebSocket session, where concurrent caller
 * coroutines share a client session through `sendSerialized` and a single reader routes the
 * `receiveDeserialized` replies of the kotlinx JSON converter back to them by request id.
 *
 * A calculator server collects a whole batch of requests and then answers in reverse arrival
 * order, so a caller only gets the right result if the client correlates replies by id; a
 * request for an unknown method comes back as a typed failure that only its own caller sees.
 */
public class MultiplexedRpcSessionTest {
    /** A remote call the client issues; `id` correlates it with its reply. */
    @Serializable
    data class RpcRequest(val id: Int, val method: String, val args: List<Long>)

    /** Every reply the server sends, told apart by the `outcome` discriminator. */
    @Serializable
    sealed class RpcReply {
        abstract val id: Int

        @Serializable
        @SerialName("result")
        data class Success(override val id: Int, val value: Long) : RpcReply()

        @Serializable
        @SerialName("error")
        data class Failure(override val id: Int, val code: Int, val message: String) : RpcReply()
    }

    /** The failure a caller sees when the server answers its call with an error reply. */
    class RpcException(val code: Int, message: String) : Exception(message)

    /** Client-side stub that lets many coroutines call the server over one shared session. */
    class RpcClient(private val session: DefaultClientWebSocketSession) {
        private val nextId: AtomicInteger = AtomicInteger()
        private val pending: ConcurrentHashMap<Int, CompletableDeferred<RpcReply>> = ConcurrentHashMap()

        /** Starts the single reader that hands each reply to the caller waiting on its id. */
        fun startReader(expectedReplies: Int): Job = session.launch {
            repeat(expectedReplies) {
                val reply: RpcReply = session.receiveDeserialized<RpcReply>()
                val waiter: CompletableDeferred<RpcReply> = checkNotNull(pending.remove(reply.id)) {
                    "reply ${reply.id} has no waiting caller"
                }
                waiter.complete(reply)
            }
        }

        /** Sends one call and suspends until the reader delivers its reply. */
        suspend fun call(method: String, vararg args: Long): Long {
            val id: Int = nextId.incrementAndGet()
            val waiter: CompletableDeferred<RpcReply> = CompletableDeferred()
            pending[id] = waiter
            session.sendSerialized(RpcRequest(id, method, args.toList()))
            return when (val reply: RpcReply = waiter.await()) {
                is RpcReply.Success -> reply.value
                is RpcReply.Failure -> throw RpcException(reply.code, reply.message)
            }
        }
    }

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0
    private val servedIds: CompletableDeferred<List<Int>> = CompletableDeferred()

    @BeforeEach
    fun startCalculatorServer() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(RPC_JSON)
            }
            routing {
                webSocket("/rpc") {
                    val batch: List<RpcRequest> = List(BATCH_SIZE) { receiveDeserialized<RpcRequest>() }
                    servedIds.complete(batch.map { it.id })
                    for (request: RpcRequest in batch.asReversed()) {
                        sendSerialized(evaluate(request))
                    }
                    for (ignored in incoming) {
                        // Keep the session open until the client has read every reply and closes it.
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopCalculatorServer() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `concurrent callers on one session each receive their own reply`(): Unit = runBlocking {
        val outcomes: MutableMap<String, Result<Long>> = linkedMapOf()

        withTimeout(TIMEOUT_MILLIS) {
            rpcClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/rpc") {
                    val rpc: RpcClient = RpcClient(this)
                    val reader: Job = rpc.startReader(BATCH_SIZE)
                    val calls: List<Deferred<Pair<String, Result<Long>>>> = listOf(
                        async { "sum" to runCatching { rpc.call("sum", 2, 3, 37) } },
                        async { "product" to runCatching { rpc.call("product", 6, 7, 1_000) } },
                        async { "max" to runCatching { rpc.call("max", -4, 19, 11) } },
                        async { "sqrt" to runCatching { rpc.call("sqrt", 81) } },
                        async { "sumOfNothing" to runCatching { rpc.call("sum") } },
                    )
                    outcomes.putAll(calls.awaitAll())
                    reader.join()
                    close(CloseReason(CloseReason.Codes.NORMAL, "all calls answered"))
                }
            }
        }

        assertEquals(42L, outcomes.getValue("sum").getOrThrow())
        assertEquals(42_000L, outcomes.getValue("product").getOrThrow())
        assertEquals(19L, outcomes.getValue("max").getOrThrow())
        assertEquals(0L, outcomes.getValue("sumOfNothing").getOrThrow())
        val failure: RpcException = outcomes.getValue("sqrt").exceptionOrNull() as RpcException
        assertEquals(METHOD_NOT_FOUND, failure.code)
        assertEquals("unknown method sqrt", failure.message)
        assertEquals((1..BATCH_SIZE).toSet(), servedIds.await().toSet())
    }

    private fun rpcClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(RPC_JSON)
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val BATCH_SIZE: Int = 5
        const val METHOD_NOT_FOUND: Int = -32601
        const val TIMEOUT_MILLIS: Long = 30_000

        val RPC_JSON: Json = Json {
            classDiscriminator = "outcome"
        }

        fun evaluate(request: RpcRequest): RpcReply = when (request.method) {
            "sum" -> RpcReply.Success(request.id, request.args.sum())
            "product" -> RpcReply.Success(request.id, request.args.fold(1L) { acc: Long, n: Long -> acc * n })
            "max" -> RpcReply.Success(request.id, request.args.max())
            else -> RpcReply.Failure(request.id, METHOD_NOT_FOUND, "unknown method ${request.method}")
        }
    }
}
