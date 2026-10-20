/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.http4s.{Header, HttpApp, HttpRoutes, Request, Response, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.middleware.RequestId
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

import java.util.UUID

/** Exercises request-ID middleware correlating order lookup responses with application audit records. */
class OrderRequestIdTest {
  private final case class AuditEntry(sku: String, requestId: String)

  @Test
  @Timeout(60)
  def preservesGatewayIdsAndGeneratesSupportReferencesForDirectRequests(): Unit = {
    val gatewayId: String = "gateway-order-731"
    val orderRequest: Request[IO] = Request[IO](uri = Uri.unsafeFromString("/orders/book-42"))

    def responseId(response: Response[IO]): IO[String] =
      IO.fromOption(response.headers.get(ci"X-Request-ID").map(_.head.value))(
        new IllegalStateException("Every order response must include a support reference")
      )

    val checks: IO[Unit] = for {
      audit <- Ref.of[IO, Vector[AuditEntry]](Vector.empty)
      routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case request @ GET -> Root / "orders" / sku =>
          for {
            id <- IO.fromOption(request.attributes.lookup(RequestId.requestIdAttrKey))(
              new IllegalStateException("Order handlers require a request correlation ID")
            )
            _ <- audit.update(_ :+ AuditEntry(sku, id))
            response <- if (sku == "book-42") Ok(s"Order book-42: shipped; reference=$id")
                        else NotFound(s"Order $sku not found; reference=$id")
          } yield response
      }
      application: HttpApp[IO] = RequestId.httpApp(routes.orNotFound)
      forwarded <- application.run(orderRequest.putHeaders(Header.Raw(ci"x-request-id", gatewayId)))
      forwardedBody <- forwarded.as[String]
      forwardedId <- responseId(forwarded)
      direct <- application.run(orderRequest)
      directBody <- direct.as[String]
      directId <- responseId(direct)
      missing <- application.run(Request[IO](uri = Uri.unsafeFromString("/orders/missing")))
      missingBody <- missing.as[String]
      missingId <- responseId(missing)
      entries <- audit.get
    } yield {
      assertEquals(Status.Ok, forwarded.status)
      assertEquals(gatewayId, forwardedId)
      assertEquals(s"Order book-42: shipped; reference=$gatewayId", forwardedBody)
      assertEquals(Status.Ok, direct.status)
      assertFalse(directId.isEmpty)
      assertEquals(directId, UUID.fromString(directId).toString)
      assertEquals(s"Order book-42: shipped; reference=$directId", directBody)
      assertEquals(Status.NotFound, missing.status)
      assertFalse(missingId.isEmpty)
      assertEquals(missingId, UUID.fromString(missingId).toString)
      assertEquals(s"Order missing not found; reference=$missingId", missingBody)
      assertEquals(
        Vector(AuditEntry("book-42", gatewayId), AuditEntry("book-42", directId), AuditEntry("missing", missingId)),
        entries
      )
    }

    checks.unsafeRunSync()
  }
}
