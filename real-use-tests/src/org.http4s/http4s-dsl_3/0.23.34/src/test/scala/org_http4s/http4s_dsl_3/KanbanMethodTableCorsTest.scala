/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Allow
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.CORSPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

import scala.concurrent.duration.*

/**
 * Method-table routing with the DSL `->>` extractor behind the CORS middleware.
 *
 * A kanban board API declares, per resource, a partial function from HTTP method to response
 * through `withMethod ->> Root / ...`; the DSL answers every method the table does not define with
 * `405 Method Not Allowed` and an `Allow` header derived from the table, while the tables also
 * answer plain `OPTIONS` discovery requests themselves. The routes sit behind a `CORSPolicy` that
 * admits a single browser origin, so preflight requests are answered by the middleware with the
 * allowed methods, headers and max age, and cross-origin responses carry the allowed origin and
 * exposed headers only for that origin. The service runs on an Ember server and is driven by an
 * Ember client acting as the browser.
 */
class KanbanMethodTableCorsTest {
  import KanbanMethodTableCorsTest.*

  @Test
  def methodTablesAnswerUndefinedMethodsWithAllow(): Unit = {
    val outcome: (Reply, Reply, Reply, Reply) = serveAndCall { (client: Client[IO], base: Uri) =>
      for {
        created <- call(client, Request[IO](Method.POST, base / "boards").withEntity("Sprint 42"))
        patchItem <- call(client, Request[IO](Method.PATCH, base / "boards" / "1"))
        deleteCollection <- call(client, Request[IO](Method.DELETE, base / "boards"))
        getArchive <- call(client, Request[IO](Method.GET, base / "boards" / "1" / "archive"))
      } yield (created, patchItem, deleteCollection, getArchive)
    }
    val (created, patchItem, deleteCollection, getArchive) = outcome

    assertEquals(Status.Created, created.status)
    assertEquals(Some(Location(uri"/boards/1")), created.location)
    assertEquals(Status.MethodNotAllowed, patchItem.status)
    assertEquals(Set(Method.GET, Method.PUT, Method.DELETE, Method.OPTIONS), patchItem.allowed)
    assertEquals(Status.MethodNotAllowed, deleteCollection.status)
    assertEquals(Set(Method.GET, Method.POST, Method.OPTIONS), deleteCollection.allowed)
    assertEquals(Status.MethodNotAllowed, getArchive.status)
    assertEquals(Set(Method.POST), getArchive.allowed)
  }

  @Test
  def boardLifecycleRunsThroughMethodTables(): Unit = {
    val replies: List[Reply] = serveAndCall { (client: Client[IO], base: Uri) =>
      val boards: Uri = base / "boards"
      List(
        Request[IO](Method.POST, boards).withEntity("Backlog"),
        Request[IO](Method.POST, boards).withEntity("Release"),
        Request[IO](Method.PUT, boards / "2").withEntity("Release 1.0"),
        Request[IO](Method.POST, boards / "2" / "archive"),
        Request[IO](Method.POST, boards / "2" / "archive"),
        Request[IO](Method.GET, boards / "2"),
        Request[IO](Method.DELETE, boards / "1"),
        Request[IO](Method.GET, boards / "1"),
        Request[IO](Method.POST, boards / "1" / "archive"),
        Request[IO](Method.GET, boards)
      ).foldLeft(IO.pure(List.empty[Reply])) { (acc: IO[List[Reply]], request: Request[IO]) =>
        acc.flatMap(done => call(client, request).map(reply => done :+ reply))
      }
    }

    assertEquals(
      List(
        Status.Created,
        Status.Created,
        Status.NoContent,
        Status.NoContent,
        Status.Conflict,
        Status.Ok,
        Status.NoContent,
        Status.NotFound,
        Status.NotFound,
        Status.Ok
      ),
      replies.map(_.status)
    )
    assertEquals("already archived", replies(4).body)
    assertEquals("Release 1.0 [archived]", replies(5).body)
    assertEquals("no board 1", replies(7).body)
    assertEquals("2:Release 1.0 [archived]", replies(9).body)
    assertEquals(Some("1"), replies(9).header(BoardCount))
  }

