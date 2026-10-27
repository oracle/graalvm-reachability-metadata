/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{HttpApp, HttpRoutes, Request, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.headers.Host
import org.http4s.implicits.*
import org.http4s.server.middleware.VirtualHost
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises virtual-host middleware serving separate storefront catalogs on a shared server. */
class StorefrontVirtualHostTest {
  @Test
  @Timeout(60)
  def selectsTheStorefrontByHostWithoutFallingBackToAnotherCatalog(): Unit = {
    val books: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "catalog" => Ok("Maple Books: Field Guide, Travel Journal")
      case GET -> Root / "products" / "field-guide" => Ok("Maple Books: Field Guide costs 19.00")
    }
    val stationery: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "catalog" => Ok("Cedar Stationery: Fountain Pen, Travel Journal")
      case GET -> Root / "products" / "fountain-pen" => Ok("Cedar Stationery: Fountain Pen costs 12.00")
    }
    val application: HttpApp[IO] = VirtualHost(
      VirtualHost.exact(books.orNotFound, "books.example.test"),
      VirtualHost.exact(stationery.orNotFound, "stationery.example.test")
    )

    def request(host: String, path: String): Request[IO] =
      Request[IO](uri = Uri.unsafeFromString(path)).putHeaders(Host(host))

    val checks: IO[Unit] = for {
      bookCatalog <- application.run(request("books.example.test", "/catalog"))
      bookCatalogBody <- bookCatalog.as[String]
      stationeryCatalog <- application.run(request("stationery.example.test", "/catalog"))
      stationeryCatalogBody <- stationeryCatalog.as[String]
      book <- application.run(request("BOOKS.EXAMPLE.TEST", "/products/field-guide"))
      bookBody <- book.as[String]
      pen <- application.run(request("stationery.example.test", "/products/fountain-pen"))
      penBody <- pen.as[String]
      // A product on the other storefront must not be found through route fallback.
      foreignProduct <- application.run(request("books.example.test", "/products/fountain-pen"))
      foreignProductBody <- foreignProduct.as[String]
      unknownHost <- application.run(request("unregistered.example.test", "/catalog"))
      unknownHostBody <- unknownHost.as[String]
      missingHost <- application.run(Request[IO](uri = Uri.unsafeFromString("/catalog")))
      missingHostBody <- missingHost.as[String]
    } yield {
      assertEquals(Status.Ok, bookCatalog.status)
      assertEquals("Maple Books: Field Guide, Travel Journal", bookCatalogBody)
      assertEquals(Status.Ok, stationeryCatalog.status)
      assertEquals("Cedar Stationery: Fountain Pen, Travel Journal", stationeryCatalogBody)
      assertEquals(Status.Ok, book.status)
      assertEquals("Maple Books: Field Guide costs 19.00", bookBody)
      assertEquals(Status.Ok, pen.status)
      assertEquals("Cedar Stationery: Fountain Pen costs 12.00", penBody)
      assertEquals(Status.NotFound, foreignProduct.status)
      assertEquals("Not found", foreignProductBody)
      assertEquals(Status.NotFound, unknownHost.status)
      assertTrue(unknownHostBody.contains("unregistered.example.test"))
      assertEquals(Status.BadRequest, missingHost.status)
      assertEquals("Host header required.", missingHostBody)
    }

    checks.unsafeRunSync()
  }
}
