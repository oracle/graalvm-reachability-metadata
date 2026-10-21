/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_tls_jvm

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.CIOCipherSuites
import io.ktor.network.tls.CipherSuite
import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * RSA key transport with the AES-CBC/HMAC-SHA1 cipher suites: a utility application reads a
 * legacy metering head-end that only speaks `TLS_RSA_WITH_AES_128_CBC_SHA` and
 * `TLS_RSA_WITH_AES_256_CBC_SHA`, uploads a tariff table spanning many CBC records, and refuses
 * a reply whose record MAC was forged.
 */
class RsaKeyTransportCbcSuiteTest {
    @Test
    fun clientTransportsThePremasterWithRsaAndProtectsRecordsWithCbcAndHmac(): Unit {
        val keyStore: KeyStore = buildKeyStore {
            certificate(ALIAS) {
                password = PASSWORD
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.RSA
                keyType = KeyType.Server
                keySizeInBits = 2048
                domains = listOf("metering.local")
                ipAddresses = listOf(InetAddress.getLoopbackAddress())
            }
        }
        val trust: X509TrustManager = trustManagerFor(keyStore)
        val tariffTable: ByteArray = tariffTable()
        val tariffCrc: String = CRC32().apply { update(tariffTable) }.value.toString(16)

        val aes128Suite: CipherSuite = CIOCipherSuites.TLS_RSA_WITH_AES128_CBC_SHA
        val aes256Suite: CipherSuite = CIOCipherSuites.TLS_RSA_WITH_AES256_CBC_SHA
        val aes128: Exchange = MeteringHeadEnd(keyStore, aes128Suite, forgeMac = false).use {
            Exchange(readMeters(it.port, trust, aes128Suite, tariffTable), it.session())
        }
        val aes256: Exchange = MeteringHeadEnd(keyStore, aes256Suite, forgeMac = false).use {
            Exchange(readMeters(it.port, trust, aes256Suite, tariffTable), it.session())
        }
        val forged: Exchange = MeteringHeadEnd(keyStore, aes128Suite, forgeMac = true).use {
            Exchange(listOfNotNull(readForgedMeter(it.port, trust)), it.session())
        }

        val expectedReplies: List<String> = listOf(
            "meter-17 4821.6 kWh",
            "meter-42 1307.2 kWh",
            "STORED ${tariffTable.size} $tariffCrc",
        )
        assertEquals(expectedReplies, aes128.replies)
        assertEquals(expectedReplies, aes256.replies)
        assertEquals(emptyList<String>(), forged.replies)

        assertEquals(listOf(aes128Suite.code), aes128.session.offeredSuites)
        assertEquals(listOf(aes256Suite.code), aes256.session.offeredSuites)
        listOf(aes128.session, aes256.session, forged.session).forEach { session ->
            assertArrayEquals(byteArrayOf(3, 3), session.premasterVersion)
            assertTrue(session.clientFinishedVerified)
        }
        assertEquals(
            listOf("READ meter-17", "READ meter-42", "UPLOAD ${tariffTable.size}", "BYE"),
            aes128.session.commands,
        )
        assertEquals(aes128.session.commands, aes256.session.commands)
        assertEquals(listOf("READ meter-17"), forged.session.commands)
        assertTrue(aes128.session.uploadRecords > 1, "upload used ${aes128.session.uploadRecords} record(s)")
        assertTrue(aes256.session.uploadRecords > 1, "upload used ${aes256.session.uploadRecords} record(s)")
    }

    /** One connection's replies as the application saw them, next to what the head-end observed. */
    private data class Exchange(val replies: List<String>, val session: HeadEndSession)

    /** Reads two meters and uploads [tariffTable] over a TLS session restricted to [suite]. */
    private fun readMeters(port: Int, trust: X509TrustManager, suite: CipherSuite, tariffTable: ByteArray): List<String> =
        withMeteringSession(port, trust, suite) { input, output ->
            val replies = mutableListOf<String>()
            for (meter in listOf("meter-17", "meter-42")) {
                output.writeStringUtf8("READ $meter\n")
                replies += checkNotNull(input.readLine()) { "head-end closed before reading $meter" }
            }
            output.writeStringUtf8("UPLOAD ${tariffTable.size}\n")
            output.writeFully(tariffTable)
            replies += checkNotNull(input.readLine()) { "head-end closed before acknowledging the upload" }
            output.writeStringUtf8("BYE\n")
            check(input.readLine() == "BYE") { "head-end did not acknowledge BYE" }
            replies
        }

