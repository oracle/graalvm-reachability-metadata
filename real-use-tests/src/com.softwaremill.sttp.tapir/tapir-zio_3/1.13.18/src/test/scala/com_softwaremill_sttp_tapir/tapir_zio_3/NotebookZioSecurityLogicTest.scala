/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.Schema
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Bearer-token security logic (`zServerSecurityLogic`) shared by several endpoints through a
 * `ZPartialServerEndpoint`.
 *
 * A notebook service authenticates every request against a `Sessions` service from the ZIO
 * environment, turning the bearer token into a `User` principal. The partial endpoint is then
 * extended with per-route inputs and outputs and completed with `serverLogic`, whose logic
 * depends on a second `Notebooks` service. The endpoints run on the ZIO HTTP interpreter and are
 * called over HTTP. The test checks that the principal resolved by the security logic scopes the
 * notes each user sees, that unknown tokens fail with the typed 401 error, that a missing
 * `Authorization` header is answered with a bearer challenge, and that role checks in the main
 * logic fail with the shared 403 error variant.
 */
class NotebookZioSecurityLogicTest {
  import NotebookZioSecurityLogicTest.*

  @Test
  def bearerTokensResolveToPrincipalsThatScopeTheNotebook(): Unit = {
    val program: ZIO[Server & Sessions & Notebooks, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe
        .run(program.provide(Server.defaultWithPort(0), Sessions.layer, Notebooks.layer))
        .getOrThrowFiberFailure()
    }

    assertEquals(StatusCode.Created, observed.adaWrites.code)
    assertEquals(Note(1L, "ada", "analytical engine"), decode[Note](observed.adaWrites.body))
    assertEquals(StatusCode.Created, observed.graceWrites.code)
    assertEquals(Note(2L, "grace", "compiler"), decode[Note](observed.graceWrites.body))

    assertEquals(StatusCode.Ok, observed.adaReads.code)
    assertEquals(List(Note(1L, "ada", "analytical engine")), decode[List[Note]](observed.adaReads.body))
    assertEquals(StatusCode.Ok, observed.graceReads.code)
    assertEquals(List("compiler"), decode[List[Note]](observed.graceReads.body).map(_.text))

    assertEquals(StatusCode.Unauthorized, observed.unknownToken.code)
    assertEquals(Unauthorized("unknown session"), decode[Unauthorized](observed.unknownToken.body))

    assertEquals(StatusCode.Unauthorized, observed.noToken.code)
    assertTrue(
      observed.noToken.challenge.exists(_.startsWith("Bearer")),
      s"expected a bearer challenge, got ${observed.noToken.challenge}"
    )

    assertEquals(StatusCode.Forbidden, observed.graceAudits.code)
    assertEquals(Forbidden("grace may not audit"), decode[Forbidden](observed.graceAudits.body))
    assertEquals(StatusCode.Ok, observed.adaAudits.code)
    assertEquals(Audit(2, List("ada", "grace")), decode[Audit](observed.adaAudits.body))
  }

