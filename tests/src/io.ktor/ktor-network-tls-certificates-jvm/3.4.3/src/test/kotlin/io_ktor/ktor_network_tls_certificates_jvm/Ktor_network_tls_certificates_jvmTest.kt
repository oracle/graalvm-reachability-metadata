/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_network_tls_certificates_jvm

import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.generateCertificate
import io.ktor.network.tls.certificates.saveToFile
import io.ktor.network.tls.certificates.trustManagers
import io.ktor.network.tls.certificates.trustStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetAddress
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

public class KtorNetworkTlsCertificatesJvmTest {
    @Test
    fun generateCertificateCreatesSelfSignedServerKeyStoreAndPersistsIt(@TempDir temporaryDirectory: Path): Unit {
        val keyStoreFile: File = temporaryDirectory.resolve("generated-server-keystore.p12").toFile()
        val keyStore: KeyStore = generateCertificate(
            file = keyStoreFile,
            algorithm = "SHA256withRSA",
            keyAlias = SERVER_ALIAS,
            keyPassword = SERVER_PASSWORD,
            jksPassword = STORE_PASSWORD,
            keySizeInBits = TEST_KEY_SIZE,
            keyType = KeyType.Server,
        )

        assertThat(keyStoreFile).isFile()
        assertThat(keyStoreFile.length()).isGreaterThan(0L)
        assertThat(keyStore.aliasNames()).containsExactlyInAnyOrder(SERVER_ALIAS, serverCertificateAlias())
        assertThat(keyStore.isKeyEntry(SERVER_ALIAS)).isTrue()
        assertThat(keyStore.isCertificateEntry(serverCertificateAlias())).isTrue()

        val privateKey: PrivateKey = keyStore.getKey(SERVER_ALIAS, SERVER_PASSWORD.toCharArray()) as PrivateKey
        assertThat(privateKey.algorithm).isEqualTo("RSA")

        val certificate: X509Certificate = keyStore.x509Certificate(SERVER_ALIAS)
        certificate.checkValidity()
        certificate.verify(certificate.publicKey)
        assertThat(certificate.subjectX500Principal.name).contains("CN=localhost")
        assertThat(certificate.issuerX500Principal).isEqualTo(certificate.subjectX500Principal)
        assertThat(certificate.basicConstraints).isEqualTo(-1)
        assertThat(certificate.extendedKeyUsage).contains(SERVER_AUTH_OID)
        assertThat(certificate.subjectAlternativeNamesOfType(DNS_ALT_NAME)).contains("localhost", "127.0.0.1")
        assertThat(certificate.subjectAlternativeNamesOfType(IP_ALT_NAME)).contains("127.0.0.1")

        val reloadedKeyStore: KeyStore = loadKeyStore(keyStoreFile, STORE_PASSWORD)
        assertThat(reloadedKeyStore.aliasNames()).contains(SERVER_ALIAS, serverCertificateAlias())
        assertThat(reloadedKeyStore.x509Certificate(SERVER_ALIAS).encoded).isEqualTo(certificate.encoded)
    }

    @Test
    fun buildKeyStoreDslCreatesConfiguredServerAndClientCertificates(): Unit {
        val keyStore: KeyStore = buildKeyStore {
            certificate("custom-server") {
                password = SERVER_PASSWORD
                subject = X500Principal("CN=service.example.test, OU=Ktor Tests, O=GraalVM, C=US")
                keySizeInBits = TEST_KEY_SIZE
                keyType = KeyType.Server
                domains = listOf("service.example.test", "localhost")
                ipAddresses = listOf(InetAddress.getByName("127.0.0.2"))
                daysValid = 5
            }
            certificate("custom-client") {
                password = CLIENT_PASSWORD
                subject = X500Principal("CN=client.example.test, OU=Ktor Tests, O=GraalVM, C=US")
                keySizeInBits = TEST_KEY_SIZE
                keyType = KeyType.Client
            }
        }

        assertThat(keyStore.aliasNames()).containsExactlyInAnyOrder("custom-server", "custom-client")
        assertThat(keyStore.getKey("custom-server", SERVER_PASSWORD.toCharArray()).algorithm).isEqualTo("RSA")
        assertThat(keyStore.getKey("custom-client", CLIENT_PASSWORD.toCharArray()).algorithm).isEqualTo("RSA")

        val serverCertificate: X509Certificate = keyStore.x509Certificate("custom-server")
        serverCertificate.verify(serverCertificate.publicKey)
        assertThat(serverCertificate.subjectX500Principal.name).contains("CN=service.example.test")
        assertThat(serverCertificate.issuerX500Principal).isEqualTo(serverCertificate.subjectX500Principal)
        assertThat(serverCertificate.extendedKeyUsage).contains(SERVER_AUTH_OID)
        assertThat(serverCertificate.subjectAlternativeNamesOfType(DNS_ALT_NAME))
            .contains("service.example.test", "localhost")
        assertThat(serverCertificate.subjectAlternativeNamesOfType(IP_ALT_NAME)).contains("127.0.0.2")

        val clientCertificate: X509Certificate = keyStore.x509Certificate("custom-client")
        clientCertificate.verify(clientCertificate.publicKey)
        assertThat(clientCertificate.subjectX500Principal.name).contains("CN=client.example.test")
        assertThat(clientCertificate.extendedKeyUsage).contains(CLIENT_AUTH_OID)
        assertThat(clientCertificate.basicConstraints).isEqualTo(-1)
    }

