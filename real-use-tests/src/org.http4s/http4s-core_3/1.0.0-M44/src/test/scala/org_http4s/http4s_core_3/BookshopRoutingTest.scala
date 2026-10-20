/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.semigroupk.*
import org.http4s.AuthedRoutes
import org.http4s.ContextRequest
import org.http4s.ContextRoutes
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.Http4sDsl
import org.http4s.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*
import org.typelevel.vault.Key

/**
 * Routes requests through composed `HttpRoutes`, `AuthedRoutes`, and `ContextRoutes` handlers that
 * share per-request state through vault attributes on `Request`.
 *
 * A bookshop backend mounts its public catalog under `/api`, tags every request with a request id
 * that handlers and the response see, lets signed-in staff manage orders through authenticated
 * routes, and serves tenant-specific branding from context routes, answering `404 Not Found` for
 * every request no handler claims.
 */
class BookshopRoutingTest {

  @Test
  def mountedCatalogRoutesFallThroughToNotFound(): Unit = {
    val app: HttpApp[IO] = Bookshop.app

    val listing: Response[IO] = app.run(Request[IO](uri = uri"/api/books")).unsafeRunSync()
    assertEquals(Status.Ok, listing.status)
    assertEquals("dune,emma,ulysses", listing.as[String].unsafeRunSync())

    val book: Response[IO] = app.run(Request[IO](uri = uri"/api/books/2")).unsafeRunSync()
    assertEquals(Status.Ok, book.status)
    assertEquals("emma (script /api, path /books/2)", book.as[String].unsafeRunSync())

    val unknownBook: Response[IO] = app.run(Request[IO](uri = uri"/api/books/9")).unsafeRunSync()
    assertEquals(Status.NotFound, unknownBook.status)
    assertEquals("no book 9", unknownBook.as[String].unsafeRunSync())

    val health: Response[IO] = app.run(Request[IO](uri = uri"/api/health")).unsafeRunSync()
    assertEquals(Status.Ok, health.status)
    assertEquals("ok", health.as[String].unsafeRunSync())

    val unmounted: Response[IO] = app.run(Request[IO](uri = uri"/books")).unsafeRunSync()
    assertEquals(Status.NotFound, unmounted.status)
    assertEquals("Not found", unmounted.as[String].unsafeRunSync())

    val wrongMethod: Response[IO] =
      app.run(Request[IO](method = Method.DELETE, uri = uri"/api/books/2")).unsafeRunSync()
    assertEquals(Status.NotFound, wrongMethod.status)
  }

  @Test
  def requestIdTravelsFromMiddlewareToHandlerAndResponse(): Unit = {
    val incoming: Request[IO] =
      Request[IO](uri = uri"/api/whoami").putHeaders("X-Request-Id" -> "req-from-edge")
    val echoed: Response[IO] = Bookshop.app.run(incoming).unsafeRunSync()

    assertEquals(Status.Ok, echoed.status)
    assertEquals("handled req-from-edge", echoed.as[String].unsafeRunSync())
    assertEquals(Some("req-from-edge"), echoed.headers.get(ci"X-Request-Id").map(_.head.value))
    assertTrue(incoming.attributes.lookup(RequestIdKey).isEmpty)

    val generated: Response[IO] =
      Bookshop.app.run(Request[IO](uri = uri"/api/whoami")).unsafeRunSync()
    assertEquals("handled generated-GET-/api/whoami", generated.as[String].unsafeRunSync())
    assertEquals(
      Some("generated-GET-/api/whoami"),
      generated.headers.get(ci"X-Request-Id").map(_.head.value)
    )

    val missing: Response[IO] =
      Bookshop.app.run(Request[IO](uri = uri"/api/nothing-here")).unsafeRunSync()
    assertEquals(Status.NotFound, missing.status)
    assertTrue(missing.headers.get(ci"X-Request-Id").isEmpty)
  }

  @Test
  def authenticatedRoutesActOnBehalfOfTheSignedInStaffMember(): Unit = {
    val app: HttpApp[IO] = Bookshop.app

    val anonymous: Response[IO] = app.run(Request[IO](uri = uri"/admin/orders")).unsafeRunSync()
    assertEquals(Status.Unauthorized, anonymous.status)
    assertEquals("sign in first", anonymous.as[String].unsafeRunSync())

    val clerkListing: Response[IO] =
      app.run(Request[IO](uri = uri"/admin/orders").putHeaders("X-Staff-Token" -> "clerk-token"))
        .unsafeRunSync()
    assertEquals(Status.Ok, clerkListing.status)
    assertEquals("orders 100,101 for Casey", clerkListing.as[String].unsafeRunSync())

    val clerkRefund: Response[IO] = app
      .run(
        Request[IO](method = Method.POST, uri = uri"/admin/orders/101/refund")
          .putHeaders("X-Staff-Token" -> "clerk-token")
      )
      .unsafeRunSync()
    assertEquals(Status.Forbidden, clerkRefund.status)
    assertEquals("Casey may not refund", clerkRefund.as[String].unsafeRunSync())

    val managerRefund: Response[IO] = app
      .run(
        Request[IO](method = Method.POST, uri = uri"/admin/orders/101/refund")
          .putHeaders("X-Staff-Token" -> "manager-token")
      )
      .unsafeRunSync()
    assertEquals(Status.Ok, managerRefund.status)
    assertEquals("order 101 refunded by Morgan", managerRefund.as[String].unsafeRunSync())

    val unknownAdminPath: Response[IO] = app
      .run(
        Request[IO](uri = uri"/admin/payroll").putHeaders("X-Staff-Token" -> "manager-token")
      )
      .unsafeRunSync()
    assertEquals(Status.NotFound, unknownAdminPath.status)
  }

