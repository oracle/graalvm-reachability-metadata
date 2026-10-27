/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, HttpApp, HttpRoutes, Method, Request, Response, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

import scala.concurrent.duration.*

/** Exercises CORS middleware for a credentialed browser storefront calling an orders API. */
class BrowserCorsTest {
  @Test
  @Timeout(60)
  def sharesOrdersOnlyWithTheConfiguredBrowserOrigin(): Unit = {
    val storefront: String = "https://shop.example.test"
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "orders" =>
        Ok("Order book-42: shipped").map(_.putHeaders(Header.Raw(ci"X-Request-Id", "orders-42")))
    }
    val application: HttpApp[IO] = CORS.policy
      .withAllowOriginHostCi(_ == CIString(storefront))
      .withAllowCredentials(true)
      .withAllowMethodsIn(Set(Method.GET))
      .withAllowHeadersIn(Set(ci"Authorization"))
      .withExposeHeadersIn(Set(ci"X-Request-Id"))
      .withMaxAge(30.minutes)
      .httpApp(routes.orNotFound)

    def preflight(origin: String, method: String, requestedHeader: String): Request[IO] =
      Request[IO](method = Method.OPTIONS, uri = Uri.unsafeFromString("/orders"))
        .putHeaders(
          Header.Raw(ci"Origin", origin),
          Header.Raw(ci"Access-Control-Request-Method", method),
          Header.Raw(ci"Access-Control-Request-Headers", requestedHeader)
        )

    def header(response: Response[IO], name: String): Option[String] =
      response.headers.get(CIString(name)).map(_.head.value)

    val checks: IO[Unit] = for {
      permission <- application.run(preflight(storefront, "GET", "authorization"))
      permissionBody <- permission.as[String]
      orders <- application.run(
        Request[IO](uri = Uri.unsafeFromString("/orders")).putHeaders(
          Header.Raw(ci"Origin", storefront),
          Header.Raw(ci"Authorization", "Bearer customer-token")
        )
      )
      ordersBody <- orders.as[String]
      foreignOrigin <- application.run(preflight("https://untrusted.example.test", "GET", "authorization"))
      forbiddenMethod <- application.run(preflight(storefront, "DELETE", "authorization"))
      forbiddenHeader <- application.run(preflight(storefront, "GET", "X-Admin-Override"))
      foreignOrders <- application.run(
        Request[IO](uri = Uri.unsafeFromString("/orders"))
          .putHeaders(Header.Raw(ci"Origin", "https://untrusted.example.test"))
      )
      sameOrigin <- application.run(Request[IO](uri = Uri.unsafeFromString("/orders")))
      sameOriginBody <- sameOrigin.as[String]
    } yield {
      assertEquals(Status.Ok, permission.status)
      assertEquals("", permissionBody)
      assertEquals(Some(storefront), header(permission, "Access-Control-Allow-Origin"))
      assertEquals(Some("true"), header(permission, "Access-Control-Allow-Credentials"))
      assertEquals(Some("GET"), header(permission, "Access-Control-Allow-Methods"))
      assertEquals(Some("Authorization"), header(permission, "Access-Control-Allow-Headers"))
      assertEquals(Some("1800"), header(permission, "Access-Control-Max-Age"))
      assertEquals(Status.Ok, orders.status)
      assertEquals("Order book-42: shipped", ordersBody)
      assertEquals(Some(storefront), header(orders, "Access-Control-Allow-Origin"))
      assertEquals(Some("true"), header(orders, "Access-Control-Allow-Credentials"))
      assertEquals(Some("X-Request-Id"), header(orders, "Access-Control-Expose-Headers"))
      assertEquals(Some("orders-42"), header(orders, "X-Request-Id"))
      assertEquals(Some("Origin"), header(orders, "Vary"))
      for (denied <- List(foreignOrigin, forbiddenMethod, forbiddenHeader, foreignOrders)) {
        assertEquals(None, header(denied, "Access-Control-Allow-Origin"))
        assertEquals(None, header(denied, "Access-Control-Allow-Credentials"))
      }
      assertEquals(Status.Ok, sameOrigin.status)
      assertEquals("Order book-42: shipped", sameOriginBody)
      assertEquals(None, header(sameOrigin, "Access-Control-Allow-Origin"))
    }

    checks.unsafeRunSync()
  }
}
