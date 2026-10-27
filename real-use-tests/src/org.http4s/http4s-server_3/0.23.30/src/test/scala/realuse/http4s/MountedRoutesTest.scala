/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{HttpApp, HttpRoutes, Method, Request, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises server routing by mounting catalog and administration endpoints under nested prefixes. */
class MountedRoutesTest {
  @Test
  @Timeout(60)
  def dispatchesMountedEndpointsAndFallsBackForUnmatchedRequests(): Unit = {
    val catalog: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "products" / sku => Ok(s"Product: $sku")
      case GET -> Root / "admin" / "contact" => Ok("Contact: operations@example.test")
    }
    val administration: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "status" => Ok("Catalog service ready")
    }
    val application: HttpApp[IO] = Router(
      "/api" -> catalog,
      "/api/admin" -> administration
    ).orNotFound

    val checks: IO[Unit] = for {
      product <- application.run(Request[IO](uri = Uri.unsafeFromString("/api/products/book-42")))
      productBody <- product.as[String]
      status <- application.run(Request[IO](uri = Uri.unsafeFromString("/api/admin/status")))
      statusBody <- status.as[String]
      contact <- application.run(Request[IO](uri = Uri.unsafeFromString("/api/admin/contact")))
      contactBody <- contact.as[String]
      missing <- application.run(Request[IO](uri = Uri.unsafeFromString("/api/products")))
      outside <- application.run(Request[IO](uri = Uri.unsafeFromString("/apix/products/book-42")))
      wrongMethod <- application.run(Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("/api/products/book-42")
      ))
    } yield {
      assertEquals(Status.Ok, product.status)
      assertEquals("Product: book-42", productBody)
      assertEquals(Status.Ok, status.status)
      assertEquals("Catalog service ready", statusBody)
      assertEquals(Status.Ok, contact.status)
      assertEquals("Contact: operations@example.test", contactBody)
      assertEquals(Status.NotFound, missing.status)
      assertEquals(Status.NotFound, outside.status)
      assertEquals(Status.NotFound, wrongMethod.status)
    }

    checks.unsafeRunSync()
  }
}