  @Test
  def contextRoutesServeTheTenantResolvedFromTheHost(): Unit = {
    val app: HttpApp[IO] = Bookshop.app

    val northwind: Response[IO] = app
      .run(Request[IO](uri = uri"/branding").putHeaders("Host" -> "northwind.shop.example"))
      .unsafeRunSync()
    assertEquals(Status.Ok, northwind.status)
    assertEquals("Northwind Books in teal", northwind.as[String].unsafeRunSync())

    val fallback: Response[IO] =
      app.run(Request[IO](uri = uri"/branding")).unsafeRunSync()
    assertEquals("Bookshop in black", fallback.as[String].unsafeRunSync())

    val defaults: HttpRoutes[IO] =
      ContextRoutes.toHttpRoutes(DefaultTenant)(Bookshop.brandingRoutes)
    val preview: Option[Response[IO]] =
      defaults.run(Request[IO](uri = uri"/branding")).value.unsafeRunSync()
    assertEquals(Some("Bookshop in black"), preview.map(_.as[String].unsafeRunSync()))
    assertEquals(None, defaults.run(Request[IO](uri = uri"/other")).value.unsafeRunSync())
  }

  final case class Staff(name: String, canRefund: Boolean)

  final case class Tenant(name: String, colour: String)

  val DefaultTenant: Tenant = Tenant("Bookshop", "black")

  /** Per-request id attached by the middleware and read back by handlers. */
  val RequestIdKey: Key[String] = Key.newKey[IO, String].unsafeRunSync()

  object Bookshop extends Http4sDsl[IO] {

    private val books: Map[Int, String] = Map(1 -> "dune", 2 -> "emma", 3 -> "ulysses")

    private val staffByToken: Map[String, Staff] = Map(
      "clerk-token" -> Staff("Casey", canRefund = false),
      "manager-token" -> Staff("Morgan", canRefund = true)
    )

    private val tenantsByHost: Map[String, Tenant] =
      Map("northwind.shop.example" -> Tenant("Northwind Books", "teal"))

    val catalogRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "books" =>
        Ok(books.toList.sortBy(_._1).map(_._2).mkString(","))
      case request @ GET -> Root / "books" / IntVar(id) =>
        books.get(id) match {
          case Some(title) =>
            Ok(s"$title (script ${request.scriptName}, path ${request.pathInfo})")
          case None => NotFound(s"no book $id")
        }
    }

    val operationsRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "health" => Ok("ok")
      case request @ GET -> Root / "whoami" =>
        Ok(s"handled ${request.attributes.lookup(RequestIdKey).getOrElse("none")}")
    }

    val orderRoutes: AuthedRoutes[Staff, IO] = AuthedRoutes.of[Staff, IO] {
      case GET -> Root / "admin" / "orders" as staff =>
        Ok(s"orders 100,101 for ${staff.name}")
      case POST -> Root / "admin" / "orders" / IntVar(id) / "refund" as staff =>
        if staff.canRefund then Ok(s"order $id refunded by ${staff.name}")
        else Forbidden(s"${staff.name} may not refund")
    }

    val brandingRoutes: ContextRoutes[Tenant, IO] = ContextRoutes.of[Tenant, IO] {
      case GET -> Root / "branding" as tenant => Ok(s"${tenant.name} in ${tenant.colour}")
    }

    /** Serves `routes` below `prefix` by moving the path-info caret past the prefix segments. */
    def mount(prefix: Uri.Path, routes: HttpRoutes[IO]): HttpRoutes[IO] =
      Kleisli { (request: Request[IO]) =>
        if request.pathInfo.startsWith(prefix) then
          routes(request.withAttribute(Request.Keys.PathInfoCaret, prefix.segments.size))
        else OptionT.none[IO, Response[IO]]
      }

    /** Tags each request with an id from `X-Request-Id`, or derives one, and echoes it back. */
    def withRequestId(routes: HttpRoutes[IO]): HttpRoutes[IO] =
      Kleisli { (request: Request[IO]) =>
        val requestId: String = request.headers
          .get(ci"X-Request-Id")
          .map(_.head.value)
          .getOrElse(s"generated-${request.method}-${request.uri.path}")
        routes(request.withAttribute(RequestIdKey, requestId))
          .map(_.putHeaders("X-Request-Id" -> requestId))
      }

    /** Runs `routes` for the staff member named by `X-Staff-Token`, or rejects the request. */
    def authenticated(routes: AuthedRoutes[Staff, IO]): HttpRoutes[IO] =
      Kleisli { (request: Request[IO]) =>
        if !request.pathInfo.startsWith(Uri.Path.unsafeFromString("/admin")) then
          OptionT.none[IO, Response[IO]]
        else
          val signedIn: Option[Staff] =
            request.headers.get(ci"X-Staff-Token").flatMap(h => staffByToken.get(h.head.value))
          signedIn match {
            case Some(staff) => routes(ContextRequest(staff, request))
            case None =>
              OptionT.pure[IO](
                Response[IO](Status.Unauthorized).withEntity("sign in first")
              )
          }
      }

    /** Resolves the tenant from the `Host` header before running the branding routes. */
    def forTenant(routes: ContextRoutes[Tenant, IO]): HttpRoutes[IO] =
      Kleisli { (request: Request[IO]) =>
        val tenant: Tenant = request.headers
          .get(ci"Host")
          .flatMap(h => tenantsByHost.get(h.head.value))
          .getOrElse(DefaultTenant)
        routes(ContextRequest(tenant, request))
      }

    val app: HttpApp[IO] = (
      withRequestId(mount(Uri.Path.unsafeFromString("/api"), catalogRoutes <+> operationsRoutes))
        <+> authenticated(orderRoutes)
        <+> forTenant(brandingRoutes)
    ).orNotFound
  }
}
