/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.UnixSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

public class ConnectUtilsJvmKtTest {
    @Test
    public fun opensUnixDomainServerAndClientSocketChannels(): Unit = runBlocking {
        withTimeout(10_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val temporaryDirectory: Path = Files.createTempDirectory("ktor-network-unix-")
            val socketPath: Path = temporaryDirectory.resolve("server.sock")
            val unixAddress: UnixSocketAddress = UnixSocketAddress(socketPath.toString())
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            var acceptedSocket: Socket? = null
            var acceptDeferred: Deferred<Socket>? = null

            try {
                val boundServerSocket: ServerSocket = aSocket(selector).tcp().bind(unixAddress)
                serverSocket = boundServerSocket
                val pendingAccept: Deferred<Socket> = async {
                    boundServerSocket.accept()
                }
                acceptDeferred = pendingAccept

                val connectedClientSocket: Socket = aSocket(selector).tcp().connect(unixAddress)
                clientSocket = connectedClientSocket
                val acceptedClientSocket: Socket = pendingAccept.await()
                acceptedSocket = acceptedClientSocket

                assertThat(boundServerSocket.localAddress).isInstanceOf(UnixSocketAddress::class.java)
                assertThat(connectedClientSocket.remoteAddress).isInstanceOf(UnixSocketAddress::class.java)
                assertThat(acceptedClientSocket.localAddress).isInstanceOf(UnixSocketAddress::class.java)
            } finally {
                acceptedSocket?.close()
                clientSocket?.close()
                serverSocket?.close()
                acceptDeferred?.cancelAndJoin()
                selector.close()
                Files.deleteIfExists(socketPath)
                Files.deleteIfExists(temporaryDirectory)
            }
        }
    }
}
