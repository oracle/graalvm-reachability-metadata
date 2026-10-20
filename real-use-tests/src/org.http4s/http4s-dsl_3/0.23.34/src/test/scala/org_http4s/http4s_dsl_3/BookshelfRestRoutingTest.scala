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
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Allow
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.http4s.server.Router
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Path-segment routing of a REST resource with typed path extractors and HTTP method matching.
 *
 * A bookshelf service keeps titles in memory and exposes them under `/api/books` through the
 * http4s DSL: collection and item routes are matched on method and path, the numeric book id is
 * extracted with `IntVar`, creation answers `201 Created` with a `Location` header, and methods
 * the item route does not support are refused with `405` and an `Allow` header. The service runs
 * on an Ember server bound to an ephemeral port and is driven by an Ember client, as an
 * application deployment would be.
 */
class BookshelfRestRoutingTest {
  import BookshelfRestRoutingTest.*

  @Test
  def bookshelfRoutesServeCrudLifecycleOverHttp(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: Uri) =>
      val books: Uri = base / "api" / "books"
      for {
        emptyList <- call(client, Request[IO](Method.GET, books))
        created <- client.run(Request[IO](Method.POST, books).withEntity("Dune")).use { response =>
          response.as[String].map(body => (response.status, response.headers.get[Location], body))
        }
        _ <- call(client, Request[IO](Method.POST, books).withEntity("Solaris"))
        listed <- call(client, Request[IO](Method.GET, books))
        fetched <- call(client, Request[IO](Method.GET, books / "1"))
        renamed <- call(client, Request[IO](Method.PUT, books / "2").withEntity("Solaris (1961)"))
        refetched <- call(client, Request[IO](Method.GET, books / "2"))
        removed <- call(client, Request[IO](Method.DELETE, books / "1"))
        missing <- call(client, Request[IO](Method.GET, books / "1"))
        notNumeric <- call(client, Request[IO](Method.GET, books / "dune"))
        notAllowed <- client.run(Request[IO](Method.PATCH, books / "2")).use { response =>
          IO.pure((response.status, response.headers.get[Allow]))
        }
        remaining <- call(client, Request[IO](Method.GET, books))
      } yield Observed(
        emptyList,
        created,
        listed,
        fetched,
        renamed,
        refetched,
        removed,
        missing,
        notNumeric,
        notAllowed,
        remaining
      )
    }

    assertEquals((Status.Ok, ""), outcome.emptyList)
    assertEquals(Status.Created, outcome.created._1)
    assertEquals(Some(Location(uri"/api/books/1")), outcome.created._2)
    assertEquals("1", outcome.created._3)
    assertEquals((Status.Ok, "1:Dune\n2:Solaris"), outcome.listed)
    assertEquals((Status.Ok, "Dune"), outcome.fetched)
    assertEquals((Status.NoContent, ""), outcome.renamed)
    assertEquals((Status.Ok, "Solaris (1961)"), outcome.refetched)
    assertEquals((Status.NoContent, ""), outcome.removed)
    assertEquals((Status.NotFound, "no book 1"), outcome.missing)
    assertEquals(Status.NotFound, outcome.notNumeric._1)
    assertEquals(Status.MethodNotAllowed, outcome.notAllowed._1)
    assertEquals(
      Set(Method.GET, Method.PUT, Method.DELETE),
      outcome.notAllowed._2.map(_.methods).getOrElse(Set.empty)
    )
    assertEquals((Status.Ok, "2:Solaris (1961)"), outcome.remaining)
  }
}

object BookshelfRestRoutingTest {

  /** What the client saw at each step of the bookshelf lifecycle. */
  final case class Observed(
      emptyList: (Status, String),
      created: (Status, Option[Location], String),
      listed: (Status, String),
      fetched: (Status, String),
      renamed: (Status, String),
      refetched: (Status, String),
      removed: (Status, String),
      missing: (Status, String),
      notNumeric: (Status, String),
      notAllowed: (Status, Option[Allow]),
      remaining: (Status, String)
  )

  /** In-memory catalogue the routes read and write; the next id grows monotonically. */
  final case class Shelf(nextId: Int, books: Map[Int, String])

  /** The bookshelf API an application would mount under `/api`. */
  def bookRoutes(shelf: Ref[IO, Shelf]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "books" =>
      shelf.get.flatMap { state =>
        Ok(state.books.toList.sortBy(_._1).map((id, title) => s"$id:$title").mkString("\n"))
      }

    case request @ POST -> Root / "books" =>
      for {
        title <- request.as[String]
        id <- shelf.modify(state => (Shelf(state.nextId + 1, state.books + (state.nextId -> title)), state.nextId))
        response <- Created(id.toString, Location(uri"/api/books" / id.toString))
      } yield response

    case GET -> Root / "books" / IntVar(id) =>
      shelf.get.flatMap(_.books.get(id).fold(NotFound(s"no book $id"))(title => Ok(title)))

    case request @ PUT -> Root / "books" / IntVar(id) =>
      request.as[String].flatMap { title =>
        shelf
          .modify(state =>
            if (state.books.contains(id)) (state.copy(books = state.books.updated(id, title)), true)
            else (state, false)
          )
          .flatMap(updated => if (updated) NoContent() else NotFound(s"no book $id"))
      }

    case DELETE -> Root / "books" / IntVar(id) =>
      shelf
        .modify(state => (state.copy(books = state.books - id), state.books.contains(id)))
        .flatMap(removed => if (removed) NoContent() else NotFound(s"no book $id"))

    case _ -> Root / "books" / IntVar(_) =>
      MethodNotAllowed(Allow(Method.GET, Method.PUT, Method.DELETE))
  }

  /** Starts the service on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      shelf <- Resource.eval(Ref.of[IO, Shelf](Shelf(1, Map.empty)))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(Router("/api" -> bookRoutes(shelf)).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the whole response body as text. */
  def call(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))
}
