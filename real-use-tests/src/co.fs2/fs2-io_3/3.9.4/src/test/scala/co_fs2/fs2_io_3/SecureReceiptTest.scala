/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.Stream
import fs2.io.net.Network
import fs2.io.net.tls.{TLSContext, TLSParameters}
import fs2.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.io.ByteArrayInputStream
import java.security.{KeyFactory, KeyStore, PrivateKey}
import java.security.cert.{Certificate, CertificateFactory}
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.SNIHostName
import scala.concurrent.duration.*

/** Exercises TLS sockets by submitting delivery confirmations to an authenticated receipt service. */
class SecureReceiptTest {
  // Public test-only EC identity for localhost, valid from 2020 through 2120; never a production secret.
  private val privateKeyBase64: String =
    "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgCZP1gWMeqexuK5QxlWnuyTkP" +
      "JSz8wgeVIEj83GibJXmhRANCAAQx/tiiYplWxcc5s4pHkpsHIydBS0iyt2S3zunOO2px" +
      "p1ln60qpeZchpUrq2CO1WKp59GBo6tPwnKGXKjOmHiGr"
  private val certificateBase64: String =
    "MIIBmzCCAUGgAwIBAgIUN74nYacQbGVyR+aMGFJk6Kx1mdAwCgYIKoZIzj0EAwIwFDES" +
      "MBAGA1UEAwwJbG9jYWxob3N0MCAXDTIwMDEwMTAwMDAwMFoYDzIxMjAwMTAxMDAwMDAw" +
      "WjAUMRIwEAYDVQQDDAlsb2NhbGhvc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQx" +
      "/tiiYplWxcc5s4pHkpsHIydBS0iyt2S3zunOO2pxp1ln60qpeZchpUrq2CO1WKp59GBo" +
      "6tPwnKGXKjOmHiGro28wbTAdBgNVHQ4EFgQUAPNGUCpZxP2fsbZxffLptKkDNMgwHwYD" +
      "VR0jBBgwFoAUAPNGUCpZxP2fsbZxffLptKkDNMgwDwYDVR0TAQH/BAUwAwEB/zAaBgNV" +
      "HREEEzARgglsb2NhbGhvc3SHBH8AAAEwCgYIKoZIzj0EAwIDSAAwRQIgOllBGCSFJZ/af" +
      "dr9QNw4WL3wiSz9onoq0mxzn2VN7WECIQD8lhTUbZ976HhDidHpKdOKmlRuCsq0t1Lnz" +
      "2LP8sDbCg=="

  private def contexts: IO[(TLSContext[IO], TLSContext[IO])] = {
    val password: Array[Char] = "test-identity".toCharArray
    for {
      stores <- IO.blocking {
        val decoder: Base64.Decoder = Base64.getDecoder
        val input: ByteArrayInputStream = new ByteArrayInputStream(decoder.decode(certificateBase64))
        val certificate: Certificate = try {
          CertificateFactory.getInstance("X.509").generateCertificate(input)
        } finally input.close()
        val key: PrivateKey = KeyFactory.getInstance("EC").generatePrivate(
          new PKCS8EncodedKeySpec(decoder.decode(privateKeyBase64))
        )
        val identity: KeyStore = KeyStore.getInstance("PKCS12")
        identity.load(null, password)
        identity.setKeyEntry("receipt-service", key, password, Array(certificate))
        // The client trusts this service certificate only, not an insecure trust-all manager.
        val trusted: KeyStore = KeyStore.getInstance("PKCS12")
        trusted.load(null, password)
        trusted.setCertificateEntry("receipt-service", certificate)
        (identity, trusted)
      }
      server <- TLSContext.Builder.forAsync[IO].fromKeyStore(stores._1, password)
      client <- TLSContext.Builder.forAsync[IO].fromKeyStore(stores._2, password)
    } yield (server, client)
  }

  @Test
  @Timeout(60)
  def submitConfirmationsOverAuthenticatedTls(): Unit = {
    val confirmations: List[String] = List("DELIVERED|shipment-81|12", "DELIVERED|shipment-82|5")
    val expectedReceipts: List[String] = List("ACCEPTED|shipment-81|12", "ACCEPTED|shipment-82|5")
    val network: Network[IO] = Network[IO]
    val serverParameters: TLSParameters = TLSParameters(applicationProtocols = Some(List("receipts/1")))
    val clientParameters: TLSParameters = TLSParameters(
      applicationProtocols = Some(List("receipts/1")),
      endpointIdentificationAlgorithm = Some("HTTPS"),
      serverNames = Some(List(new SNIHostName("localhost")))
    )

    val exchange: IO[Unit] = contexts.flatMap { case (serverTls, clientTls) =>
      network.serverResource(address = Some(ip"127.0.0.1")).use { case (address, connections) =>
        val serve: IO[List[String]] = connections.take(1).flatMap { raw =>
          Stream.resource(serverTls.serverBuilder(raw).withParameters(serverParameters).build)
            .flatMap { socket =>
              socket.reads.through(text.utf8.decode).through(text.lines).filter(_.nonEmpty)
                .take(confirmations.size.toLong).evalMap { confirmation =>
                  val fields: Array[String] = confirmation.split("\\|", -1)
                  IO(assertEquals("DELIVERED", fields(0))) *>
                    Stream.emit(s"ACCEPTED|${fields(1)}|${fields(2)}\n")
                      .through(text.utf8.encode).through(socket.writes).compile.drain.as(confirmation)
                }
            }
        }.compile.toList

        val submit: IO[(List[String], String)] = network.client(address).flatMap { raw =>
          clientTls.clientBuilder(raw).withParameters(clientParameters).build
        }.use { socket =>
          for {
            _ <- Stream.emits(confirmations).map(_ + "\n").through(text.utf8.encode)
              .through(socket.writes).compile.drain
            receipts <- socket.reads.through(text.utf8.decode).through(text.lines)
              .filter(_.nonEmpty).take(confirmations.size.toLong).compile.toList
            protocol <- socket.applicationProtocol
          } yield (receipts, protocol)
        }

        serve.both(submit).flatMap { case (received, (receipts, protocol)) =>
          IO {
            assertEquals(confirmations, received, "The authenticated service must receive both confirmations")
            assertEquals(expectedReceipts, receipts, "Each delivery must receive its matching receipt")
            assertEquals("receipts/1", protocol, "Both endpoints must agree on the receipt protocol")
          }
        }
      }
    }
    exchange.timeout(30.seconds).unsafeRunSync()
  }
}
