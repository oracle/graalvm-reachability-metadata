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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Ties per-connection coroutines to the lifetime of a TCP connection through the accepted
 * socket's own coroutine scope and its `socketContext` job.
 *
 * A push server launches each client's session, writer and topic watchers inside the accepted
 * `Socket`, which is a `CoroutineScope`. Closing the socket does not complete `socketContext`
 * until every coroutine launched in it has finished, so the server's connection registry,
 * driven by `socketContext.invokeOnCompletion`, only drops a client after all of that client's
 * feed subscriptions have been released, whether the client quit or just went away.
 */
public class TcpConnectionScopedWatchersTest {

    /** An in-memory topic feed delivering every published value to each current subscriber. */
    private class Feed {
        private val subscribers: MutableMap<String, MutableList<Channel<String>>> = ConcurrentHashMap()

        fun subscribe(topic: String): Channel<String> {
            val inbox: Channel<String> = Channel(Channel.UNLIMITED)
            subscribers.computeIfAbsent(topic) { CopyOnWriteArrayList() }.add(inbox)
            return inbox
        }

        fun unsubscribe(topic: String, inbox: Channel<String>) {
            subscribers[topic]?.remove(inbox)
            inbox.close()
        }

        /** Publishes [value] to [topic] and returns how many subscribers received it. */
        fun publish(topic: String, value: String): Int =
            subscribers[topic].orEmpty().count { inbox: Channel<String> -> inbox.trySend(value).isSuccess }

        fun subscriberCount(topic: String): Int = subscribers[topic].orEmpty().size
    }

    /** A push server speaking `HELLO <name>`, `WATCH <topic>`, `UNWATCH <topic>` and `QUIT`. */
    private class PushServer(private val listener: ServerSocket, private val feed: Feed) {
        private val acceptScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private lateinit var acceptLoop: Job

        /** Connected clients by name, removed when their socket's job completes. */
        val connections: MutableMap<String, Socket> = ConcurrentHashMap()

        /** One `<name> closed=<isClosed>` entry per connection whose socket job completed. */
        val completions: Channel<String> = Channel(Channel.UNLIMITED)

        val port: Int
            get() = (listener.localAddress as InetSocketAddress).port

        fun start() {
            acceptLoop = acceptScope.launch {
                while (!listener.isClosed) {
                    val socket: Socket = try {
                        listener.accept()
                    } catch (closed: Exception) {
                        break
                    }
                    serve(socket)
                }
            }
        }

        /** Runs the whole session inside the socket's scope, so it is part of the socket's job. */
        private fun serve(socket: Socket): Job = socket.launch {
            val input: ByteReadChannel = socket.openReadChannel()
            val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
            val outbox: Channel<String> = Channel(Channel.UNLIMITED)
            val writer: Job = launch {
                for (line: String in outbox) {
                    output.writeStringUtf8("$line\n")
                }
            }
            val watchers: MutableMap<String, Job> = mutableMapOf()
            try {
                val name: String = checkNotNull(input.readUTF8Line()).removePrefix("HELLO ")
                connections[name] = socket
                socket.socketContext.invokeOnCompletion {
                    connections.remove(name)
                    completions.trySend("$name closed=${socket.isClosed}")
                }
                outbox.send("HI $name")
                while (true) {
                    val command: List<String> = (readCommand(input) ?: break).split(' ', limit = 2)
                    when (command[0]) {
                        "WATCH" -> watchers[command[1]] = socket.launch {
                            watch(command[1], outbox)
                        }
                        "UNWATCH" -> {
                            watchers.remove(command[1])?.cancelAndJoin()
                            outbox.send("UNWATCHED ${command[1]}")
                        }
                        "QUIT" -> {
                            outbox.send("BYE $name")
                            break
                        }
                        else -> outbox.send("ERROR ${command[0]}")
                    }
                }
            } finally {
                // Watchers are only cancelled here; the socket job still waits for their cleanup.
                watchers.values.forEach { watcher: Job -> watcher.cancel() }
                outbox.close()
                writer.join()
                socket.close()
            }
        }

