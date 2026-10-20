/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_tls_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.CIOCipherSuites
import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * In-band TLS upgrade of an established Ktor [Connection] (STARTTLS): a mail client greets a
 * relay in plaintext, is refused mail until it upgrades, then switches the same connection to
 * TLS with `Connection.tls` and submits a multi-record message body over the encrypted channel.
 */
class StartTlsConnectionUpgradeTest {
    @Test
    fun mailClientUpgradesItsPlaintextConnectionAndSubmitsMailOverTls(): Unit {
        val keyStore: KeyStore = buildKeyStore {
            certificate(ALIAS) {
                password = PASSWORD
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.RSA
                keyType = KeyType.Server
                keySizeInBits = 2048
                domains = listOf("localhost")
                ipAddresses = listOf(InetAddress.getLoopbackAddress())
            }
        }
        val body: List<String> = (1..1_500).map { index -> "line $index of the quarterly report attachment" }

        MailRelay(keyStore).use { relay ->
            val transcript: List<String> = runBlocking {
                withTimeout(TIMEOUT_MILLIS) {
                    SelectorManager(Dispatchers.IO).use { selector ->
                        val plain = aSocket(selector).tcp().connect("127.0.0.1", relay.port)
                        try {
                            submit(plain.connection(), keyStore, body)
                        } finally {
                            plain.close()
                        }
                    }
                }
            }

            val bodyBytes: Int = body.sumOf { line -> line.length + 1 }
            assertEquals(
                listOf(
                    "220 relay.localhost ESMTP",
                    "250 relay.localhost offers STARTTLS",
                    "530 must issue STARTTLS first",
                    "220 ready to start TLS",
                    "250 relay.localhost secured",
                    "250 sender ok",
                    "250 recipient ok",
                    "354 end data with .",
                    "250 queued $bodyBytes bytes",
                    "221 bye",
                ),
                transcript,
            )
            val delivery: Delivery = relay.delivery.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            assertEquals(listOf("EHLO client.localhost", "MAIL FROM:<ops@localhost>", "STARTTLS"), delivery.plaintext)
            assertEquals("TLSv1.2", delivery.protocol)
            assertEquals(CIOCipherSuites.ECDHE_RSA_AES128_SHA256.name, delivery.cipherSuite)
            assertEquals(
                listOf("EHLO client.localhost", "MAIL FROM:<ops@localhost>", "RCPT TO:<board@localhost>", "DATA", "QUIT"),
                delivery.secured,
            )
            assertEquals(body, delivery.message)
        }
    }

