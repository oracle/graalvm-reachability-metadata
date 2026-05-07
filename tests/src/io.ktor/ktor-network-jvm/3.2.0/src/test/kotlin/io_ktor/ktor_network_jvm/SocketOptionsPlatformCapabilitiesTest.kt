/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.port
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class SocketOptionsPlatformCapabilitiesTest {
    @Test
    public fun enablesReusePortForTcpServerTcpClientAndUdpSocket(): Unit = runBlocking {
        withTimeout(10_000) {
            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            var serverSocket: ServerSocket? = null
            var clientSocket: Socket? = null
            var datagramSocket: BoundDatagramSocket? = null

            try {
                val boundServerSocket: ServerSocket = aSocket(selector).tcp().bind(
                    InetSocketAddress(LOOPBACK_HOST, 0)
                ) {
                    reusePort = true
                }
                serverSocket = boundServerSocket
                val serverPort: Int = boundServerSocket.localAddress.port()

                val connectedClientSocket: Socket = aSocket(selector).tcp().connect(
                    InetSocketAddress(LOOPBACK_HOST, serverPort)
                ) {
                    reusePort = true
                }
                clientSocket = connectedClientSocket

                val boundDatagramSocket: BoundDatagramSocket = aSocket(selector).udp().bind(
                    InetSocketAddress(LOOPBACK_HOST, 0)
                ) {
                    reusePort = true
                }
                datagramSocket = boundDatagramSocket

                assertThat(serverPort).isPositive()
                assertThat(connectedClientSocket.localAddress.port()).isPositive()
                assertThat(connectedClientSocket.remoteAddress.port()).isEqualTo(serverPort)
                assertThat(boundDatagramSocket.localAddress.port()).isPositive()
            } finally {
                clientSocket?.close()
                serverSocket?.close()
                datagramSocket?.close()
                selector.close()
            }
        }
    }

    private companion object {
        private const val LOOPBACK_HOST: String = "127.0.0.1"
    }
}
