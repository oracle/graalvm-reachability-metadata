/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_tls_jvm

import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.tls.CIOCipherSuites
import io.ktor.network.tls.CipherType
import io.ktor.network.tls.NoPrivateKeyException
import io.ktor.network.tls.OID
import io.ktor.network.tls.SecretExchangeType
import io.ktor.network.tls.ServerKeyExchangeType
import io.ktor.network.tls.TLSAlertLevel
import io.ktor.network.tls.TLSAlertType
import io.ktor.network.tls.TLSConfigBuilder
import io.ktor.network.tls.TLSHandshakeType
import io.ktor.network.tls.TLSRecordType
import io.ktor.network.tls.TLSVersion
import io.ktor.network.tls.addCertificateChain
import io.ktor.network.tls.addKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.HashAndSign
import io.ktor.network.tls.extensions.NamedCurve
import io.ktor.network.tls.extensions.PointFormat
import io.ktor.network.tls.extensions.SignatureAlgorithm
import io.ktor.network.tls.extensions.SupportedNamedCurves
import io.ktor.network.tls.extensions.SupportedPointFormats
import io.ktor.network.tls.extensions.SupportedSignatureAlgorithms
import io.ktor.network.tls.extensions.TLSExtensionType
import io.ktor.network.tls.extensions.byCode
import io.ktor.network.tls.keysGenerationAlgorithm
import io.ktor.network.tls.takeFrom
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.reader
import io.ktor.utils.io.writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CancellationException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