    /**
     * Speaks the plaintext half of the session on [connection], upgrades that same connection to
     * TLS, and finishes the submission over it; returns every reply the relay sent.
     */
    private suspend fun submit(connection: Connection, keyStore: KeyStore, body: List<String>): List<String> {
        val replies = mutableListOf<String>()
        replies += reply(connection.input)
        replies += command(connection.input, connection.output, "EHLO client.localhost")
        replies += command(connection.input, connection.output, "MAIL FROM:<ops@localhost>")
        replies += command(connection.input, connection.output, "STARTTLS")

        val secure = connection.tls(Dispatchers.IO) {
            trustManager = trustManagerFor(keyStore)
            serverName = "localhost"
            cipherSuites = listOf(CIOCipherSuites.ECDHE_RSA_AES128_SHA256)
        }
        secure.use { socket ->
            val input: ByteReadChannel = socket.openReadChannel()
            val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = false)
            replies += command(input, output, "EHLO client.localhost")
            replies += command(input, output, "MAIL FROM:<ops@localhost>")
            replies += command(input, output, "RCPT TO:<board@localhost>")
            replies += command(input, output, "DATA")
            body.forEach { line -> output.writeStringUtf8("$line\n") }
            replies += command(input, output, ".")
            replies += command(input, output, "QUIT")
        }
        return replies
    }

    /** Sends one command line and returns the single-line reply it gets. */
    private suspend fun command(input: ByteReadChannel, output: ByteWriteChannel, line: String): String {
        output.writeStringUtf8("$line\n")
        output.flush()
        return reply(input)
    }

    private suspend fun reply(input: ByteReadChannel): String =
        checkNotNull(input.readLine()) { "relay closed the connection before replying" }

    /** What the relay saw before and after the upgrade, and the message it queued. */
    private data class Delivery(
        val plaintext: List<String>,
        val protocol: String,
        val cipherSuite: String,
        val secured: List<String>,
        val message: List<String>,
    )

    /**
     * A plaintext mail relay that refuses mail until the client issues STARTTLS, then layers JDK
     * TLS 1.2 over the accepted socket and accepts one message on the secured channel.
     */
    private class MailRelay(keyStore: KeyStore) : AutoCloseable {
        private val serverSocket: ServerSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val tlsFactory: SSLSocketFactory
        private val worker: Thread
        val delivery: CompletableFuture<Delivery> = CompletableFuture()
        val port: Int = serverSocket.localPort

        init {
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD.toCharArray()) }
                .keyManagers
            tlsFactory = SSLContext.getInstance("TLSv1.2").apply { init(keyManagers, null, null) }.socketFactory
            worker = Thread({ serve() }, "mail-relay").apply { start() }
        }

        private fun serve(): Unit {
            try {
                serverSocket.accept().use { client -> delivery.complete(converse(client)) }
            } catch (failure: Exception) {
                delivery.completeExceptionally(failure)
            }
        }

        private fun converse(client: Socket): Delivery {
            val plaintext = mutableListOf<String>()
            send(client.getOutputStream(), "220 relay.localhost ESMTP")
            while (true) {
                val line: String = checkNotNull(receive(client.getInputStream())) { "client left before STARTTLS" }
                plaintext += line
                when {
                    line.startsWith("EHLO ") -> send(client.getOutputStream(), "250 relay.localhost offers STARTTLS")
                    line == "STARTTLS" -> break
                    else -> send(client.getOutputStream(), "530 must issue STARTTLS first")
                }
            }
            send(client.getOutputStream(), "220 ready to start TLS")

            val secured = mutableListOf<String>()
            val message = mutableListOf<String>()
            (tlsFactory.createSocket(client, "localhost", client.port, false) as SSLSocket).use { tls ->
                tls.useClientMode = false
                tls.enabledProtocols = arrayOf("TLSv1.2")
                tls.startHandshake()
                val input: InputStream = tls.inputStream
                val output: OutputStream = tls.outputStream
                while (true) {
                    val line: String = receive(input) ?: break
                    secured += line
                    when {
                        line.startsWith("EHLO ") -> send(output, "250 relay.localhost secured")
                        line.startsWith("MAIL FROM:") -> send(output, "250 sender ok")
                        line.startsWith("RCPT TO:") -> send(output, "250 recipient ok")
                        line == "DATA" -> {
                            send(output, "354 end data with .")
                            while (true) {
                                val dataLine: String = checkNotNull(receive(input)) { "client left inside DATA" }
                                if (dataLine == ".") {
                                    break
                                }
                                message += dataLine
                            }
                            send(output, "250 queued ${message.sumOf { it.length + 1 }} bytes")
                        }
                        line == "QUIT" -> {
                            send(output, "221 bye")
                            break
                        }
                        else -> send(output, "500 unknown command")
                    }
                }
                return Delivery(plaintext, tls.session.protocol, tls.session.cipherSuite, secured, message)
            }
        }

        /**
         * Reads one `\n`-terminated line byte by byte, so no byte of the client's TLS handshake
         * is consumed by the plaintext reader before the upgrade.
         */
        private fun receive(input: InputStream): String? {
            val line = ByteArrayOutputStream()
            while (true) {
                val next: Int = input.read()
                when (next) {
                    -1 -> return if (line.size() == 0) null else line.toString(StandardCharsets.UTF_8)
                    '\n'.code -> return line.toString(StandardCharsets.UTF_8)
                    else -> line.write(next)
                }
            }
        }

        private fun send(output: OutputStream, line: String): Unit {
            output.write("$line\n".toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val ALIAS: String = "mail-relay"
        const val PASSWORD: String = "changeit"
        const val TIMEOUT_MILLIS: Long = 30_000

        /** Trusts exactly the certificates held in [keyStore], as a pinned client would. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