  @Test
  def plainOptionsRequestsAreAnsweredByTheMethodTable(): Unit = {
    val outcome: (Reply, Reply) = serveAndCall { (client: Client[IO], base: Uri) =>
      for {
        _ <- call(client, Request[IO](Method.POST, base / "boards").withEntity("Ops"))
        collection <- call(client, Request[IO](Method.OPTIONS, base / "boards"))
        item <- call(client, Request[IO](Method.OPTIONS, base / "boards" / "1"))
      } yield (collection, item)
    }
    val (collection, item) = outcome

    assertEquals(Status.NoContent, collection.status)
    assertEquals(Set(Method.GET, Method.POST, Method.OPTIONS), collection.allowed)
    assertEquals(None, collection.header(ci"Access-Control-Allow-Origin"))
    assertEquals(Status.NoContent, item.status)
    assertEquals(Set(Method.GET, Method.PUT, Method.DELETE, Method.OPTIONS), item.allowed)
  }

  @Test
  def corsPreflightAdmitsOnlyTheBoardFrontend(): Unit = {
    val outcome: (Reply, Reply) = serveAndCall { (client: Client[IO], base: Uri) =>
      def preflight(origin: String): Request[IO] =
        Request[IO](Method.OPTIONS, base / "boards" / "1").putHeaders(
          Header.Raw(ci"Origin", origin),
          Header.Raw(ci"Access-Control-Request-Method", "PUT"),
          Header.Raw(ci"Access-Control-Request-Headers", "content-type")
        )
      for {
        admitted <- call(client, preflight(FrontendOrigin))
        refused <- call(client, preflight("http://evil.example"))
      } yield (admitted, refused)
    }
    val (admitted, refused) = outcome

    assertEquals(Status.Ok, admitted.status)
    assertEquals(Some(FrontendOrigin), admitted.header(ci"Access-Control-Allow-Origin"))
    assertEquals(
      Set("get", "post", "put", "delete"),
      admitted.headerList(ci"Access-Control-Allow-Methods")
    )
    assertEquals(Set("content-type"), admitted.headerList(ci"Access-Control-Allow-Headers"))
    assertEquals(Some("600"), admitted.header(ci"Access-Control-Max-Age"))
    assertEquals(Set.empty[Method], admitted.allowed)
    assertEquals(None, refused.header(ci"Access-Control-Allow-Origin"))
    assertEquals(None, refused.header(ci"Access-Control-Allow-Methods"))
  }

  @Test
  def crossOriginResponsesExposeHeadersOnlyToTheFrontend(): Unit = {
    val outcome: (Reply, Reply) = serveAndCall { (client: Client[IO], base: Uri) =>
      def list(origin: String): Request[IO] =
        Request[IO](Method.GET, base / "boards").putHeaders(Header.Raw(ci"Origin", origin))
      for {
        _ <- call(client, Request[IO](Method.POST, base / "boards").withEntity("Roadmap"))
        admitted <- call(client, list(FrontendOrigin))
        foreign <- call(client, list("http://evil.example"))
      } yield (admitted, foreign)
    }
    val (admitted, foreign) = outcome

    assertEquals((Status.Ok, "1:Roadmap"), (admitted.status, admitted.body))
    assertEquals(Some(FrontendOrigin), admitted.header(ci"Access-Control-Allow-Origin"))
    assertEquals(Set("x-board-count"), admitted.headerList(ci"Access-Control-Expose-Headers"))
    assertEquals(Some("1"), admitted.header(BoardCount))
    assertEquals((Status.Ok, "1:Roadmap"), (foreign.status, foreign.body))
    assertEquals(None, foreign.header(ci"Access-Control-Allow-Origin"))
    assertEquals(None, foreign.header(ci"Access-Control-Expose-Headers"))
  }
}

object KanbanMethodTableCorsTest {

  /** The browser origin the board frontend is served from. */
  val FrontendOrigin: String = "http://kanban.example:8080"

  /** Response header carrying the number of boards in a listing. */
  val BoardCount: CIString = ci"X-Board-Count"

  /** A board the API stores in memory. */
  final case class Board(name: String, archived: Boolean) {
    def render: String = if (archived) s"$name [archived]" else name
  }

  /** In-memory store; the next id grows monotonically. */
  final case class Boards(nextId: Int, byId: Map[Int, Board])