    @Test
    fun caSignedCertificateBuildsVerifiableChainTrustStoreAndTrustManagers(@TempDir temporaryDirectory: Path): Unit {
        val caKeyStore: KeyStore = generateCertificate(
            algorithm = "SHA256withRSA",
            keyAlias = CA_ALIAS,
            keyPassword = CA_PASSWORD,
            keySizeInBits = TEST_KEY_SIZE,
            keyType = KeyType.CA,
        )
        val serviceKeyStoreFile: File = temporaryDirectory.resolve("service-keystore.p12").toFile()
        val serviceKeyStore: KeyStore = caKeyStore.generateCertificate(
            file = serviceKeyStoreFile,
            algorithm = "SHA256withRSA",
            keyAlias = "service",
            keyPassword = SERVER_PASSWORD,
            jksPassword = STORE_PASSWORD,
            keySizeInBits = TEST_KEY_SIZE,
            caKeyAlias = CA_ALIAS,
            caPassword = CA_PASSWORD,
            keyType = KeyType.Server,
        )

        val caCertificate: X509Certificate = caKeyStore.x509Certificate(CA_ALIAS)
        assertThat(caCertificate.basicConstraints).isGreaterThanOrEqualTo(0)
        caCertificate.verify(caCertificate.publicKey)

        val certificateChain: Array<out Certificate> = serviceKeyStore.getCertificateChain("service")
        assertThat(certificateChain).hasSize(2)
        val serviceCertificate: X509Certificate = certificateChain[0] as X509Certificate
        val issuerCertificate: X509Certificate = certificateChain[1] as X509Certificate
        assertThat(issuerCertificate.encoded).isEqualTo(caCertificate.encoded)
        assertThat(serviceCertificate.issuerX500Principal).isEqualTo(caCertificate.subjectX500Principal)
        assertThat(serviceCertificate.subjectX500Principal.name).contains("CN=localhost")
        serviceCertificate.verify(caCertificate.publicKey)
        assertThat(serviceCertificate.extendedKeyUsage).contains(SERVER_AUTH_OID)

        val trustStoreFile: File = temporaryDirectory.resolve("trust-store.jks").toFile()
        val trustStore: KeyStore = caKeyStore.trustStore(file = trustStoreFile, password = TRUST_STORE_PASSWORD)
        assertThat(trustStoreFile).isFile()
        assertThat(trustStore.aliasNames()).contains(CA_ALIAS, caCertificateAlias())
        assertThat(trustStore.isCertificateEntry(CA_ALIAS)).isTrue()
        assertThat(trustStore.getKey(CA_ALIAS, CA_PASSWORD.toCharArray())).isNull()

        val x509TrustManager: X509TrustManager = trustStore.trustManagers.filterIsInstance<X509TrustManager>().single()
        x509TrustManager.checkServerTrusted(arrayOf(serviceCertificate, issuerCertificate), "RSA")

        val reloadedServiceKeyStore: KeyStore = loadKeyStore(serviceKeyStoreFile, STORE_PASSWORD)
        assertThat(reloadedServiceKeyStore.x509Certificate("service").encoded).isEqualTo(serviceCertificate.encoded)
        val reloadedTrustStore: KeyStore = loadKeyStore(trustStoreFile, String(TRUST_STORE_PASSWORD), "JKS")
        assertThat(reloadedTrustStore.x509Certificate(CA_ALIAS).encoded).isEqualTo(caCertificate.encoded)
    }