public class Ktor_network_tls_jvmTest {
    @Test
    fun cipherSuitesExposeTlsParametersAndPlatformSupportedList(): Unit {
        val suite = CIOCipherSuites.ECDHE_RSA_AES128_SHA256

        assertThat(suite.code).isEqualTo(0xc02f.toShort())
        assertThat(suite.name).isEqualTo("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
        assertThat(suite.openSSLName).isEqualTo("ECDHE-RSA-AES128-GCM-SHA256")
        assertThat(suite.exchangeType).isEqualTo(SecretExchangeType.ECDHE)
        assertThat(suite.exchangeType.jvmName).isEqualTo("ECDHE_ECDSA")
        assertThat(suite.jdkCipherName).isEqualTo("AES/GCM/NoPadding")
        assertThat(suite.keyStrength).isEqualTo(128)
        assertThat(suite.keyStrengthInBytes).isEqualTo(16)
        assertThat(suite.fixedIvLength).isEqualTo(4)
        assertThat(suite.ivLength).isEqualTo(12)
        assertThat(suite.cipherTagSizeInBytes).isEqualTo(16)
        assertThat(suite.macName).isEqualTo("AEAD")
        assertThat(suite.macStrengthInBytes).isZero()
        assertThat(suite.hash).isEqualTo(HashAlgorithm.SHA256)
        assertThat(suite.signatureAlgorithm).isEqualTo(SignatureAlgorithm.RSA)
        assertThat(suite.cipherType).isEqualTo(CipherType.GCM)
        assertThat(CIOCipherSuites.SupportedSuites).contains(suite)
        assertThat(CIOCipherSuites.SupportedSuites).allSatisfy { supported ->
            assertThat(supported.keyStrengthInBytes).isEqualTo(supported.keyStrength / 8)
            assertThat(supported.macStrengthInBytes).isEqualTo(supported.macStrength / 8)
        }
    }

    @Test
    fun enumCompanionsDecodeValidCodesAndRejectUnknownCodes(): Unit {
        assertThat(TLSVersion.byCode(0x0300)).isEqualTo(TLSVersion.SSL3)
        assertThat(TLSVersion.byCode(0x0303)).isEqualTo(TLSVersion.TLS12)
        assertThat(TLSRecordType.byCode(0x16)).isEqualTo(TLSRecordType.Handshake)
        assertThat(TLSHandshakeType.byCode(0x10)).isEqualTo(TLSHandshakeType.ClientKeyExchange)
        assertThat(ServerKeyExchangeType.byCode(3)).isEqualTo(ServerKeyExchangeType.NamedCurve)
        assertThat(TLSAlertLevel.byCode(2)).isEqualTo(TLSAlertLevel.FATAL)
        assertThat(TLSAlertType.byCode(48)).isEqualTo(TLSAlertType.UnknownCa)
        assertThat(TLSExtensionType.byCode(13)).isEqualTo(TLSExtensionType.SIGNATURE_ALGORITHMS)

        assertThatThrownBy { TLSVersion.byCode(0x0304) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid TLS version code")
        assertThatThrownBy { TLSRecordType.byCode(0xff) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid TLS record type code")
        assertThatThrownBy { TLSHandshakeType.byCode(0xff) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid TLS handshake type code")
        assertThatThrownBy { ServerKeyExchangeType.byCode(-1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid TLS ServerKeyExchange type code")
        assertThatThrownBy { TLSAlertLevel.byCode(3) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid TLS record type code")
        assertThatThrownBy { TLSAlertType.byCode(255) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid TLS record type code")
        assertThatThrownBy { TLSExtensionType.byCode(255) }
            .isInstanceOf(Exception::class.java)
            .hasMessageContaining("Unknown server hello extension type")
    }

    @Test
    fun tlsExtensionsAndSignatureAlgorithmsExposeSupportedValues(): Unit {
        assertThat(NamedCurve.fromCode(23)).isEqualTo(NamedCurve.secp256r1)
        assertThat(NamedCurve.fromCode(Short.MAX_VALUE)).isNull()
        assertThat(NamedCurve.secp384r1.fieldSize).isEqualTo(384)
        assertThat(SupportedNamedCurves).containsExactly(NamedCurve.secp256r1, NamedCurve.secp384r1)
        assertThat(SupportedPointFormats).containsExactly(
            PointFormat.UNCOMPRESSED,
            PointFormat.ANSIX962_COMPRESSED_PRIME,
            PointFormat.ANSIX962_COMPRESSED_CHAR2
        )

        assertThat(HashAlgorithm.byCode(4)).isEqualTo(HashAlgorithm.SHA256)
        assertThat(HashAlgorithm.SHA384.openSSLName).isEqualTo("SHA-384")
        assertThat(HashAlgorithm.SHA384.macName).isEqualTo("HmacSHA384")
        assertThat(SignatureAlgorithm.byCode(1)).isEqualTo(SignatureAlgorithm.RSA)
        assertThat(SignatureAlgorithm.byCode(Byte.MAX_VALUE)).isNull()

        val known = HashAndSign.byCode(HashAlgorithm.SHA256.code, SignatureAlgorithm.RSA.code)
        assertThat(known).isNotNull()
        assertThat(known!!.name).isEqualTo("SHA256withRSA")
        assertThat(known.oid).isEqualTo(OID.RSAwithSHA256Encryption)
        assertThat(SupportedSignatureAlgorithms).contains(known)

        val syntacticallyValidButUnsupported = HashAndSign.byCode(
            HashAlgorithm.SHA512.code,
            SignatureAlgorithm.ECDSA.code
        )
        assertThat(syntacticallyValidButUnsupported).isNotNull()
        assertThat(syntacticallyValidButUnsupported!!.name).isEqualTo("SHA512withECDSA")
        assertThat(syntacticallyValidButUnsupported.oid).isNull()

        assertThatThrownBy { HashAlgorithm.byCode(Byte.MAX_VALUE) }
            .isInstanceOf(Exception::class.java)
            .hasMessageContaining("Unknown hash algorithm")
        assertThatThrownBy { HashAndSign.byCode(HashAlgorithm.SHA256.code, SignatureAlgorithm.ANON.code) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Anonymous signature not allowed")
    }

    @Test
    fun oidAndKeyGenerationHelpersMapStandardAlgorithms(): Unit {
        val rsaEncryption = OID.RSAEncryption
        assertThat(rsaEncryption.identifier).isEqualTo("1 2 840 113549 1 1 1")
        assertThat(rsaEncryption.asArray.toList()).containsExactly(1, 2, 840, 113549, 1, 1, 1)
        assertThat(OID.CommonName.asArray.toList()).containsExactly(2, 5, 4, 3)
        assertThat(OID.fromAlgorithm("SHA384withECDSA")).isEqualTo(OID.ECDSAwithSHA384Encryption)
        assertThat(OID.fromAlgorithm("SHA256withRSA")).isEqualTo(OID.RSAwithSHA256Encryption)

        assertThat(keysGenerationAlgorithm("SHA256withECDSA")).isEqualTo("EC")
        assertThat(keysGenerationAlgorithm("SHA256withDSA")).isEqualTo("DSA")
        assertThat(keysGenerationAlgorithm("SHA256withRSA")).isEqualTo("RSA")
        assertThatThrownBy { OID.fromAlgorithm("SHA3withRSA") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Couldn't find OID")
        assertThatThrownBy { keysGenerationAlgorithm("Ed25519") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Couldn't find KeyPairGenerator algorithm")
    }

    @Test
    fun tlsConfigBuilderProvidesDefaultsAndRejectsUnsupportedTrustManagers(): Unit {
        val defaultConfig = TLSConfigBuilder().build()

        assertThat(defaultConfig.random).isNotNull()
        assertThat(defaultConfig.trustManager).isInstanceOf(X509TrustManager::class.java)
        assertThat(defaultConfig.cipherSuites).containsExactlyElementsOf(CIOCipherSuites.SupportedSuites)
        assertThat(defaultConfig.certificates).isEmpty()
        assertThat(defaultConfig.serverName).isNull()

        assertThatThrownBy {
            TLSConfigBuilder().trustManager = object : TrustManager { }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Only [X509TrustManager] supported")
    }

    @Test
    fun tlsConfigBuilderCopiesExplicitSettingsAndLoadsCertificateChains(): Unit {
        val random = SecureRandom.getInstance("SHA1PRNG")
        val trustManager = trustAllManager()
        val certificate = testCertificate()
        val privateKey = testPrivateKey()
        val source = TLSConfigBuilder().apply {
            this.random = random
            this.trustManager = trustManager
            this.cipherSuites = listOf(CIOCipherSuites.ECDHE_RSA_AES128_SHA256)
            this.serverName = "source.example.test"
            addCertificateChain(arrayOf(certificate), privateKey)
        }

        val copiedConfig = TLSConfigBuilder().apply { takeFrom(source) }.build()

        assertThat(copiedConfig.random).isSameAs(random)
        assertThat(copiedConfig.trustManager).isSameAs(trustManager)
        assertThat(copiedConfig.cipherSuites).containsExactly(CIOCipherSuites.ECDHE_RSA_AES128_SHA256)
        assertThat(copiedConfig.serverName).isEqualTo("source.example.test")
        assertThat(copiedConfig.certificates).hasSize(1)
        assertThat(copiedConfig.certificates.single().certificateChain).containsExactly(certificate)
        assertThat(copiedConfig.certificates.single().key).isSameAs(privateKey)

        val keyStoreConfig = TLSConfigBuilder().apply {
            addKeyStore(testKeyStore(), KEY_PASSWORD, TEST_KEY_ALIAS)
        }.build()

        assertThat(keyStoreConfig.certificates).hasSize(1)
        assertThat(keyStoreConfig.certificates.single().certificateChain.single().subjectX500Principal.name)
            .contains("CN=localhost")
    }

    @Test
    fun keyStoreLoadingFailsForMissingAlias(): Unit {
        assertThatThrownBy {
            TLSConfigBuilder().addKeyStore(testKeyStore(), KEY_PASSWORD, "missing")
        }
            .isInstanceOf(IllegalStateException::class.java)
            .isNotInstanceOf(NoPrivateKeyException::class.java)
            .hasMessageContaining("Fail to get the certificate chain")
    }

    @Test
    fun keyStoreLoadingWithoutExplicitAliasLoadsAllCertificateEntries(): Unit {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(
                "primary",
                testPrivateKey(),
                KEY_PASSWORD,
                arrayOf<java.security.cert.Certificate>(testCertificate())
            )
            setKeyEntry(
                "secondary",
                testPrivateKey(),
                KEY_PASSWORD,
                arrayOf<java.security.cert.Certificate>(testCertificate())
            )
        }

        val config = TLSConfigBuilder().apply {
            addKeyStore(keyStore, KEY_PASSWORD)
        }.build()

        assertThat(config.certificates).hasSize(2)
        assertThat(config.certificates)
            .allSatisfy { certificateAndKey ->
                assertThat(certificateAndKey.certificateChain.single().subjectX500Principal.name)
                    .contains("CN=localhost")
                assertThat(certificateAndKey.key.algorithm).isEqualTo("RSA")
            }
    }

    @Test
    fun tlsClosesUnderlyingSocketWhenHandshakeChannelFails(): Unit = runBlocking {
        val socket = ImmediateEofSocket()
        var failure: Throwable? = null

        try {
            withTimeout(Duration.ofSeconds(5).toMillis()) {
                socket.tls(coroutineContext) {
                    trustManager = trustAllManager()
                    random = SecureRandom.getInstance("SHA1PRNG")
                    cipherSuites = listOf(CIOCipherSuites.ECDHE_RSA_AES128_SHA256)
                    serverName = "localhost"
                }
            }
        } catch (cause: Throwable) {
            failure = cause
        }

        assertThat(failure).isNotNull()
        assertThat(failure).isNotInstanceOf(kotlinx.coroutines.TimeoutCancellationException::class.java)
        assertThat(socket.closeCount).isGreaterThanOrEqualTo(1)
    }

    private class ImmediateEofSocket : Socket {
        private val job = Job()
        private var readChannel: ByteChannel? = null
        private var writeChannel: ByteChannel? = null

        var closeCount: Int = 0
            private set

        override val coroutineContext = Dispatchers.IO + job
        override val socketContext: Job = job
        override val localAddress: SocketAddress = InetSocketAddress("127.0.0.1", 0)
        override val remoteAddress: SocketAddress = InetSocketAddress("127.0.0.1", 443)

        override fun attachForReading(channel: ByteChannel): WriterJob {
            readChannel = channel
            channel.cancel(EOFException("server closed before TLS handshake"))
            return writer(Dispatchers.IO, channel) { }
        }

        override fun attachForWriting(channel: ByteChannel): ReaderJob {
            writeChannel = channel
            channel.cancel(EOFException("client write channel unavailable during TLS handshake"))
            return reader(Dispatchers.IO, channel) { }
        }

        override fun close(): Unit {
            closeCount++
            readChannel?.cancel(CancellationException("socket closed"))
            writeChannel?.cancel(CancellationException("socket closed"))
            job.complete()
        }
    }

    private companion object {
        private const val TEST_KEY_ALIAS = "server"
        private val KEY_PASSWORD: CharArray = "changeit".toCharArray()

        private val SERVER_CERTIFICATE_PEM: String = """
            -----BEGIN CERTIFICATE-----
            MIIDJTCCAg2gAwIBAgIUPPa6hVVTJaywj5yZetvBaz5mVUIwDQYJKoZIhvcNAQEL
            BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDUwNzIwNDUyNFoXDTM2MDUw
            NDIwNDUyNFowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
            AAOCAQ8AMIIBCgKCAQEAkMpij8TxXz2PKnesm9FSH7TKGq6T3f6DyUPmNHohWbSj
            wHAYyDijjfI8zSiqXmJU11pZViUyOTo8uMEAcgyRbrZYvwB+setS+hi2pbq4wOz8
            PHSPoWDDydMnU1y2KHthk+Uu4sTVPxwJGBYMuDTFJR4dh0IA8fJc3gVVyxYgvZgE
            ar0sYt+jY0fiGHTKUujGAfqLmvDKQSvGeIDerB8kgtmO9kR7sgdeTtojPTq0gOba
            WpTIAaRbQ3rFf6adeFCVdv3M/bEddfNg3T4pOmKkKT1F+DYiI7H+Wk8okueWh6ZY
            fp9nykFpSIidskUHn3Mdu99rZZEWYDOC+5pJUrIwwwIDAQABo28wbTAdBgNVHQ4E
            FgQUVkaMHo9a7zPJcT8cm/V80Zgsa9gwHwYDVR0jBBgwFoAUVkaMHo9a7zPJcT8c
            m/V80Zgsa9gwDwYDVR0TAQH/BAUwAwEB/zAaBgNVHREEEzARgglsb2NhbGhvc3SH
            BH8AAAEwDQYJKoZIhvcNAQELBQADggEBABXWme4hV2zDwTZq0+0paflYs+jiezni
            rVIe3UGwLncVimhUmwARuHSZ431zGsmaY7t4i8zK3IVxn+TdTnuwkeAbqyHseg5R
            B6TrQerOZ7oEhP9rrLHbtP13LkikAykB1jwfR7/omd89cvtT/SjY5R+a6vUhRYlr
            yJhBAOLDAHR1vbVOgLpecngzI32PTCWT4W/t390HXY48zu45S9QL1CGVCgAN8ZyY
            77Ekym/dANdQj57FMEU419NEUsi2+6UlYm6V2szlPGRPcChHrinh/LKj+AVaaKIS
            TbHg9IQVGufETJIiVcckUpjIKZoflZAwmyI5mPQmKNKyMKddvJMHmB0=
            -----END CERTIFICATE-----
        """.trimIndent()

        private val SERVER_PRIVATE_KEY_PEM: String = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCQymKPxPFfPY8q
            d6yb0VIftMoarpPd/oPJQ+Y0eiFZtKPAcBjIOKON8jzNKKpeYlTXWllWJTI5Ojy4
            wQByDJFutli/AH6x61L6GLalurjA7Pw8dI+hYMPJ0ydTXLYoe2GT5S7ixNU/HAkY
            Fgy4NMUlHh2HQgDx8lzeBVXLFiC9mARqvSxi36NjR+IYdMpS6MYB+oua8MpBK8Z4
            gN6sHySC2Y72RHuyB15O2iM9OrSA5tpalMgBpFtDesV/pp14UJV2/cz9sR1182Dd
            Pik6YqQpPUX4NiIjsf5aTyiS55aHplh+n2fKQWlIiJ2yRQefcx2732tlkRZgM4L7
            mklSsjDDAgMBAAECggEAAcOdgM/u+vCWkndj0IAz5nP+9GVFIvLLa0PbBa+pQV0M
            k7cp7iKWh4+4gu1oaf77tqYAqaaJXp4hiES9uyYBDZ7GJQmeAY/y8l4jt5A3WQ5q
            IlhvOZGiwQ5ED+V0yLh8H1+u+w9X4811JOh73jCyaDneNTwuI8SGsiPRgEh0PKsd
            TaDgEHCa38+qzwvgauc9BRngCoMCWWWmFlA2n9c5RuwmKDNANk4VliUG+LYPEXwr
            Du3wOErzNnF5l9IwYTmERdwtLti7fufmPq14yUZamikPJWFxDgNAouKK5surEmQ2
            s+txSAijSoso8MdjpPV7KaelivAICJiLWxQG4a2SwQKBgQDAwWvuu3F3abL50Sv6
            OprD685rLv2+mMXRcu/wo8AoynsesDeNgD3HuTWvPmaLBGbsofyoiocPOgnBownZ
            UPKzO5NLYUUhVcS1ZWlZd1YnwF7BMxIfy8jdbcAsiV2pceN5TE1nXx+/l8rC750g
            r5CAkxJTniF+gtb6DCkON4u5wQKBgQDATCAgieF8yxTJ6cUOrsbmPuwfA+XyCi6o
            kg2p4zrgvinVocWf8yzZW0GrtB5zsQCi1FPJeFelCbOwQBGPmlXela51vcqc3OPC
            2OdrA5rfT31wRVpfLAzT74wB3Ca+GQk2rYt8LZoaze6XSyBP+4wDOiy6ng36urYo
            ji1vx3zjgwKBgQC9tuG7U2PHKxJLjNNi8oFW6eT9W3/FMuooTp7X0uOTgk6RktDq
            hVjJFYJAHAOjOc7vgjOB0u5BT1dA7W4JJQHq5G0BmRgISjlUbB63PpxefZkFQHXL
            M7BcN+QYMY8s8fn4beAVKOu/j++x01JsVD++PIKiKBZBRRe/fW5/Hq54QQKBgCwr
            0F1pDqCpzXar+hXrU8jjvz1ImfNFH36dPgI+LfId/GIULN8W7sBm0+jrEOumRu0g
            NLbcq9U/K0VbEi2YWA0u+MoW9Imfu7mwNUhBpbuR+NBnPeEKr0+ngNOUjFmySomC
            x72YhAOQNjQOj7ePopPDMy8Sy0dCyED8l7dLbYadAoGAcchQQ34oJGZXLpDg7T7j
            g9HzNHJILRleHVzXBVKQ8m8igY7a0kOzGY+cVpQyUbDp1dsAj5RsaDd9YTcoDRiw
            SgHeve9rNsmAaXxxZcm9bjiGuik8TLgN6QT6YqPhynu719rfOJjjUDHPcZjYRNBM
            CZcAEPCHQNKWutGvlVz+5A4=
            -----END PRIVATE KEY-----
        """.trimIndent()

        private fun testCertificate(): X509Certificate {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            return certificateFactory.generateCertificate(ByteArrayInputStream(pemBytes(SERVER_CERTIFICATE_PEM)))
                as X509Certificate
        }

        private fun testPrivateKey(): PrivateKey {
            val keySpec = PKCS8EncodedKeySpec(pemBytes(SERVER_PRIVATE_KEY_PEM))
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        }

        private fun testKeyStore(): KeyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(
                TEST_KEY_ALIAS,
                testPrivateKey(),
                KEY_PASSWORD,
                arrayOf<java.security.cert.Certificate>(testCertificate())
            )
        }

        private fun trustAllManager(): X509TrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String): Unit = Unit

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String): Unit = Unit
        }

        private fun pemBytes(pem: String): ByteArray {
            val base64 = pem.lineSequence()
                .filterNot { it.startsWith("-----") }
                .joinToString(separator = "") { it.trim() }
            return Base64.getMimeDecoder().decode(base64)
        }
    }
}
