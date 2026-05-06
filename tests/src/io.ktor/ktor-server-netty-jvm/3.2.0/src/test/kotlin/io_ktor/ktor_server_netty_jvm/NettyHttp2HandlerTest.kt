/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_netty_jvm

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.network.tls.certificates.generateCertificate
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.http.push
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.UseHttp2Push
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

public class NettyHttp2HandlerTest {
    @OptIn(UseHttp2Push::class)
    @Test
    fun `netty http2 push promise sets child stream id and property`() {
        val keyStorePassword: String = "changeit"
        val keyAlias: String = "netty-test-key"
        val keyStore: KeyStore = generateCertificate(
            keyAlias = keyAlias,
            keyPassword = keyStorePassword,
            jksPassword = keyStorePassword,
            algorithm = "SHA256withRSA",
            keySizeInBits = 2048,
        )

        withSecureNettyServer(
            keyAlias = keyAlias,
            keyStorePassword = keyStorePassword,
            keyStore = keyStore,
            module = {
                routing {
                    get("/") {
                        call.push("/pushed-resource")
                        call.respondText("root-response", ContentType.Text.Plain)
                    }
                    get("/pushed-resource") {
                        call.respondText("pushed-response", ContentType.Text.Plain)
                    }
                }
            },
        ) { baseUri: URI ->
            val executor: ExecutorService = Executors.newSingleThreadExecutor()
            val client: HttpClient = newHttp2Client(executor)
            try {
                val pushedPaths: LinkedBlockingQueue<String> = LinkedBlockingQueue()
                val pushHandler: HttpResponse.PushPromiseHandler<String> = HttpResponse.PushPromiseHandler {
                    _: HttpRequest,
                    pushPromiseRequest: HttpRequest,
                    _ ->

                    pushedPaths.offer(pushPromiseRequest.uri().path)
                }

                val mainResponse: HttpResponse<String> = client.sendAsync(
                    request(baseUri.resolve("/")).build(),
                    HttpResponse.BodyHandlers.ofString(),
                    pushHandler,
                ).get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                val pushedPath: String = pushedPaths.poll(
                    REQUEST_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS,
                ) ?: error("HTTP/2 push promise was not received")

                assertThat(mainResponse.version()).isEqualTo(HttpClient.Version.HTTP_2)
                assertThat(mainResponse.statusCode()).isEqualTo(HttpStatusCode.OK.value)
                assertThat(mainResponse.body()).isEqualTo("root-response")
                assertThat(pushedPath).isEqualTo("/pushed-resource")
            } finally {
                client.shutdownNow()
                executor.shutdownNow()
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
            }
        }
    }

    private fun withSecureNettyServer(
        keyAlias: String,
        keyStorePassword: String,
        keyStore: KeyStore,
        module: suspend Application.() -> Unit,
        test: (URI) -> Unit,
    ) {
        val port: Int = findAvailablePort()
        val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> = embeddedServer(
            Netty,
            configure = {
                enableHttp2 = true
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = keyAlias,
                    keyStorePassword = { keyStorePassword.toCharArray() },
                    privateKeyPassword = { keyStorePassword.toCharArray() },
                ) {
                    host = LOOPBACK_HOST
                    this.port = port
                }
            },
            module = module,
        )

        server.start(wait = false)
        try {
            test(URI("https://$LOOPBACK_HOST:$port"))
        } finally {
            server.stop(0L, 5_000L)
        }
    }

    private fun newHttp2Client(executor: ExecutorService): HttpClient = HttpClient.newBuilder()
        .connectTimeout(REQUEST_TIMEOUT)
        .executor(executor)
        .sslContext(trustingSslContext())
        .version(HttpClient.Version.HTTP_2)
        .build()

    private fun request(uri: URI): HttpRequest.Builder = HttpRequest.newBuilder(uri)
        .timeout(REQUEST_TIMEOUT)
        .version(HttpClient.Version.HTTP_2)
        .GET()

    private fun trustingSslContext(): SSLContext {
        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(TrustAllCertificates), SecureRandom())
        return sslContext
    }

    private fun findAvailablePort(): Int = ServerSocket().use { socket: ServerSocket ->
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), 0))
        socket.localPort
    }

    private object TrustAllCertificates : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String): Unit = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String): Unit = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        private const val LOOPBACK_HOST: String = "127.0.0.1"
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