  private def exercise(port: Int, backend: Backend[Task]): Task[Observed] = {
    val base: String = s"http://localhost:$port"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map { r =>
        Reply(r.code, r.body.fold(identity, identity), r.header(HeaderNames.WwwAuthenticate))
      }
    def write(token: String, text: String): Request[Either[String, String]] =
      basicRequest.post(uri"$base/notes").auth.bearer(token).contentType("application/json")
        .body(s"""{"text":"$text"}""")
    for {
      adaWrites <- send(write("token-ada", "analytical engine"))
      graceWrites <- send(write("token-grace", "compiler"))
      adaReads <- send(basicRequest.get(uri"$base/notes").auth.bearer("token-ada"))
      graceReads <- send(basicRequest.get(uri"$base/notes").auth.bearer("token-grace"))
      unknownToken <- send(basicRequest.get(uri"$base/notes").auth.bearer("token-mallory"))
      noToken <- send(basicRequest.get(uri"$base/notes"))
      graceAudits <- send(basicRequest.get(uri"$base/admin/audit").auth.bearer("token-grace"))
      adaAudits <- send(basicRequest.get(uri"$base/admin/audit").auth.bearer("token-ada"))
    } yield Observed(adaWrites, graceWrites, adaReads, graceReads, unknownToken, noToken, graceAudits, adaAudits)
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object NotebookZioSecurityLogicTest {
  final case class User(name: String, admin: Boolean)
  final case class NewNote(text: String) derives Schema
  final case class Note(id: Long, owner: String, text: String) derives Schema
  final case class Audit(notes: Int, owners: List[String]) derives Schema

  sealed trait AccessError
  final case class Unauthorized(reason: String) extends AccessError derives Schema
  final case class Forbidden(reason: String) extends AccessError derives Schema

  given JsonCodec[NewNote] = DeriveJsonCodec.gen[NewNote]
  given JsonCodec[Note] = DeriveJsonCodec.gen[Note]
  given JsonCodec[Audit] = DeriveJsonCodec.gen[Audit]
  given JsonCodec[Unauthorized] = DeriveJsonCodec.gen[Unauthorized]
  given JsonCodec[Forbidden] = DeriveJsonCodec.gen[Forbidden]

  final case class Reply(code: StatusCode, body: String, challenge: Option[String])

  final case class Observed(
      adaWrites: Reply,
      graceWrites: Reply,
      adaReads: Reply,
      graceReads: Reply,
      unknownToken: Reply,
      noToken: Reply,
      graceAudits: Reply,
      adaAudits: Reply
  )

  /** Resolves bearer tokens to users; required by the security logic. */
  trait Sessions {
    def lookup(token: String): UIO[Option[User]]
  }

  object Sessions {
    private val known: Map[String, User] =
      Map("token-ada" -> User("ada", admin = true), "token-grace" -> User("grace", admin = false))

    val layer: ZLayer[Any, Nothing, Sessions] =
      ZLayer.succeed(new Sessions {
        def lookup(token: String): UIO[Option[User]] = ZIO.succeed(known.get(token))
      })
  }

  /** Stores notes; required by the main server logic only. */
  trait Notebooks {
    def add(owner: String, text: String): UIO[Note]
    def all: UIO[List[Note]]
  }

  object Notebooks {
    val layer: ZLayer[Any, Nothing, Notebooks] = ZLayer.fromZIO(Ref.make(Vector.empty[Note]).map { notes =>
      new Notebooks {
        def add(owner: String, text: String): UIO[Note] =
          notes.modify(all => {
            val note: Note = Note(all.size + 1L, owner, text)
            (note, all :+ note)
          })
        def all: UIO[List[Note]] = notes.get.map(_.toList)
      }
    })
  }

  private val accessErrors = oneOf[AccessError](
    oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized]),
    oneOfVariant(StatusCode.Forbidden, jsonBody[Forbidden])
  )

  private val secured: ZPartialServerEndpoint[Sessions, String, User, Unit, AccessError, Unit, Any] =
    endpoint.securityIn(auth.bearer[String]()).errorOut(accessErrors).zServerSecurityLogic[Sessions, User] { token =>
      ZIO.serviceWithZIO[Sessions](_.lookup(token)).someOrFail(Unauthorized("unknown session"))
    }

  val serverEndpoints: List[ZServerEndpoint[Sessions & Notebooks, Any]] = List(
    secured.post.in("notes").in(jsonBody[NewNote]).out(statusCode(StatusCode.Created)).out(jsonBody[Note])
      .serverLogic[Notebooks](user => note => ZIO.serviceWithZIO[Notebooks](_.add(user.name, note.text))),
    secured.get.in("notes").out(jsonBody[List[Note]])
      .serverLogic[Notebooks](user => _ => ZIO.serviceWithZIO[Notebooks](_.all).map(_.filter(_.owner == user.name))),
    secured.get.in("admin" / "audit").out(jsonBody[Audit]).serverLogic[Notebooks] { user => _ =>
      if (!user.admin) ZIO.fail(Forbidden(s"${user.name} may not audit"))
      else ZIO.serviceWithZIO[Notebooks](_.all).map(notes => Audit(notes.size, notes.map(_.owner).distinct.sorted))
    }
  )
}
