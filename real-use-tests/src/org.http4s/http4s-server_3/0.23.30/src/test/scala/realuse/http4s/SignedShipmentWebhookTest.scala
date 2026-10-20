/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.data.Kleisli
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.*
import io.circe.Json
import org.http4s.{Header, HttpApp, HttpRoutes, MediaType, Method, Request, Status, Uri}
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.server.middleware.BodyCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.concurrent.duration.*

/** Exercises request-body caching for signed shipment webhooks verified before JSON decoding. */
class SignedShipmentWebhookTest {
  private def signature(bytes: Array[Byte]): String = {
    val mac: Mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec("shipment-webhook-test-secret".getBytes(UTF_8), "HmacSHA256"))
    HexFormat.of().formatHex(mac.doFinal(bytes))
  }

  private def verifySignatures(application: HttpApp[IO]): HttpApp[IO] = Kleisli { request =>
    if (request.method != Method.POST) application.run(request)
    else request.body.compile.toVector.flatMap { bytes =>
      val supplied: Option[String] = request.headers.get(ci"X-Shipment-Signature").map(_.head.value)
      val valid: Boolean = supplied.exists(value => MessageDigest.isEqual(
        value.getBytes(UTF_8), signature(bytes.toArray).getBytes(UTF_8)
      ))
      if (valid) application.run(request)
      else Forbidden("Invalid shipment signature")
    }
  }

  private def webhook(base: Uri, payload: String, signedPayload: String): Request[IO] =
    Request[IO](method = Method.POST, uri = base / "webhooks" / "shipment")
      .withEntity(payload)
      .putHeaders(
        `Content-Type`(MediaType.application.json),
        Header.Raw(ci"X-Shipment-Signature", signature(signedPayload.getBytes(UTF_8)))
      )

  private def send(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))

  @Test
  @Timeout(60)
  def verifiesNetworkBodiesAndStillDecodesEachAcceptedShipment(): Unit = {
    val dispatched: String = """{"order":"book-42","state":"dispatched"}"""
    val delivered: String = """{"order":"book-42","state":"delivered"}"""
    val forged: String = """{"order":"book-42","state":"cancelled"}"""

    val checks: IO[Unit] = for {
      shipments <- Ref.of[IO, Map[String, String]](Map.empty)
      acceptedEvents <- Ref.of[IO, Vector[String]](Vector.empty)
      routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case request @ POST -> Root / "webhooks" / "shipment" =>
          for {
            json <- request.as[Json]
            order <- IO.fromEither(json.hcursor.get[String]("order"))
            state <- IO.fromEither(json.hcursor.get[String]("state"))
            _ <- shipments.update(_.updated(order, state))
            _ <- acceptedEvents.update(_ :+ state)
            response <- Accepted(s"Recorded $order: $state")
          } yield response
        case GET -> Root / "shipments" / order =>
          shipments.get.flatMap(_.get(order).fold(NotFound("Unknown shipment"))(Ok(_)))
      }
      // Both signature verification and the handler consume the actual incoming network body.
      application: HttpApp[IO] = BodyCache.httpApp(verifySignatures(routes.orNotFound))
      _ <- (for {
        server <- EmberServerBuilder.default[IO]
          .withHost(ipv4"127.0.0.1")
          .withPort(port"0")
          .withHttpApp(application)
          .build
        client <- EmberClientBuilder.default[IO].withTimeout(15.seconds).build
      } yield (server, client)).use { case (server, client) =>
        val base: Uri = server.baseUri
        val lookup: Request[IO] = Request[IO](uri = base / "shipments" / "book-42")
        for {
          first <- send(client, webhook(base, dispatched, dispatched))
          initial <- send(client, lookup)
          rejected <- send(client, webhook(base, forged, dispatched))
          unsigned <- send(client, webhook(base, forged, forged).removeHeader(ci"X-Shipment-Signature"))
          unchanged <- send(client, lookup)
          second <- send(client, webhook(base, delivered, delivered))
          latest <- send(client, lookup)
          events <- acceptedEvents.get
        } yield {
          assertEquals((Status.Accepted, "Recorded book-42: dispatched"), first)
          assertEquals((Status.Ok, "dispatched"), initial)
          assertEquals((Status.Forbidden, "Invalid shipment signature"), rejected)
          assertEquals((Status.Forbidden, "Invalid shipment signature"), unsigned)
          assertEquals((Status.Ok, "dispatched"), unchanged)
          assertEquals((Status.Accepted, "Recorded book-42: delivered"), second)
          assertEquals((Status.Ok, "delivered"), latest)
          assertEquals(Vector("dispatched", "delivered"), events)
        }
      }
    } yield ()

    checks.unsafeRunSync()
  }
}
