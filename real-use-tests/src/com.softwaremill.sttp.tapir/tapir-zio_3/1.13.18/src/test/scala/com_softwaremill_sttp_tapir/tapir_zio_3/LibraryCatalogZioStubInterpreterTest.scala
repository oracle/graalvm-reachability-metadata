/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.testing.BackendStub
import sttp.model.{StatusCode, Uri}
import sttp.tapir.{PublicEndpoint, Schema}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.json.zio.*
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.stub4.TapirStubInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import sttp.tapir.ztapir.*
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{RIO, Ref, Runtime, UIO, Unsafe, ZIO, ZLayer}

/**
 * In-process testing of `ZServerEndpoint`s through the sttp stub interpreter driven by ztapir's
 * `RIOMonadError`, without binding a server.
 *
 * A library catalog defines its endpoints with `zServerLogic` against a `Catalog` service from
 * the ZIO environment. Its test backend is a `BackendStub` over `RIO[Catalog, *]` built from
 * `RIOMonadError`, wired with the application's `ZioHttpServerOptions` interceptors, and called
 * through requests that the sttp client interpreter derives from the same endpoint definitions.
 * The test checks that the real logic runs against the provided service, that decode failures
 * and thrown exceptions go through the application's default handlers, that a server endpoint
 * stubbed with `thenRespondError` answers without touching the service, and that an endpoint
 * that only has a description can be answered with `thenRespond`.
 */
class LibraryCatalogZioStubInterpreterTest {
  import LibraryCatalogZioStubInterpreterTest.*

  @Test
  def stubbedBackendsRunTheCatalogEndpointsInProcess(): Unit = {
    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(exercise.provide(Catalog.layer)).getOrThrowFiberFailure()
    }

    assertEquals(Right(Book("978-0", "Ada Lovelace", "Notes on the Engine")), observed.found)
    assertEquals(Left(NotFound("no book 999")), observed.missing)
    assertEquals(
      Right(List(Book("978-1", "Grace Hopper", "Compilers"), Book("978-2", "Grace Hopper", "COBOL"))),
      observed.byAuthor
    )
    assertEquals(Right(List(Book("978-1", "Grace Hopper", "Compilers"))), observed.byAuthorLimited)
    assertEquals(Right(Receipt("978-1", "alan", 1)), observed.firstReservation)
    assertEquals(Left(OnHold("978-1 is held by alan")), observed.secondReservation)

    assertEquals(StatusCode.BadRequest, observed.badLimit.code)
    assertEquals(Problem("Invalid value for: query parameter limit"), decode[Problem](observed.badLimit.body))

    assertEquals(StatusCode.InternalServerError, observed.searchFailure.code)
    assertEquals(Problem("Internal server error"), decode[Problem](observed.searchFailure.body))

    assertEquals(Left(OnHold("reservations are paused")), observed.pausedReservation)
    assertEquals(StatusCode.BadRequest, observed.pausedMalformed.code)
    assertEquals(List("978-1"), observed.heldAfterStubs)

