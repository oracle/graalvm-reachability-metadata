/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpVersion
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import java.net.http.HttpClient as JdkHttpClient

/**
 * HTTPS with a custom trust store and HTTP/2 negotiated over TLS through the Java engine
 * configuration, as a budgeting application fetches statements from a bank API whose certificate
 * is signed by a private authority the application ships.
 */
public class BankStatementTlsTest {

    /** The bank API: serves statements over TLS only, with a certificate no public authority signed. */
    class BankApi : AutoCloseable {
        /** The bank's key store; its certificate is what a client must trust to reach the API. */
        val keyStore: KeyStore = buildKeyStore {
            certificate(KEY_ALIAS) {
                password = KEY_PASSWORD
                domains = listOf("localhost")
                ipAddresses = listOf(InetAddress.getByName("127.0.0.1"))
            }
        }

        /** The scheme and protocol each request reached the bank with, in arrival order. */
        val arrivals: MutableList<String> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(Netty, configure = {
            sslConnector(
                keyStore = keyStore,
                keyAlias = KEY_ALIAS,
                keyStorePassword = { KEY_PASSWORD.toCharArray() },
                privateKeyPassword = { KEY_PASSWORD.toCharArray() },
            ) {
                host = "127.0.0.1"
                port = 0
            }
        }) {
            routing {
                get("/accounts/{account}/statements/{month}") {
                    arrivals += "${call.request.origin.scheme} ${call.request.httpVersion}"
                    val account: String = call.parameters["account"]!!
                    val month: String = call.parameters["month"]!!
                    call.respondText(statement(account, month), ContentType.Text.CSV)
                }
                post("/accounts/{account}/disputes") {
                    arrivals += "${call.request.origin.scheme} ${call.request.httpVersion}"
                    val reason: String = call.receiveText()
                    call.respondText(
                        "dispute opened for ${call.parameters["account"]}: $reason",
                        status = HttpStatusCode.Created,
                    )
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    @Test
    fun fetchesStatementsOverHttp2WhenTheBankCertificateIsTrusted(): Unit = runBlocking {
        BankApi().use { bank: BankApi ->
            val client: HttpClient = HttpClient(Java) {
                engine {
                    protocolVersion = JdkHttpClient.Version.HTTP_2
                    config { sslContext(trusting(bank.keyStore)) }
                }
                defaultRequest { url("https://127.0.0.1:${bank.port}/") }
            }
            client.use {
                val march: HttpResponse = client.get("accounts/CH-4471/statements/2026-03")
                assertThat(march.status).isEqualTo(HttpStatusCode.OK)
                assertThat(march.version).isEqualTo(HttpProtocolVersion.HTTP_2_0)
                assertThat(march.bodyAsText()).isEqualTo(statement("CH-4471", "2026-03"))

                val dispute: HttpResponse = client.post("accounts/CH-4471/disputes") {
                    setBody("duplicate card charge on 2026-03-14")
                }
                assertThat(dispute.status).isEqualTo(HttpStatusCode.Created)
                assertThat(dispute.version).isEqualTo(HttpProtocolVersion.HTTP_2_0)
                assertThat(dispute.bodyAsText())
                    .isEqualTo("dispute opened for CH-4471: duplicate card charge on 2026-03-14")
            }
            assertThat(bank.arrivals).containsExactly("https HTTP/2", "https HTTP/2")
        }
    }

    @Test
    fun pinsHttp11OverTlsWhenTheClientAsksForIt(): Unit = runBlocking {
        BankApi().use { bank: BankApi ->
            val client: HttpClient = HttpClient(Java) {
                engine {
                    protocolVersion = JdkHttpClient.Version.HTTP_1_1
                    config { sslContext(trusting(bank.keyStore)) }
                }
                defaultRequest { url("https://127.0.0.1:${bank.port}/") }
            }
            client.use {
                val april: HttpResponse = client.get("accounts/DE-9020/statements/2026-04")
                assertThat(april.status).isEqualTo(HttpStatusCode.OK)
                assertThat(april.version).isEqualTo(HttpProtocolVersion.HTTP_1_1)
                assertThat(april.bodyAsText()).isEqualTo(statement("DE-9020", "2026-04"))
            }
            assertThat(bank.arrivals).containsExactly("https HTTP/1.1")
        }
    }

    @Test
    fun refusesTheBankWhenItsCertificateIsNotTrusted(): Unit = runBlocking {
        BankApi().use { bank: BankApi ->
            // Without the bank's certificate, only the platform's public authorities are trusted.
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url("https://127.0.0.1:${bank.port}/") }
            }
            client.use {
                val failure: Throwable? = runCatching { client.get("accounts/CH-4471/statements/2026-03") }
                    .exceptionOrNull()
                assertThat(failure).isNotNull()
                assertThat(generateSequence(failure) { it.cause }.toList())
                    .anyMatch { it is SSLHandshakeException }
            }
            assertThat(bank.arrivals).isEmpty()
        }
    }

    private companion object {
        const val KEY_ALIAS: String = "bank-api"
        const val KEY_PASSWORD: String = "vault-pass"

        /** A TLS context that trusts exactly the certificates in [keyStore]. */
        fun trusting(keyStore: KeyStore): SSLContext {
            val trustManagers: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagers.init(keyStore)
            val context: SSLContext = SSLContext.getInstance("TLS")
            context.init(null, trustManagers.trustManagers, null)
            return context
        }

        fun statement(account: String, month: String): String =
            (1..5).joinToString("\n", prefix = "account,month,day,amount\n") { day: Int ->
                "$account,$month,$day,${day * 125 - 40}.50"
            }
    }
}