        private suspend fun readCommand(input: ByteReadChannel): String? = try {
            input.readUTF8Line()
        } catch (reset: IOException) {
            null
        }

        private suspend fun watch(topic: String, outbox: Channel<String>) {
            val inbox: Channel<String> = feed.subscribe(topic)
            try {
                outbox.send("WATCHING $topic")
                for (value: String in inbox) {
                    outbox.send("EVENT $topic $value")
                }
            } finally {
                feed.unsubscribe(topic, inbox)
            }
        }

        suspend fun shutdown() {
            listener.close()
            listener.awaitClosed()
            acceptLoop.cancelAndJoin()
            connections.values.forEach { socket: Socket -> socket.cancel() }
            acceptScope.cancel()
        }
    }

    /** One client connection reading the server's pushed lines in order. */
    private class Client(private val socket: Socket) {
        private val input: ByteReadChannel = socket.openReadChannel()
        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

        suspend fun send(command: String) {
            output.writeStringUtf8("$command\n")
        }

        suspend fun next(): String? = input.readUTF8Line()

        suspend fun request(command: String): String? {
            send(command)
            return next()
        }

        fun close() {
            socket.close()
        }
    }

    @Test
    public fun subscriptionsAreReleasedBeforeTheConnectionCompletes(): Unit = runBlocking {
        withTimeout(30_000) {
            val feed: Feed = Feed()
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val server: PushServer = PushServer(
                aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0)),
                feed,
            )
            server.start()
            val clients: MutableList<Client> = mutableListOf()
            try {
                val connect: suspend (String) -> Client = { name: String ->
                    val client: Client = Client(
                        aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", server.port))
                    )
                    clients.add(client)
                    assertThat(client.request("HELLO $name")).isEqualTo("HI $name")
                    client
                }
                val alice: Client = connect("alice")
                assertThat(alice.request("WATCH orders")).isEqualTo("WATCHING orders")
                assertThat(alice.request("WATCH stock")).isEqualTo("WATCHING stock")
                val bob: Client = connect("bob")
                assertThat(bob.request("WATCH orders")).isEqualTo("WATCHING orders")
                assertThat(server.connections.keys).containsExactlyInAnyOrder("alice", "bob")
                assertThat(feed.subscriberCount("orders")).isEqualTo(2)
                assertThat(feed.subscriberCount("stock")).isEqualTo(1)

                // Each watcher pushes into its own connection.
                assertThat(feed.publish("orders", "o-1")).isEqualTo(2)
                assertThat(alice.next()).isEqualTo("EVENT orders o-1")
                assertThat(bob.next()).isEqualTo("EVENT orders o-1")

                // An explicit unwatch releases the subscription while the connection stays up.
                assertThat(alice.request("UNWATCH stock")).isEqualTo("UNWATCHED stock")
                assertThat(feed.subscriberCount("stock")).isZero()
                assertThat(feed.publish("stock", "s-1")).isZero()
                assertThat(feed.publish("orders", "o-2")).isEqualTo(2)
                assertThat(alice.next()).isEqualTo("EVENT orders o-2")
                assertThat(bob.next()).isEqualTo("EVENT orders o-2")

                // Alice quits: her socket job completes only after her watcher unsubscribed.
                assertThat(alice.request("QUIT")).isEqualTo("BYE alice")
                assertThat(alice.next()).isNull()
                assertThat(server.completions.receive()).isEqualTo("alice closed=true")
                assertThat(feed.subscriberCount("orders")).isEqualTo(1)
                assertThat(server.connections.keys).containsExactly("bob")
                assertThat(feed.publish("orders", "o-3")).isEqualTo(1)
                assertThat(bob.next()).isEqualTo("EVENT orders o-3")

                // Bob drops the connection without quitting; the same cleanup runs.
                bob.close()
                assertThat(server.completions.receive()).isEqualTo("bob closed=true")
                assertThat(feed.subscriberCount("orders")).isZero()
                assertThat(server.connections).isEmpty()
            } finally {
                clients.forEach { client: Client -> client.close() }
                server.shutdown()
                selector.close()
            }
        }
    }
}