    assertEquals(Right(List("978-1", "978-2")), observed.recommended)
  }

  private val exercise: RIO[Catalog, Observed] = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val findRequest = client.toRequestThrowDecodeFailures(bookByIsbn.endpoint, Some(base))
    val searchRequest = client.toRequestThrowDecodeFailures(searchBooks.endpoint, Some(base))
    val reserveRequest = client.toRequestThrowDecodeFailures(reserveBook.endpoint, Some(base))
    val recommendRequest = client.toRequestThrowDecodeFailures(recommendations, Some(base))

    val live: Backend[CatalogTask] = stub.whenServerEndpointsRunLogic(serverEndpoints).backend()
    val failing: Backend[CatalogTask] = stub
      .whenServerEndpoint(searchBooks).thenThrowException(new IllegalStateException("index offline"))
      .backend()
    val paused: Backend[CatalogTask] = stub
      .whenServerEndpoint(reserveBook).thenRespondError(OnHold("reservations are paused"))
      .backend()
    val planned: Backend[CatalogTask] = stub
      .whenEndpoint(recommendations).thenRespond(List("978-1", "978-2"))
      .backend()

    for {
      found <- findRequest("978-0").send(live).map(_.body)
      missing <- findRequest("999").send(live).map(_.body)
      byAuthor <- searchRequest(("Grace Hopper", 10)).send(live).map(_.body)
      byAuthorLimited <- searchRequest(("Grace Hopper", 1)).send(live).map(_.body)
      firstReservation <- reserveRequest(("978-1", Hold("alan"))).send(live).map(_.body)
      secondReservation <- reserveRequest(("978-1", Hold("barbara"))).send(live).map(_.body)
      badLimit <- raw(basicRequest.get(uri"$base/books?author=Ada&limit=many"), live)
      searchFailure <- raw(basicRequest.get(uri"$base/books?author=Ada"), failing)
      pausedReservation <- reserveRequest(("978-2", Hold("edsger"))).send(paused).map(_.body)
      pausedMalformed <- raw(
        basicRequest.post(uri"$base/books/978-2/reservations").contentType("application/json").body("{}"),
        paused
      )
      heldAfterStubs <- ZIO.serviceWithZIO[Catalog](_.held)
      recommended <- recommendRequest("978-0").send(planned).map(_.body)
    } yield Observed(
      found,
      missing,
      byAuthor,
      byAuthorLimited,
      firstReservation,
      secondReservation,
      badLimit,
      searchFailure,
      pausedReservation,
      pausedMalformed,
      heldAfterStubs,
      recommended
    )
  }

  private def raw(request: Request[Either[String, String]], backend: Backend[CatalogTask]): CatalogTask[Reply] =
    request.send(backend).map(r => Reply(r.code, r.body.fold(identity, identity)))

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object LibraryCatalogZioStubInterpreterTest {
  type CatalogTask[A] = RIO[Catalog, A]

  final case class Book(isbn: String, author: String, title: String) derives Schema
  final case class Hold(patron: String) derives Schema
  final case class Receipt(isbn: String, patron: String, queuePosition: Int) derives Schema
  final case class Problem(message: String) derives Schema

  sealed trait CatalogError
  final case class NotFound(message: String) extends CatalogError derives Schema
  final case class OnHold(message: String) extends CatalogError derives Schema

  given JsonCodec[Book] = DeriveJsonCodec.gen[Book]
  given JsonCodec[Hold] = DeriveJsonCodec.gen[Hold]
  given JsonCodec[Receipt] = DeriveJsonCodec.gen[Receipt]
  given JsonCodec[Problem] = DeriveJsonCodec.gen[Problem]
  given JsonCodec[NotFound] = DeriveJsonCodec.gen[NotFound]
  given JsonCodec[OnHold] = DeriveJsonCodec.gen[OnHold]

  final case class Reply(code: StatusCode, body: String)

  final case class Observed(
      found: Either[CatalogError, Book],
      missing: Either[CatalogError, Book],
      byAuthor: Either[Unit, List[Book]],
      byAuthorLimited: Either[Unit, List[Book]],
      firstReservation: Either[CatalogError, Receipt],
      secondReservation: Either[CatalogError, Receipt],
      badLimit: Reply,
      searchFailure: Reply,
      pausedReservation: Either[CatalogError, Receipt],
      pausedMalformed: Reply,
      heldAfterStubs: List[String],
      recommended: Either[Unit, List[String]]
  )

  /** Holds the books and their reservations; required by every server logic. */
  trait Catalog {
    def find(isbn: String): UIO[Option[Book]]
    def byAuthor(author: String): UIO[List[Book]]
    def reserve(isbn: String, patron: String): ZIO[Any, CatalogError, Receipt]
    def held: UIO[List[String]]
  }

  object Catalog {
    private val books: List[Book] = List(
      Book("978-0", "Ada Lovelace", "Notes on the Engine"),
      Book("978-1", "Grace Hopper", "Compilers"),
      Book("978-2", "Grace Hopper", "COBOL")
    )

    val layer: ZLayer[Any, Nothing, Catalog] = ZLayer.fromZIO(Ref.make(Map.empty[String, String]).map { holds =>
      new Catalog {
        def find(isbn: String): UIO[Option[Book]] = ZIO.succeed(books.find(_.isbn == isbn))
        def byAuthor(author: String): UIO[List[Book]] = ZIO.succeed(books.filter(_.author == author))
        def reserve(isbn: String, patron: String): ZIO[Any, CatalogError, Receipt] =
          find(isbn).someOrFail(NotFound(s"no book $isbn")) *> holds.modify { current =>
            current.get(isbn) match {
              case Some(holder) => (Left(OnHold(s"$isbn is held by $holder")), current)
              case None => (Right(Receipt(isbn, patron, current.size + 1)), current.updated(isbn, patron))
            }
          }.absolve
        def held: UIO[List[String]] = holds.get.map(_.keys.toList.sorted)
      }
    })
  }

  val base: Uri = uri"http://catalog.test"

  private val catalogErrors = oneOf[CatalogError](
    oneOfVariant(StatusCode.NotFound, jsonBody[NotFound]),
    oneOfVariant(StatusCode.Conflict, jsonBody[OnHold])
  )

  val bookByIsbn: ServerEndpoint.Full[Unit, Unit, String, CatalogError, Book, Any, CatalogTask] =
    endpoint.get.in("books" / path[String]("isbn")).errorOut(catalogErrors).out(jsonBody[Book])
      .zServerLogic[Catalog](isbn => ZIO.serviceWithZIO[Catalog](_.find(isbn)).someOrFail(NotFound(s"no book $isbn")))

  val searchBooks: ServerEndpoint.Full[Unit, Unit, (String, Int), Unit, List[Book], Any, CatalogTask] =
    endpoint.get.in("books").in(query[String]("author")).in(query[Int]("limit").default(10))
      .out(jsonBody[List[Book]])
      .zServerLogic[Catalog] { (author, limit) => ZIO.serviceWithZIO[Catalog](_.byAuthor(author)).map(_.take(limit)) }

  val reserveBook: ServerEndpoint.Full[Unit, Unit, (String, Hold), CatalogError, Receipt, Any, CatalogTask] =
    endpoint.post.in("books" / path[String]("isbn") / "reservations").in(jsonBody[Hold])
      .errorOut(catalogErrors).out(statusCode(StatusCode.Created)).out(jsonBody[Receipt])
      .zServerLogic[Catalog] { (isbn, hold) => ZIO.serviceWithZIO[Catalog](_.reserve(isbn, hold.patron)) }

  /** Described for clients ahead of its implementation, so tests can only stub it. */
  val recommendations: PublicEndpoint[String, Unit, List[String], Any] =
    endpoint.get.in("books" / path[String]("isbn") / "recommendations").out(jsonBody[List[String]])

  val serverEndpoints: List[ZServerEndpoint[Catalog, Any]] = List(bookByIsbn, searchBooks, reserveBook)

  /** The interceptors the application serves with, rendering every default answer as a `Problem`. */
  private val options = ZioHttpServerOptions.customiseInterceptors[Catalog]
    .defaultHandlers(message => ValuedEndpointOutput(jsonBody[Problem], Problem(message)))

  def stub: TapirStubInterpreter[CatalogTask, ZioHttpServerOptions[Catalog]] =
    TapirStubInterpreter(options, BackendStub[CatalogTask](new RIOMonadError[Catalog]))
}
