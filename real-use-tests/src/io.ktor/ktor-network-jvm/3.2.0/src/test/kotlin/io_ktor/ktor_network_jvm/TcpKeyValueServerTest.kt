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
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves a line-based key-value protocol over TCP server and client sockets driven by a
 * ktor selector.
 *
 * The server accepts every connection on its own coroutine and shares one store, so a value
 * written by one client is read back by another one, as in a small cache service.
 */
public class TcpKeyValueServerTest {

    /** A tiny cache server speaking `SET key value`, `GET key`, `DEL key` and `QUIT`. */
    private class KeyValueServer(private val serverSocket: ServerSocket) {
        private val store: MutableMap<String, String> = ConcurrentHashMap()

        val port: Int
            get() = (serverSocket.localAddress as InetSocketAddress).port

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
                while (true) {
                    val line: String = input.readUTF8Line() ?: break
                    val parts: List<String> = line.split(' ', limit = 3)
                    val reply: String = when (parts[0]) {
                        "SET" -> {
                            store[parts[1]] = parts[2]
                            "OK"
                        }
                        "GET" -> store[parts[1]]?.let { value: String -> "VALUE $value" } ?: "MISSING"
                        "DEL" -> if (store.remove(parts[1]) != null) "DELETED" else "MISSING"
                        "QUIT" -> {
                            output.writeStringUtf8("BYE\n")
                            break
                        }
                        else -> "ERROR unknown command ${parts[0]}"
                    }
                    output.writeStringUtf8("$reply\n")
                }
            }
        }
    }

    /** A client session that sends one command per line and reads one reply line. */
    private class KeyValueClient(private val socket: Socket) {
        private val input: ByteReadChannel = socket.openReadChannel()
        private val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

        suspend fun send(command: String): String {
            output.writeStringUtf8("$command\n")
            return checkNotNull(input.readUTF8Line()) { "Server closed the connection on '$command'" }
        }

        suspend fun quit(): String {
            val reply: String = send("QUIT")
            val afterQuit: String? = input.readUTF8Line()
            assertThat(afterQuit).isNull()
            socket.close()
            return reply
        }
    }

    @Test
    public fun clientsShareStateThroughTheServer(): Unit = runBlocking {
        withTimeout(30_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket: ServerSocket = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            val server: KeyValueServer = KeyValueServer(serverSocket)
            val serverJob: Job = server.start(CoroutineScope(Dispatchers.IO))
            try {
                assertThat(server.port).isPositive()

                val writer: KeyValueClient = KeyValueClient(
                    aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", server.port))
                )
                assertThat(writer.send("SET greeting hello native world")).isEqualTo("OK")
                assertThat(writer.send("SET counter 41")).isEqualTo("OK")
                assertThat(writer.send("SET counter 42")).isEqualTo("OK")
                assertThat(writer.send("PING")).isEqualTo("ERROR unknown command PING")
                assertThat(writer.quit()).isEqualTo("BYE")

                val reader: KeyValueClient = KeyValueClient(
                    aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", server.port))
                )
                assertThat(reader.send("GET greeting")).isEqualTo("VALUE hello native world")
                assertThat(reader.send("GET counter")).isEqualTo("VALUE 42")
                assertThat(reader.send("DEL counter")).isEqualTo("DELETED")
                assertThat(reader.send("GET counter")).isEqualTo("MISSING")
                assertThat(reader.send("DEL counter")).isEqualTo("MISSING")
                assertThat(reader.quit()).isEqualTo("BYE")
            } finally {
                serverSocket.close()
                serverJob.cancelAndJoin()
                selector.close()
            }
        }
    }
}