    @Test
    fun buildKeyStoreDslSignsCertificateWithIssuerCertificateSubject(): Unit {
        val caAlias: String = "builder-ca"
        val serviceAlias: String = "builder-signed-service"
        val caSubject: X500Principal = X500Principal("CN=builder-ca.example.test, OU=Ktor Tests, O=GraalVM, C=US")
        val caKeyStore: KeyStore = buildKeyStore {
            certificate(caAlias) {
                password = CA_PASSWORD
                subject = caSubject
                keySizeInBits = TEST_KEY_SIZE
                keyType = KeyType.CA
                daysValid = 5
            }
        }
        val caCertificate: X509Certificate = caKeyStore.x509Certificate(caAlias)
        val caKeyPair: KeyPair = KeyPair(
            caCertificate.publicKey,
            caKeyStore.getKey(caAlias, CA_PASSWORD.toCharArray()) as PrivateKey,
        )

        val serviceKeyStore: KeyStore = buildKeyStore {
            certificate(serviceAlias) {
                password = SERVER_PASSWORD
                subject = X500Principal("CN=builder-signed.example.test, OU=Ktor Tests, O=GraalVM, C=US")
                keySizeInBits = TEST_KEY_SIZE
                keyType = KeyType.Server
                domains = listOf("builder-signed.example.test")
                ipAddresses = listOf(InetAddress.getByName("127.0.0.3"))
                signWith(caKeyPair, caCertificate)
            }
        }

        assertThat(caCertificate.subjectX500Principal).isEqualTo(caSubject)
        assertThat(caCertificate.issuerX500Principal).isEqualTo(caSubject)
        assertThat(caCertificate.basicConstraints).isGreaterThanOrEqualTo(0)
        caCertificate.verify(caCertificate.publicKey)

        val certificateChain: Array<out Certificate> = serviceKeyStore.getCertificateChain(serviceAlias)
        assertThat(certificateChain).hasSize(2)
        val serviceCertificate: X509Certificate = certificateChain[0] as X509Certificate
        val issuerCertificate: X509Certificate = certificateChain[1] as X509Certificate
        assertThat(issuerCertificate.encoded).isEqualTo(caCertificate.encoded)
        assertThat(serviceCertificate.subjectX500Principal.name).contains("CN=builder-signed.example.test")
        assertThat(serviceCertificate.issuerX500Principal).isEqualTo(caSubject)
        assertThat(serviceCertificate.basicConstraints).isEqualTo(-1)
        assertThat(serviceCertificate.extendedKeyUsage).contains(SERVER_AUTH_OID)
        assertThat(serviceCertificate.subjectAlternativeNamesOfType(DNS_ALT_NAME))
            .contains("builder-signed.example.test")
        assertThat(serviceCertificate.subjectAlternativeNamesOfType(IP_ALT_NAME)).contains("127.0.0.3")
        serviceCertificate.verify(caCertificate.publicKey)
    }

    @Test
    fun saveToFileWritesReloadableKeyStoreCreatedByBuilder(@TempDir temporaryDirectory: Path): Unit {
        val keyStore: KeyStore = buildKeyStore {
            certificate("persisted-client") {
                password = CLIENT_PASSWORD
                subject = X500Principal("CN=persisted-client.example.test, OU=Ktor Tests, O=GraalVM, C=US")
                keySizeInBits = TEST_KEY_SIZE
                keyType = KeyType.Client
            }
        }
        val keyStoreFile: File = temporaryDirectory.resolve("builder-keystore.p12").toFile()

        keyStore.saveToFile(keyStoreFile, STORE_PASSWORD)

        assertThat(keyStoreFile).isFile()
        assertThat(keyStoreFile.length()).isGreaterThan(0L)
        val reloadedKeyStore: KeyStore = loadKeyStore(keyStoreFile, STORE_PASSWORD)
        assertThat(reloadedKeyStore.aliasNames()).containsExactly("persisted-client")
        assertThat(reloadedKeyStore.getKey("persisted-client", CLIENT_PASSWORD.toCharArray()).algorithm)
            .isEqualTo("RSA")
        val certificate: X509Certificate = reloadedKeyStore.x509Certificate("persisted-client")
        certificate.checkValidity()
        assertThat(certificate.subjectX500Principal.name).contains("CN=persisted-client.example.test")
        assertThat(certificate.extendedKeyUsage).contains(CLIENT_AUTH_OID)
    }

    private fun loadKeyStore(file: File, password: String, type: String = KeyStore.getDefaultType()): KeyStore {
        val keyStore: KeyStore = KeyStore.getInstance(type)
        file.inputStream().use { inputStream ->
            keyStore.load(inputStream, password.toCharArray())
        }
        return keyStore
    }

    private fun KeyStore.x509Certificate(alias: String): X509Certificate = getCertificate(alias) as X509Certificate

    private fun KeyStore.aliasNames(): Set<String> = aliases().asSequence().toSet()

    private fun serverCertificateAlias(): String = "$SERVER_ALIAS$CERT_ALIAS_SUFFIX".lowercase()

    private fun caCertificateAlias(): String = "$CA_ALIAS$CERT_ALIAS_SUFFIX".lowercase()

    private fun X509Certificate.subjectAlternativeNamesOfType(type: Int): List<String> =
        subjectAlternativeNames.orEmpty()
            .filter { alternativeName -> alternativeName.firstOrNull() == type }
            .map { alternativeName -> alternativeName[1].toString() }

    private companion object {
        const val TEST_KEY_SIZE: Int = 1024
        const val SERVER_ALIAS: String = "server"
        const val CA_ALIAS: String = "ca"
        const val CERT_ALIAS_SUFFIX: String = "Cert"
        const val SERVER_PASSWORD: String = "server-password"
        const val CLIENT_PASSWORD: String = "client-password"
        const val CA_PASSWORD: String = "ca-password"
        const val STORE_PASSWORD: String = "store-password"
        val TRUST_STORE_PASSWORD: CharArray = "trust-store-password".toCharArray()
        const val DNS_ALT_NAME: Int = 2
        const val IP_ALT_NAME: Int = 7
        const val SERVER_AUTH_OID: String = "1.3.6.1.5.5.7.3.1"
        const val CLIENT_AUTH_OID: String = "1.3.6.1.5.5.7.3.2"
    }
}
