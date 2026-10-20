/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.time.Duration
import java.util.Base64
import java.util.Properties
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.Forbidden
import play.api.mvc.Results.Ok
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises HTTPS serving through the default SSL engine provider with a configured server keystore. */
class SecureDispatchServerTest {
  @Test
  @Timeout(60)
  def servesAPrivateDispatchManifestOverVerifiedTls(): Unit = {
    val password: Array[Char] = "test-only-password".toCharArray
    val encodedStore: Array[Byte] = Base64.getMimeDecoder.decode(serverKeyStore)
    val serverStore: KeyStore = KeyStore.getInstance("PKCS12")
    val input: ByteArrayInputStream = new ByteArrayInputStream(encodedStore)
    try {
      serverStore.load(input, password)
    } finally {
      input.close()
    }

    // Trust only this test server certificate; retain normal TLS hostname verification.
    val trustStore: KeyStore = KeyStore.getInstance("PKCS12")
    trustStore.load(null, password)
    trustStore.setCertificateEntry("dispatch-server", serverStore.getCertificate("dispatch-server"))
    val trustManagers: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagers.init(trustStore)
    val tls: SSLContext = SSLContext.getInstance("TLS")
    tls.init(null, trustManagers.getTrustManagers, null)

    val path: Path = Files.createTempFile("dispatch-server-", ".p12")
    try {
      Files.write(path, encodedStore)
      val properties: Properties = new Properties()
      properties.setProperty("play.server.https.keyStore.path", path.toString)
      properties.setProperty("play.server.https.keyStore.type", "PKCS12")
      properties.setProperty("play.server.https.keyStore.password", String.valueOf(password))
      val config: ServerConfig = ServerConfig(
        port = None,
        sslPort = Some(0),
        address = "127.0.0.1",
        mode = Mode.Test,
        properties = properties
      )

      Server.withRouterFromComponents(config) { components =>
        {
          case request if request.method == "GET" && request.path == "/dispatch/parcel-42" =>
            components.defaultActionBuilder { incoming =>
              if (incoming.secure) {
                Ok("Private dispatch manifest: parcel-42;destination=north-depot;units=12")
                  .withHeaders("Cache-Control" -> "no-store")
              } else {
                Forbidden("Dispatch manifests require HTTPS")
              }
            }
        }
      } { port =>
        val client: HttpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .sslContext(tls)
          .version(HttpClient.Version.HTTP_1_1)
          .build()
        try {
          val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(s"https://127.0.0.1:${port.value}/dispatch/parcel-42"))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()
          val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())

          assertEquals(200, response.statusCode())
          assertEquals("Private dispatch manifest: parcel-42;destination=north-depot;units=12", response.body())
          assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow())
          val session: SSLSession = response.sslSession().orElseThrow()
          assertTrue(session.getProtocol.startsWith("TLS"))
          assertEquals(serverStore.getCertificate("dispatch-server"), session.getPeerCertificates.head)
        } finally {
          client.close()
        }
      }
    } finally {
      Files.deleteIfExists(path)
    }
  }

  // Test-only EC key and self-signed certificate, valid 2020-2119, with IP SAN 127.0.0.1.
  // Embedded PKCS12 avoids depending on keytool or machine-local certificate files at runtime.
  private val serverKeyStore: String = """MIIEggIBAzCCBCwGCSqGSIb3DQEHAaCCBB0EggQZMIIEFTCCATwGCSqGSIb3DQEHAaCCAS0EggEp
    |MIIBJTCCASEGCyqGSIb3DQEMCgECoIG9MIG6MGYGCSqGSIb3DQEFDTBZMDgGCSqGSIb3DQEFDDAr
    |BBQukLpSxRcqkXIJtKxoImh9c2c05AICJxACASAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoE
    |EG0OfrMd6ipNIPOb66F2eFYEUKEQ72dLl9JuXpimpEsIjOrHoRSW5zj5eYlhZ4+rtOAu4jcDrLtS
    |Rd3UAMWfGpGp7eW3mb7OXNC6LZENGmM/eWOIR2VkDFXdUuhZ5pQUWMNrMVIwLQYJKoZIhvcNAQkU
    |MSAeHgBkAGkAcwBwAGEAdABjAGgALQBzAGUAcgB2AGUAcjAhBgkqhkiG9w0BCRUxFAQSVGltZSAx
    |NzkyNDQ2NzkyMzM5MIIC0QYJKoZIhvcNAQcGoIICwjCCAr4CAQAwggK3BgkqhkiG9w0BBwEwZgYJ
    |KoZIhvcNAQUNMFkwOAYJKoZIhvcNAQUMMCsEFI15nFiCiNBY1jL/oH4D3z5UwQtAAgInEAIBIDAM
    |BggqhkiG9w0CCQUAMB0GCWCGSAFlAwQBKgQQUPPXFK7b4SWHlkxzRH9sSYCCAkAM6PdmsE15HQhA
    |UWxaJEWBow1/W3dJjwyHLi+73kVa6anJZ8lLHmpQcocBEPa+bWpoZOXGaXD1CJ315TaQRP1NiRjl
    |0VBcUq3gewXc4fPYqaeOaDHOo3KrPikW7/O488VvsyD99wauzOCKsa6kr8g4mhvzBhpNScYzDgpp
    |bhISPftCJaf4Hq4rSAy8aP0V2Cc3PKdgdu0gXkHUgLoNKuJY8YG40cobCiwPvFMY1LqJ0sDkJFqR
    |sSwc5EM15Qn/xEV5womZQOrWq4s+cKBxWp9fsKSYW7Mo726X28DofLzQg/eX7lMI2tEo2qgne4TD
    |6SfScyVx7qSt44WmqKwaF4CTMaENtMMIRR5EQf8O2ikCJ6ais0mgfVaENXSvB4bEBNIrGaPjAu6p
    |gzvcK3zqCnIWBfAl9V4UtQdPb6SnuSdFUBCrN/w1DYWUXo4kC2iDEyx5knaM4Zo4FaarzETxPVds
    |WOiRmEe7HefJX5YxEux4f/7mkMSKqiCu0M5ukaX0RYAgvxYHyV4ki7F6injR0w7c9cMopCuFOkET
    |z8U6tB+eD4tJ3NsLyLcy/jueqPEWokF17s9pcRn2GghMmIopgHNDq4Ix1mZjSFUN/fOhkAkV4P6i
    |Q1FrkSmkJ5n4y36aYAxHjkpQlep2A6WrlVeo5OcUp8xWczwmnhXEXJJ5zxDNf8Pq5TowVQbZoBH3
    |x4ZP4o1WyStEcpyknYKLHMDfiZDLLGz+ojdU9VasS8pqu/njQe28orsa7flhdapMt6jSFfEwTTAx
    |MA0GCWCGSAFlAwQCAQUABCC8dLe/HXxRYHBpjdSxwlAihl2dbpFVBcvbnIWtpY2m1wQUq9Tg7TCi
    |lDgOG6oHtV1wKJvYbBkCAicQ
    |""".stripMargin
}
