/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import fs2.io.net.tls.TLSContext
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import scala.concurrent.duration.*

/** Exercises Ember's TLS termination subsystem for certificate-verified HTTPS invoice downloads. */
class HttpsInvoiceDownloadTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  // Public, test-only credentials for the loopback listener, never production credentials.
  private val privateKey: String = """
    MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCNJXPt5uITtay3
    tWKPU0ALWk1DtL9AMEt3kzksLBlxHPFcVVTt+asmhIKG/knD//taumoi6WF0yp2Z
    cGk8dD46lyuymJS8vZXnaKptSwD/gg4+MUa54868MW5jgZexQ10QgOsdegR4ooMf
    yp6A0Nysu/GgGXe6Xmq7ovW6VpmFwWfb8C/Cumz95EoK5hX51awEBvkv/69Uj/wx
    6fJ92+5ZEfiefpTQughScwFNwjVMB3waw+GlEHUWTJluEzT7Q8099RK7RwL+55ho
    VjySX5GXWhu9FeYb2Mq0EdPv5tzONAyU8FP9cWYqCgfiQwVJIpkK3nAabxILhGvH
    GjbGX3kJAgMBAAECggEANEhvTOx9Sfe95r7Vp2mbC5mDjVTikxBQpBZw0x/yvKeP
    8DfbodsdvsLO5tpC0Mt9e/oZXp08cFqtTi0Jjk8wzds5YIqMbe18HZ/zoVBrKjou
    tHLkU+9CoQtebJWQbyUY0AgxD1YnWdg44gnX9LdpRsLHmhpU80AOITsX8tIpRBX3
    X/sFlLrQkJ3lsTfbrjZZ2LFINwDAVl3SLyNF+CpU74+zwen/PhBwEwVrZZNwY6l9
    1T248UZdbMq/pG9EeiTHaLZBRIim6+DKX9Q2mEk07Zsvq/PqM46sA88mv3G+XWmI
    YCpBMO3NDPl50TRDZl1zkNAY4kFpyKOWcwpQ4TM0kQKBgQDAu3Yu1WF24WKb4Zug
    XVm5n7aES+MJR/e4hpT0eIN6ZHvpq9UkWHGqcVoIi/zG7vpGN2ALE8HqbsmfVxDt
    0KZjxxqFKftr5NvfwrqdIKnfbncaT51NyVtEUxj+OWzl3nea/YhYTQ6E/WjkCnog
    0GHmgJyUweyUUnFjyL7NSytiHwKBgQC7euN7uGfLrsQtIx2x/lLyG06iG1FBijBS
    t81otDonq9DzeHxK+EPUqxbhGoiVpN3zjMvAIzheKWMKdwaSJoKRb8q2yq8k/rcU
    zp+P9VGAe3og8kNW0d6W2Hsw7JcXzgi7OPnf+/x76MZbXic/DM0UFQnHlHe8AOUl
    tDGcZkfP1wKBgDj3b+A569wClG30ylYlIX8vSPhjodC9d+gPx3lXIa2Mg9+2jYJ3
    rekuJel+zxFR06HrfjTn+xo68tdpGbThRj0Zvan8lbb3Z1IFMbl9zRggg43AWIOE
    b7iLQ0jpWlm7Xujt5V4MMK14ZYd53U1TqoxCGYYSp8XTdHaFAHiXrlQLAoGBAISo
    dvDjUiaw2kVqFKgO4lhH9EMGe1+ZzYmqXyFYbdIGZIry1VgjBbQk/8J2iPECQL8G
    AGJLSaZvOJJ+GFsv/rG0Xccr8fSGe7uwXP17Q5lbRdasNfPn+s3YDotWza7dbhY0
    QbM41bZDxp9+Jt0ZTL+KB2p+Zp7UL1CXiYc2ZFfvAoGBAKCCXJCFa4E7eGPQfFPg
    K3regqH6XBDQQVkt5aaqbGM9GWPMvWI20b3WDvXzFenhS2fU0clm5j5dVCYo0HZb
    ArrCoWmNLZ07Suv88nIZ0KnaONwKKt2aupkbGCzVpEX3y7Y3/8GCe5zNOLXha4q7
    sMvv8ToiqjzA9etFdBrUD2t1
    """

  private val certificate: String = """-----BEGIN CERTIFICATE-----
    MIIDJzCCAg+gAwIBAgIUU4y6aBly+xxp4A4PNsQXgbO7FBYwDQYJKoZIhvcNAQEL
    BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MCAXDTI2MTAyMDA2NDM0MVoYDzIxMjYw
    OTI2MDY0MzQxWjAUMRIwEAYDVQQDDAlsb2NhbGhvc3QwggEiMA0GCSqGSIb3DQEB
    AQUAA4IBDwAwggEKAoIBAQCNJXPt5uITtay3tWKPU0ALWk1DtL9AMEt3kzksLBlx
    HPFcVVTt+asmhIKG/knD//taumoi6WF0yp2ZcGk8dD46lyuymJS8vZXnaKptSwD/
    gg4+MUa54868MW5jgZexQ10QgOsdegR4ooMfyp6A0Nysu/GgGXe6Xmq7ovW6VpmF
    wWfb8C/Cumz95EoK5hX51awEBvkv/69Uj/wx6fJ92+5ZEfiefpTQughScwFNwjVM
    B3waw+GlEHUWTJluEzT7Q8099RK7RwL+55hoVjySX5GXWhu9FeYb2Mq0EdPv5tzO
    NAyU8FP9cWYqCgfiQwVJIpkK3nAabxILhGvHGjbGX3kJAgMBAAGjbzBtMB0GA1Ud
    DgQWBBT7F6VxWu6aHI0RdPHeMFoAG/wAxjAfBgNVHSMEGDAWgBT7F6VxWu6aHI0R
    dPHeMFoAG/wAxjAPBgNVHRMBAf8EBTADAQH/MBoGA1UdEQQTMBGCCWxvY2FsaG9z
    dIcEfwAAATANBgkqhkiG9w0BAQsFAAOCAQEAE1UPVKXrCginCM7cRh2xMQlPtBcn
    l10q+v0/5qBiXIZPlsHbl2bv/yTNjWX1SNq0lZL8SeilPR9s6IRmcb8xzuvnDjP/
    BUuPeeQnDa8/NXKPPhZkKZIEJSwV3Lzvzi+ENkl7HMTTY9+CD5+hMv0D8nmNOCtI
    XVlPf7QYcvS5MsTaeBzpZAqns723PQ5B7a7tEOpIw39mMqe8dzaItT3Cgn5JE+IS
    XTOl75FT+qHlmTIk8rY2yRFlXy2k6UoCsS/w2QORZUK2yUIkxOgLFHiTvQdfyBL+
    95hYDmpoRMN8rbnm2hu7Ku2cQhi62y3ILc/OGaGRAB8ZGbTmpeszlGEwQQ==
    -----END CERTIFICATE-----"""

  private def tlsContexts(): (SSLContext, SSLContext) = {
    val cert: Certificate = CertificateFactory.getInstance("X.509").generateCertificate(
      new ByteArrayInputStream(certificate.getBytes(StandardCharsets.US_ASCII))
    )
    val key: PrivateKey = KeyFactory.getInstance("RSA").generatePrivate(
      new PKCS8EncodedKeySpec(Base64.getMimeDecoder.decode(privateKey))
    )
    val password: Array[Char] = "test-only".toCharArray
    val identity: KeyStore = KeyStore.getInstance("PKCS12")
    identity.load(null, password)
    identity.setKeyEntry("invoice-service", key, password, Array(cert))
    val keys: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keys.init(identity, password)
    val server: SSLContext = SSLContext.getInstance("TLS")
    server.init(keys.getKeyManagers, null, null)

    // Trust only this fixture certificate, retaining the client's normal hostname verification.
    val trustStore: KeyStore = KeyStore.getInstance("PKCS12")
    trustStore.load(null, null)
    trustStore.setCertificateEntry("invoice-service", cert)
    val trust: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trust.init(trustStore)
    val client: SSLContext = SSLContext.getInstance("TLS")
    client.init(null, trust.getTrustManagers, null)
    (server, client)
  }

  @Test
  @Timeout(60)
  def downloadsAnInvoiceFromAnAuthenticatedHttpsEndpoint(): Unit = {
    val invoice: String = "Invoice INV-42\nCustomer: Tea Shop\nTotal: 120.00 USD\n"
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case request @ GET -> Root / "invoices" / "INV-42" =>
        if (request.isSecure.contains(true)) Ok(invoice)
        else Forbidden("Invoices require an encrypted connection")
    }
    val program: IO[Unit] = IO.blocking(tlsContexts()).flatMap { case (serverTls, clientTls) =>
      EmberServerBuilder.default[IO]
        .withHost(Host.fromString("127.0.0.1").get)
        .withPort(Port.fromInt(0).get)
        .withTLS(TLSContext.Builder.forAsync[IO].fromSSLContext(serverTls))
        .withHttpApp(routes.orNotFound)
        .withShutdownTimeout(10.seconds)
        .build.use { server =>
          Resource.make(IO.blocking {
            HttpClient.newBuilder()
              .sslContext(clientTls)
              .connectTimeout(Duration.ofSeconds(15))
              .version(HttpClient.Version.HTTP_1_1)
              .build()
          })(client => IO.blocking(client.close())).use { client =>
            IO.blocking {
              val address: URI = URI.create(
                s"https://127.0.0.1:${server.address.port.value}/invoices/INV-42"
              )
              val request: HttpRequest = HttpRequest.newBuilder(address)
                .timeout(Duration.ofSeconds(15)).GET().build()
              val response: HttpResponse[String] = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
              assertEquals(200, response.statusCode())
              assertEquals(invoice, response.body())
              assertTrue(response.sslSession().isPresent, "The invoice must arrive over TLS")
              val protocol: String = response.sslSession().get().getProtocol
              assertTrue(Set("TLSv1.2", "TLSv1.3").contains(protocol), protocol)
              assertEquals("CN=localhost", response.sslSession().get().getPeerPrincipal.getName)
            }
          }
        }
    }

    program.timeout(50.seconds).unsafeRunSync()
  }
}