  /** What the client saw for one request: status, body and response headers. */
  final case class Reply(status: Status, body: String, headers: List[(CIString, String)]) {
    def header(name: CIString): Option[String] = headers.collectFirst { case (`name`, value) => value }

    /** Comma-separated values of every occurrence of `name`, case-folded to lower case. */
    def headerList(name: CIString): Set[String] =
      headers.collect { case (`name`, value) => value }
        .flatMap(_.split(',').map(_.trim.toLowerCase).filter(_.nonEmpty))
        .toSet

    def allowed: Set[Method] =
      headerList(ci"Allow").map(name => Method.fromString(name.toUpperCase).fold(throw _, identity))

    def location: Option[Location] = header(ci"Location").map(value => Location(Uri.unsafeFromString(value)))
  }

  /** The board API; each resource is one method table matched with `->>`. */
  def boardRoutes(store: Ref[IO, Boards]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ (withMethod ->> Root / "boards") =>
      withMethod {
        case GET =>
          store.get.flatMap { state =>
            val lines: List[String] = state.byId.toList.sortBy(_._1).map((id, board) => s"$id:${board.render}")
            Ok(lines.mkString("\n")).map(_.putHeaders(Header.Raw(BoardCount, state.byId.size.toString)))
          }
        case POST =>
          for {
            name <- request.as[String]
            id <- store.modify(state =>
              (Boards(state.nextId + 1, state.byId + (state.nextId -> Board(name, archived = false))), state.nextId)
            )
            response <- Created(id.toString, Location(uri"/boards" / id.toString))
          } yield response
        case OPTIONS =>
          NoContent().map(_.putHeaders(Allow(GET, POST, OPTIONS)))
      }

    case request @ (withMethod ->> Root / "boards" / IntVar(id)) =>
      withMethod {
        case GET =>
          store.get.flatMap(_.byId.get(id).fold(notFound(id))(board => Ok(board.render)))
        case PUT =>
          request.as[String].flatMap { name =>
            update(store, id)(board => Right(board.copy(name = name)))
          }
        case DELETE =>
          store
            .modify(state => (state.copy(byId = state.byId - id), state.byId.contains(id)))
            .flatMap(removed => if (removed) NoContent() else notFound(id))
        case OPTIONS =>
          NoContent().map(_.putHeaders(Allow(GET, PUT, DELETE, OPTIONS)))
      }

    case withMethod ->> Root / "boards" / IntVar(id) / "archive" =>
      withMethod { case POST =>
        update(store, id)(board =>
          if (board.archived) Left("already archived") else Right(board.copy(archived = true))
        )
      }
  }

  /** Applies `change` to board `id`, answering 204, 404 for an unknown board, or 409 on refusal. */
  def update(store: Ref[IO, Boards], id: Int)(change: Board => Either[String, Board]): IO[Response[IO]] =
    store
      .modify { state =>
        state.byId.get(id) match {
          case None => (state, None)
          case Some(board) =>
            change(board) match {
              case Left(reason) => (state, Some(Left(reason)))
              case Right(next) => (state.copy(byId = state.byId.updated(id, next)), Some(Right(())))
            }
        }
      }
      .flatMap {
        case None => notFound(id)
        case Some(Left(reason)) => Conflict(reason)
        case Some(Right(())) => NoContent()
      }

  def notFound(id: Int): IO[Response[IO]] = NotFound(s"no board $id")

  /** The CORS policy admitting only the board frontend. */
  val corsPolicy: CORSPolicy = CORS.policy
    .withAllowOriginHostCi(Set(CIString(FrontendOrigin)))
    .withAllowMethodsIn(Set(Method.GET, Method.POST, Method.PUT, Method.DELETE))
    .withAllowHeadersIn(Set(ci"Content-Type"))
    .withExposeHeadersIn(Set(BoardCount))
    .withAllowCredentials(false)
    .withMaxAge(10.minutes)

  /** Starts the API on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      store <- Resource.eval(Ref.of[IO, Boards](Boards(1, Map.empty)))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(corsPolicy.httpRoutes(boardRoutes(store)).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and records the status, the whole body and every response header. */
  def call(client: Client[IO], request: Request[IO]): IO[Reply] =
    client.run(request).use { response =>
      response.as[String].map { body =>
        Reply(response.status, body, response.headers.headers.map(raw => (raw.name, raw.value)))
      }
    }
}
