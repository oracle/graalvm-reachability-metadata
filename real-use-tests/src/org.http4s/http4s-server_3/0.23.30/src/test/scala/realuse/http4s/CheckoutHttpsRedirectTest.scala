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
import org.http4s.{Header, HttpApp, HttpRoutes, Request, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.headers.{Host, Location}
import org.http4s.implicits.*
import org.http4s.server.middleware.HttpsRedirect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

/** Exercises HTTPS redirect middleware for a checkout service behind a TLS-terminating proxy. */
class CheckoutHttpsRedirectTest {
  @Test
  @Timeout(60)
  def redirectsInsecureCheckoutLinksAndServesTheSecureFollowUpWithoutALoop(): Unit = {
    val checkout: Uri = Uri.unsafeFromString("/checkout/book-42?quantity=3&return=%2Forders%2Frecent")
    val publicCheckout: Uri = Uri.unsafeFromString(
      "https://shop.example.test/checkout/book-42?quantity=3&return=%2Forders%2Frecent"
    )
    // The edge proxy supplies Host and replaces X-Forwarded-Proto on every incoming request.
    val insecure: Request[IO] = Request[IO](uri = checkout).putHeaders(
      Host("shop.example.test"),
      Header.Raw(ci"X-Forwarded-Proto", "http")
    )

    val checks: IO[Unit] = for {
      checkoutViews <- Ref.of[IO, Vector[Map[String, String]]](Vector.empty)
      routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case request @ GET -> Root / "checkout" / "book-42" =>
          checkoutViews.update(_ :+ request.uri.query.params) *>
            Ok(s"Checkout book-42: quantity=${request.uri.query.params.getOrElse("quantity", "1")}")
        case GET -> Root / "health" => Ok("ready")
      }
      application: HttpApp[IO] = HttpsRedirect.httpApp(routes.orNotFound)
      redirect <- application.run(insecure)
      redirectBody <- redirect.as[String]
      viewsBeforeFollowUp <- checkoutViews.get
      destination <- IO.fromOption(redirect.headers.get[Location].map(_.uri))(
        new IllegalStateException("An insecure checkout link must supply a secure destination")
      )
      // The browser follows Location, and the TLS-terminating edge marks this request as HTTPS.
      secure <- application.run(Request[IO](uri = destination).putHeaders(
        Host("shop.example.test"),
        Header.Raw(ci"X-Forwarded-Proto", "https")
      ))
      checkoutBody <- secure.as[String]
      viewsAfterFollowUp <- checkoutViews.get
      // Internal health probes without proxy headers must still reach the service.
      health <- application.run(Request[IO](uri = Uri.unsafeFromString("/health")))
      healthBody <- health.as[String]
    } yield {
      assertEquals(Status.MovedPermanently, redirect.status)
      assertEquals(publicCheckout, destination)
      assertEquals("", redirectBody)
      assertEquals(Vector.empty[Map[String, String]], viewsBeforeFollowUp)
      assertEquals(Status.Ok, secure.status)
      assertEquals(None, secure.headers.get[Location])
      assertEquals("Checkout book-42: quantity=3", checkoutBody)
      assertEquals(Vector(Map("quantity" -> "3", "return" -> "/orders/recent")), viewsAfterFollowUp)
      assertEquals(Status.Ok, health.status)
      assertEquals("ready", healthBody)
      assertEquals(None, health.headers.get[Location])
    }

    checks.unsafeRunSync()
  }
}
