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
import io.ktor.network.sockets.port
import io.ktor.network.sockets.toJavaAddress
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Admits TCP peers by address with the ktor socket address model: `InetSocketAddress`
 * hostnames, ports, `resolveAddress` bytes, `copy`, destructuring and equality, and the
 * `toJavaAddress` conversion to `java.net` addresses.
 *
 * An operations gateway compiles CIDR allow rules from configured addresses, advertises the
 * endpoint it bound, and checks the raw address bytes of every accepted peer, serving both
 * ktor clients and a legacy blocking `java.net.Socket` client that dials the converted
 * endpoint.
 */
public class PeerAllowlistGatewayTest {

    /** One CIDR allow rule: the network address bytes and how many leading bits must match. */
    private class AllowRule(val network: ByteArray, private val prefixLength: Int) {
        init {
            require(prefixLength in 0..network.size * 8) { "Prefix /$prefixLength does not fit ${network.size} bytes" }
        }

        fun matches(candidate: ByteArray): Boolean {
            if (candidate.size != network.size) {
                return false
            }
            var remaining: Int = prefixLength
            var index: Int = 0
            while (remaining > 0) {
                val bits: Int = minOf(8, remaining)
                val mask: Int = (0xFF shl (8 - bits)) and 0xFF
                if ((candidate[index].toInt() and mask) != (network[index].toInt() and mask)) {
                    return false
                }
                remaining -= bits
                index++
            }
            return true
        }

        companion object {
            fun parse(cidr: String): AllowRule {
                val (host: String, prefix: String) = cidr.split('/', limit = 2)
                val address: InetSocketAddress = InetSocketAddress(host, 0)
                val bytes: ByteArray = requireNotNull(address.resolveAddress()) {
                    "Allow rule host ${address.hostname} is not resolved"
                }
                return AllowRule(bytes, prefix.toInt())
            }
        }
    }

    /** The outcome the gateway recorded for one accepted peer. */
    private data class Decision(val peer: InetSocketAddress, val admitted: Boolean)

    /** A gateway that greets allowed peers with `ADMITTED <address bytes>` and answers `STATUS`. */
    private class AdmissionGateway(private val listener: ServerSocket, @Volatile var rules: List<AllowRule>) {
        val decisions: Channel<Decision> = Channel(Channel.UNLIMITED)

        fun start(scope: CoroutineScope): Job = scope.launch {
            while (isActive) {
                val connection: Socket = listener.accept()
                launch { admit(connection) }
            }
        }

        private suspend fun admit(connection: Socket) {
            connection.use { socket: Socket ->
                val peer: InetSocketAddress = socket.remoteAddress as InetSocketAddress
                val peerBytes: ByteArray = checkNotNull(peer.resolveAddress()) { "Accepted peer has no address" }
                val admitted: Boolean = rules.any { rule: AllowRule -> rule.matches(peerBytes) }
                val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
                decisions.send(Decision(peer, admitted))
                if (!admitted) {
                    output.writeStringUtf8("DENIED\n")
                    return@use
                }
                output.writeStringUtf8("ADMITTED ${peerBytes.joinToString(".") { (it.toInt() and 0xFF).toString() }}\n")
                val input: ByteReadChannel = socket.openReadChannel()
                while (true) {
                    val command: String = input.readUTF8Line() ?: break
                    output.writeStringUtf8(if (command == "STATUS") "UP\n" else "ERROR unknown command $command\n")
                }
            }
        }
    }

    @Test
    public fun gatewayAdmitsPeersByAddressAcrossKtorAndJavaNetClients(): Unit = runBlocking {
        withTimeout(30_000) {
            val loopbackRule: AllowRule = AllowRule.parse("127.0.0.0/8")
            val ipv6LoopbackRule: AllowRule = AllowRule.parse("::1/128")
            assertThat(loopbackRule.network).containsExactly(127, 0, 0, 0)
            assertThat(ipv6LoopbackRule.network).hasSize(16).endsWith(1)
            assertThat(loopbackRule.matches(byteArrayOf(127, 10, 20, 30))).isTrue()
            assertThat(loopbackRule.matches(byteArrayOf(10, 0, 0, 1))).isFalse()
            assertThat(ipv6LoopbackRule.matches(byteArrayOf(127, 0, 0, 1))).isFalse()

            assertThatThrownBy { AllowRule.parse("10.0.0.0/40") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("/40")

            val selector: SelectorManager = SelectorManager(Dispatchers.IO)
            val configured: InetSocketAddress = InetSocketAddress("localhost", 0)
            val listener: ServerSocket = aSocket(selector).tcp().bind(configured)
            val gateway: AdmissionGateway = AdmissionGateway(listener, listOf(loopbackRule, ipv6LoopbackRule))
            val gatewayJob: Job = gateway.start(CoroutineScope(Dispatchers.IO))
            try {
                val advertised: InetSocketAddress = configured.copy(port = listener.localAddress.port())
                val (advertisedHost: String, advertisedPort: Int) = advertised
                assertThat(advertisedHost).isEqualTo("localhost")
                assertThat(advertisedPort).isPositive().isNotEqualTo(configured.port)
                assertThat(advertised).isEqualTo(listener.localAddress)
                assertThat(advertised.resolveAddress()).containsExactly(127, 0, 0, 1)

                val operator: Socket = aSocket(selector).tcp().connect(advertised)
                operator.use { socket: Socket ->
                    val input: ByteReadChannel = socket.openReadChannel()
                    val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
                    assertThat(input.readUTF8Line()).isEqualTo("ADMITTED 127.0.0.1")
                    output.writeStringUtf8("STATUS\n")
                    assertThat(input.readUTF8Line()).isEqualTo("UP")
                    val decision: Decision = gateway.decisions.receive()
                    assertThat(decision.admitted).isTrue()
                    assertThat(decision.peer).isEqualTo(socket.localAddress)
                    assertThat(socket.remoteAddress).isEqualTo(advertised)
                }

                val javaEndpoint: java.net.SocketAddress = advertised.toJavaAddress()
                assertThat(javaEndpoint).isEqualTo(java.net.InetSocketAddress("127.0.0.1", advertisedPort))
                val legacyLocal: java.net.SocketAddress = withContext(Dispatchers.IO) {
                    java.net.Socket().use { legacy: java.net.Socket ->
                        legacy.connect(javaEndpoint, 10_000)
                        val reader: BufferedReader =
                            BufferedReader(InputStreamReader(legacy.getInputStream(), StandardCharsets.UTF_8))
                        assertThat(reader.readLine()).isEqualTo("ADMITTED 127.0.0.1")
                        legacy.localSocketAddress
                    }
                }
                val legacyDecision: Decision = gateway.decisions.receive()
                assertThat(legacyDecision.admitted).isTrue()
                assertThat(legacyDecision.peer.toJavaAddress()).isEqualTo(legacyLocal)

                gateway.rules = listOf(AllowRule.parse("10.0.0.0/8"))
                val stranger: Socket = aSocket(selector).tcp().connect(advertisedHost, advertisedPort)
                stranger.use { socket: Socket ->
                    val input: ByteReadChannel = socket.openReadChannel()
                    assertThat(input.readUTF8Line()).isEqualTo("DENIED")
                    assertThat(input.readUTF8Line()).isNull()
                    val decision: Decision = gateway.decisions.receive()
                    assertThat(decision.admitted).isFalse()
                    assertThat(decision.peer.port).isEqualTo(socket.localAddress.port())
                }
            } finally {
                listener.close()
                gatewayJob.cancelAndJoin()
                selector.close()
            }
        }
    }
}