    /** Asks a head-end whose replies carry a forged MAC for one reading and returns what arrived. */
    private fun readForgedMeter(port: Int, trust: X509TrustManager): String? =
        withMeteringSession(port, trust, CIOCipherSuites.TLS_RSA_WITH_AES128_CBC_SHA) { input, output ->
            output.writeStringUtf8("READ meter-17\n")
            input.readLine()
        }

    private fun <T> withMeteringSession(
        port: Int,
        trust: X509TrustManager,
        suite: CipherSuite,
        conversation: suspend (ByteReadChannel, ByteWriteChannel) -> T,
    ): T = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            SelectorManager(Dispatchers.IO).use { selector ->
                val plain = aSocket(selector).tcp().connect("127.0.0.1", port)
                try {
                    plain.tls(Dispatchers.IO) {
                        trustManager = trust
                        serverName = "metering.local"
                        cipherSuites = listOf(suite)
                    }.use { secure ->
                        conversation(secure.openReadChannel(), secure.openWriteChannel(autoFlush = true))
                    }
                } finally {
                    plain.close()
                }
            }
        }
    }

    /** What the head-end observed on the single connection it accepted. */
    private data class HeadEndSession(
        val offeredSuites: List<Short>,
        val premasterVersion: ByteArray,
        val clientFinishedVerified: Boolean,
        val commands: List<String>,
        val uploadRecords: Int,
    )

    /**
     * A legacy metering head-end that speaks TLS 1.2 with RSA key transport and one AES-CBC
     * suite, which a current JDK no longer negotiates. It accepts one connection, answers `READ`
     * and `UPLOAD` commands, and, when [forgeMac] is set, sends its first reply with a MAC that
     * does not match the record.
     */
    private class MeteringHeadEnd(keyStore: KeyStore, private val suite: CipherSuite, private val forgeMac: Boolean) :
        AutoCloseable {
        private val serverSocket: ServerSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val privateKey: PrivateKey = keyStore.getKey(ALIAS, PASSWORD.toCharArray()) as PrivateKey
        private val chain: Array<Certificate> = keyStore.getCertificateChain(ALIAS)
        private val random: SecureRandom = SecureRandom()
        private val transcript: ByteArrayOutputStream = ByteArrayOutputStream()
        private val outcome: CompletableFuture<HeadEndSession> = CompletableFuture()
        private val worker: Thread
        val port: Int = serverSocket.localPort

        private lateinit var input: DataInputStream
        private lateinit var output: OutputStream
        private lateinit var keyBlock: ByteArray
        private var readSequence: Long = 0
        private var writeSequence: Long = 0
        private var readEncrypted: Boolean = false
        private var applicationRecords: Int = 0
        private var uploadRecords: Int = 0
        private val pending: ByteArrayOutputStream = ByteArrayOutputStream()

        init {
            worker = Thread({ serve() }, "metering-head-end").apply { start() }
        }

        fun session(): HeadEndSession = outcome.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

        private fun serve(): Unit {
            try {
                serverSocket.accept().use { connection -> outcome.complete(converse(connection)) }
            } catch (failure: Exception) {
                outcome.completeExceptionally(failure)
            }
        }

        private fun converse(connection: Socket): HeadEndSession {
            connection.soTimeout = TIMEOUT_MILLIS.toInt()
            input = DataInputStream(connection.getInputStream())
            output = connection.getOutputStream()

            val clientHello: ByteArray = readHandshake(CLIENT_HELLO)
            val clientRandom: ByteArray = clientHello.copyOfRange(2, 34)
            val offeredSuites: List<Short> = offeredSuites(clientHello)
            check(suite.code in offeredSuites) { "client did not offer ${suite.name}" }

            val serverRandom = ByteArray(32).also { random.nextBytes(it) }
            val serverHello = ByteArrayOutputStream().apply {
                write(byteArrayOf(3, 3))
                write(serverRandom)
                write(0)
                writeShort(suite.code.toInt())
                write(0)
            }.toByteArray()
            val certificates = ByteArrayOutputStream().apply {
                chain.forEach { writeLength24(it.encoded.size); write(it.encoded) }
            }.toByteArray()
            val certificateMessage = ByteArrayOutputStream().apply {
                writeLength24(certificates.size)
                write(certificates)
            }.toByteArray()
            writeRecord(
                HANDSHAKE,
                handshake(SERVER_HELLO, serverHello) + handshake(CERTIFICATE, certificateMessage) +
                    handshake(SERVER_HELLO_DONE, ByteArray(0)),
            )

            val keyExchange: ByteArray = readHandshake(CLIENT_KEY_EXCHANGE)
            val encryptedLength: Int = ((keyExchange[0].toInt() and 0xff) shl 8) or (keyExchange[1].toInt() and 0xff)
            val premaster: ByteArray = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                .apply { init(Cipher.DECRYPT_MODE, privateKey) }
                .doFinal(keyExchange, 2, encryptedLength)
            check(premaster.size == 48) { "premaster secret has ${premaster.size} bytes" }
            val master: ByteArray = prf(premaster, "master secret", clientRandom + serverRandom, 48)
            keyBlock = prf(
                master,
                "key expansion",
                serverRandom + clientRandom,
                2 * MAC_LENGTH + 2 * suite.keyStrengthInBytes + 2 * suite.fixedIvLength,
            )

            val changeCipherSpec: Record = readRecord()
            check(changeCipherSpec.type == CHANGE_CIPHER_SPEC && changeCipherSpec.body.contentEquals(byteArrayOf(1)))
            readEncrypted = true
            val expectedClientFinished: ByteArray = prf(master, "client finished", transcriptHash(), 12)
            val clientFinished: ByteArray = readHandshake(FINISHED)
            val clientFinishedVerified: Boolean = MessageDigest.isEqual(expectedClientFinished, clientFinished)
            check(clientFinishedVerified) { "client Finished does not match the handshake transcript" }

            writeRecord(CHANGE_CIPHER_SPEC, byteArrayOf(1))
            writeEncrypted(HANDSHAKE, handshake(FINISHED, prf(master, "server finished", transcriptHash(), 12)))

            val commands = mutableListOf<String>()
            while (true) {
                val command: String = readLine() ?: break
                commands += command
                val words: List<String> = command.split(' ')
                when (words[0]) {
                    "READ" -> {
                        writeEncrypted(APPLICATION_DATA, "${words[1]} ${READINGS.getValue(words[1])}\n".utf8(), forgeMac)
                        if (forgeMac) {
                            drainUntilClosed()
                            break
                        }
                    }
                    "UPLOAD" -> {
                        val recordsBefore: Int = applicationRecords
                        val table: ByteArray = readBytes(words[1].toInt())
                        uploadRecords = applicationRecords - recordsBefore
                        val crc: String = CRC32().apply { update(table) }.value.toString(16)
                        writeEncrypted(APPLICATION_DATA, "STORED ${table.size} $crc\n".utf8())
                    }
                    "BYE" -> {
                        writeEncrypted(APPLICATION_DATA, "BYE\n".utf8())
                        break
                    }
                    else -> error("unknown command '$command'")
                }
            }
            return HeadEndSession(offeredSuites, premaster.copyOfRange(0, 2), clientFinishedVerified, commands, uploadRecords)
        }

        private class Record(val type: Int, val body: ByteArray)

        private fun readRecord(): Record {
            val type: Int = input.readUnsignedByte()
            input.readUnsignedShort()
            val body = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
            return Record(type, if (readEncrypted) decrypt(type, body) else body)
        }

        /** Reads one handshake message of [expectedType], adds it to the transcript, returns its body. */
        private fun readHandshake(expectedType: Int): ByteArray {
            val record: Record = readRecord()
            check(record.type == HANDSHAKE) { "expected a handshake record, got type ${record.type}" }
            val message: ByteArray = record.body
            check((message[0].toInt() and 0xff) == expectedType) { "expected handshake $expectedType, got ${message[0]}" }
            val length: Int = ((message[1].toInt() and 0xff) shl 16) or
                ((message[2].toInt() and 0xff) shl 8) or (message[3].toInt() and 0xff)
            check(message.size == 4 + length) { "handshake $expectedType spans more than one record" }
            transcript.write(message)
            return message.copyOfRange(4, message.size)
        }

        /** Returns the next line of application data, or `null` once the client ends the session. */
        private fun readLine(): String? {
            while (true) {
                val buffered: ByteArray = pending.toByteArray()
                val newline: Int = buffered.indexOf('\n'.code.toByte())
                if (newline >= 0) {
                    pending.reset()
                    pending.write(buffered, newline + 1, buffered.size - newline - 1)
                    return String(buffered, 0, newline, StandardCharsets.UTF_8)
                }
                if (!readApplicationData()) return null
            }
        }

        private fun readBytes(count: Int): ByteArray {
            while (pending.size() < count) {
                check(readApplicationData()) { "client ended the session inside a $count byte upload" }
            }
            val buffered: ByteArray = pending.toByteArray()
            pending.reset()
            pending.write(buffered, count, buffered.size - count)
            return buffered.copyOfRange(0, count)
        }

        private fun readApplicationData(): Boolean {
            val record: Record = try {
                readRecord()
            } catch (closed: EOFException) {
                return false
            }
            if (record.type == ALERT) return false
            check(record.type == APPLICATION_DATA) { "unexpected record type ${record.type}" }
            applicationRecords++
            pending.write(record.body)
            return true
        }

        private fun drainUntilClosed(): Unit {
            while (input.read() >= 0) {
                continue
            }
        }

        private fun decrypt(type: Int, body: ByteArray): ByteArray {
            val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(CLIENT_KEY), IvParameterSpec(body, 0, BLOCK))
            val plain: ByteArray = cipher.doFinal(body, BLOCK, body.size - BLOCK)
            val padding: Int = plain.last().toInt() and 0xff
            val macStart: Int = plain.size - padding - 1 - MAC_LENGTH
            check((plain.size - padding - 1 until plain.size).all { (plain[it].toInt() and 0xff) == padding }) {
                "client record padding is malformed"
            }
            val content: ByteArray = plain.copyOfRange(0, macStart)
            val expected: ByteArray = mac(CLIENT_MAC, readSequence++, type, content)
            check(MessageDigest.isEqual(expected, plain.copyOfRange(macStart, macStart + MAC_LENGTH))) {
                "client record MAC does not verify"
            }
            return content
        }

        private fun writeEncrypted(type: Int, content: ByteArray, forge: Boolean = false): Unit {
            val mac: ByteArray = mac(SERVER_MAC, writeSequence++, type, content)
            if (forge) mac[0] = (mac[0].toInt() xor 0x5a).toByte()
            val padding: Int = BLOCK - (content.size + MAC_LENGTH + 1) % BLOCK
            val plain: ByteArray = content + mac + ByteArray(padding + 1) { padding.toByte() }
            val iv = ByteArray(BLOCK).also { random.nextBytes(it) }
            val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(SERVER_KEY), IvParameterSpec(iv))
            writeRecord(type, iv + cipher.doFinal(plain))
        }

        private fun mac(which: Int, sequence: Long, type: Int, content: ByteArray): ByteArray {
            val header = ByteArrayOutputStream().apply {
                for (shift in 56 downTo 0 step 8) write((sequence ushr shift).toInt())
                write(type)
                write(byteArrayOf(3, 3))
                writeShort(content.size)
            }.toByteArray()
            val offset: Int = if (which == CLIENT_MAC) 0 else MAC_LENGTH
            return Mac.getInstance("HmacSHA1").apply {
                init(SecretKeySpec(keyBlock, offset, MAC_LENGTH, "HmacSHA1"))
                update(header)
            }.doFinal(content)
        }

        private fun key(which: Int): SecretKeySpec {
            val length: Int = suite.keyStrengthInBytes
            val offset: Int = 2 * MAC_LENGTH + if (which == CLIENT_KEY) 0 else length
            return SecretKeySpec(keyBlock, offset, length, "AES")
        }

        private fun writeRecord(type: Int, body: ByteArray): Unit {
            output.write(byteArrayOf(type.toByte(), 3, 3, (body.size shr 8).toByte(), body.size.toByte()) + body)
            output.flush()
        }

        private fun handshake(type: Int, body: ByteArray): ByteArray {
            val message: ByteArray = ByteArrayOutputStream().apply {
                write(type)
                writeLength24(body.size)
                write(body)
            }.toByteArray()
            transcript.write(message)
            return message
        }

        private fun transcriptHash(): ByteArray = MessageDigest.getInstance("SHA-256").digest(transcript.toByteArray())

        override fun close(): Unit {
            serverSocket.close()
            worker.join(TIMEOUT_MILLIS)
        }
    }

    private companion object {
        const val ALIAS: String = "metering-head-end"
        const val PASSWORD: String = "changeit"
        const val TIMEOUT_MILLIS: Long = 30_000

        const val CHANGE_CIPHER_SPEC: Int = 20
        const val ALERT: Int = 21
        const val HANDSHAKE: Int = 22
        const val APPLICATION_DATA: Int = 23
        const val CLIENT_HELLO: Int = 1
        const val SERVER_HELLO: Int = 2
        const val CERTIFICATE: Int = 11
        const val SERVER_HELLO_DONE: Int = 14
        const val CLIENT_KEY_EXCHANGE: Int = 16
        const val FINISHED: Int = 20

        const val BLOCK: Int = 16
        const val MAC_LENGTH: Int = 20
        const val CLIENT_MAC: Int = 0
        const val SERVER_MAC: Int = 1
        const val CLIENT_KEY: Int = 0
        const val SERVER_KEY: Int = 1

        val READINGS: Map<String, String> = mapOf("meter-17" to "4821.6 kWh", "meter-42" to "1307.2 kWh")

        /** A tariff table large enough that the client splits it across several TLS records. */
        fun tariffTable(): ByteArray = (0 until 1200)
            .joinToString("") { slot -> "slot-%04d;%02d:%02d;%d\n".format(slot, slot / 50, slot % 60, slot * 17) }
            .utf8()

        fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

        fun ByteArrayOutputStream.writeShort(value: Int): Unit {
            write(value shr 8)
            write(value)
        }

        fun ByteArrayOutputStream.writeLength24(value: Int): Unit {
            write(value shr 16)
            write(value shr 8)
            write(value)
        }

        /** Reads the cipher suite codes the client offered in its ClientHello [body]. */
        fun offeredSuites(body: ByteArray): List<Short> {
            val sessionIdLength: Int = body[34].toInt() and 0xff
            val suitesStart: Int = 35 + sessionIdLength
            val suitesLength: Int = ((body[suitesStart].toInt() and 0xff) shl 8) or (body[suitesStart + 1].toInt() and 0xff)
            return (0 until suitesLength / 2).map { index ->
                val at: Int = suitesStart + 2 + 2 * index
                (((body[at].toInt() and 0xff) shl 8) or (body[at + 1].toInt() and 0xff)).toShort()
            }
        }

        /** The TLS 1.2 pseudo-random function, P_SHA256 over [label] and [seed]. */
        fun prf(secret: ByteArray, label: String, seed: ByteArray, length: Int): ByteArray {
            val mac: Mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
            val labelledSeed: ByteArray = label.toByteArray(StandardCharsets.US_ASCII) + seed
            val result = ByteArrayOutputStream()
            var a: ByteArray = labelledSeed
            while (result.size() < length) {
                a = mac.doFinal(a)
                mac.update(a)
                result.write(mac.doFinal(labelledSeed))
            }
            return result.toByteArray().copyOf(length)
        }

        /** Trusts exactly the certificates held in [keyStore], as a pinned client would. */
        fun trustManagerFor(keyStore: KeyStore): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .single()
    }
}
