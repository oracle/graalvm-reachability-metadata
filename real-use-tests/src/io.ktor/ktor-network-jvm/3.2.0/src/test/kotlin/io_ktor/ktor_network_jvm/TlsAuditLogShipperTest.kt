/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.tls
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateException
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Ships audit records over TLS client connections: ktor-network TCP sockets upgraded with
 * `tls()` and read through `connection()`.
 *
 * A collector terminates TLS with a self-signed certificate. The shipper trusts that
 * certificate, so its records arrive and are acknowledged in order; a shipper that trusts a
 * different certificate never completes the handshake and delivers nothing.
 */
public class TlsAuditLogShipperTest {

    /** A TLS 1.2 collector that acknowledges every `LOG` line and answers `BYE` with a count. */
    private class AuditCollector(identity: KeyStore, password: String) : AutoCloseable {
        private val serverSocket: SSLServerSocket
        private val acceptor: Thread
        val received: MutableList<String> = CopyOnWriteArrayList()
        val sessions: MutableList<String> = CopyOnWriteArrayList()

        init {
            val keyManagers: KeyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagers.init(identity, password.toCharArray())
            val context: SSLContext = SSLContext.getInstance("TLS")
            context.init(keyManagers.keyManagers, null, null)
            serverSocket = context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
            serverSocket.enabledProtocols = arrayOf("TLSv1.2")
            acceptor = Thread(::acceptLoop, "audit-collector")
            acceptor.start()
        }

        val port: Int
            get() = serverSocket.localPort

        private fun acceptLoop() {
            while (true) {
                val client: SSLSocket = try {
                    serverSocket.accept() as SSLSocket
                } catch (closed: SocketException) {
                    return
                }
                client.use { serve(it) }
            }
        }

        private fun serve(client: SSLSocket) {
            try {
                val reader = BufferedReader(InputStreamReader(client.inputStream, StandardCharsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(client.outputStream, StandardCharsets.UTF_8))
                client.startHandshake()
                sessions += client.session.protocol
                var accepted = 0
                while (true) {
                    val line: String = reader.readLine() ?: return
                    if (line == "BYE") {
                        writer.print("BYE $accepted\n")
                        writer.flush()
                        return
                    }
                    received += line.removePrefix("LOG ")
                    accepted++
                    writer.print("ACK $accepted\n")
                    writer.flush()
                }
            } catch (handshakeOrIo: IOException) {
                // A peer that rejects the certificate aborts the handshake; the collector moves on.
            }
        }

        override fun close() {
            serverSocket.close()
            acceptor.join(10_000)
        }
    }

    /** The application client: opens a TCP socket, upgrades it to TLS and ships records. */
    private class AuditShipper(private val selector: SelectorManager, private val trust: X509TrustManager) {

        suspend fun ship(port: Int, records: List<String>): ShipmentReceipt {
            val plain: Socket = aSocket(selector).tcp().connect("localhost", port)
            try {
                val secure: Socket = plain.tls(Dispatchers.IO) {
                    trustManager = trust
                    serverName = "localhost"
                }
                secure.use {
                    val connection: Connection = it.connection()
                    val acks: MutableList<String> = mutableListOf()
                    for (record in records) {
                        connection.output.writeStringUtf8("LOG $record\n")
                        connection.output.flush()
                        acks += checkNotNull(connection.input.readUTF8Line())
                    }
                    connection.output.writeStringUtf8("BYE\n")
                    connection.output.flush()
                    val farewell: String = checkNotNull(connection.input.readUTF8Line())
                    val remotePort: Int = (it.remoteAddress as InetSocketAddress).port
                    return ShipmentReceipt(acks, farewell, remotePort)
                }
            } finally {
                plain.close()
            }
        }
    }

    private data class ShipmentReceipt(val acks: List<String>, val farewell: String, val remotePort: Int)

    private fun selfSigned(alias: String, password: String): KeyStore = buildKeyStore {
        certificate(alias) {
            this.password = password
            hash = HashAlgorithm.SHA256
            keySizeInBits = 2048
            domains = listOf("localhost")
        }
    }

    private fun trustOnly(keyStore: KeyStore): X509TrustManager {
        val factory: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    }

    @Test
    fun shipsRecordsToATrustedCollectorAndRefusesAnUntrustedOne() {
        val password = "changeit"
        val collectorIdentity: KeyStore = selfSigned("collector", password)
        val impostorIdentity: KeyStore = selfSigned("impostor", password)
        val records: List<String> = listOf(
            "user=alice action=login result=ok",
            "user=alice action=export table=invoices rows=42",
            "user=bob action=delete table=sessions result=denied",
        )

        AuditCollector(collectorIdentity, password).use { collector ->
            SelectorManager(Dispatchers.IO).use { selector ->
                runBlocking {
                    withTimeout(30_000) {
                        val trusted = AuditShipper(selector, trustOnly(collectorIdentity))
                        val receipt: ShipmentReceipt = trusted.ship(collector.port, records)

                        assertThat(receipt.acks).containsExactly("ACK 1", "ACK 2", "ACK 3")
                        assertThat(receipt.farewell).isEqualTo("BYE 3")
                        assertThat(receipt.remotePort).isEqualTo(collector.port)

                        val misconfigured = AuditShipper(selector, trustOnly(impostorIdentity))
                        val refusal: Throwable? =
                            runCatching { misconfigured.ship(collector.port, listOf("leak")) }.exceptionOrNull()
                        assertThat(generateSequence(refusal) { it.cause }.toList())
                            .hasAtLeastOneElementOfType(CertificateException::class.java)
                    }
                }
            }
            assertThat(collector.received).containsExactlyElementsOf(records)
            assertThat(collector.sessions).containsExactly("TLSv1.2")
        }
    }
}
