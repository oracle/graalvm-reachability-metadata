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
import sttp.model.StatusCode
import sttp.tapir.Schema.annotations.{validate, validateEach}
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import sttp.tapir.{Schema, ValidationResult, Validator}
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Request validation of endpoint inputs and derived body schemas before `zServerLogic` runs.
 *
 * A race-entry desk accepts registrations whose case class carries `@validate` and
 * `@validateEach` annotations (length, range, pattern and enumeration validators) plus a
 * whole-object `Validator.custom` rule, and lists entries by a pattern-validated race code
 * path and a range-validated `limit` query. The endpoints are served by the ZIO HTTP
 * interpreter on a real socket. The test checks that valid input reaches the logic, which
 * reads its registry from the ZIO environment, that each violated rule is rejected with
 * 400 and a message naming the failing field, and that rejected requests never reach the
 * logic and so leave the registry unchanged.
 */
class RaceEntryZioValidationTest {
  import RaceEntryZioValidationTest.*

  @Test
  def invalidInputsAreRejectedBeforeTheLogicTouchesTheRegistry(): Unit = {
    val program: ZIO[Server & Registry, Throwable, (Observed, Int)] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
      stored <- ZIO.serviceWithZIO[Registry](_.size)
    } yield (observed, stored)

    val (observed, stored): (Observed, Int) = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program.provide(Server.defaultWithPort(0), InMemoryRegistry.layer))
        .getOrThrowFiberFailure()
    }

    assertEquals(StatusCode.Created, observed.accepted.code)
    assertEquals(
      Entry(1, "RUN-042", Registration("Ada Lovelace", 36, "ada@example.org", "half", List("club", "pacer"), Some(7))),
      decode[Entry](observed.accepted.body)
    )
    assertEquals(StatusCode.Created, observed.secondAccepted.code)
    assertEquals(2, decode[Entry](observed.secondAccepted.body).number)

    assertRejected(observed.shortName, "name")
    assertRejected(observed.tooYoung, "age")
    assertRejected(observed.badEmail, "email")
    assertRejected(observed.unknownDistance, "distance")
    assertRejected(observed.emptyTag, "tags")
    assertRejected(observed.negativeBib, "bib")
    assertRejected(observed.marathonUnderage, "a full marathon entrant must be at least 18")
    assertRejected(observed.badRaceCode, "race")
    assertRejected(observed.limitTooLarge, "limit")

    assertEquals(StatusCode.Ok, observed.listed.code)
    assertEquals(List(1), decode[List[Entry]](observed.listed.body).map(_.number))
    assertEquals(StatusCode.Ok, observed.otherRace.code)
    assertEquals(List(2), decode[List[Entry]](observed.otherRace.body).map(_.number))

    assertEquals(2, stored)
  }

  private def assertRejected(reply: Reply, mentioned: String): Unit = {
    assertEquals(StatusCode.BadRequest, reply.code, reply.body)
    assertTrue(reply.body.contains(mentioned), reply.body)
  }

  private def exercise(port: Int, backend: Backend[Task]): Task[Observed] = {
    val base: String = s"http://localhost:$port"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map(r => Reply(r.code, r.body.fold(identity, identity)))
    def register(race: String, json: String): Task[Reply] =
      send(basicRequest.post(uri"$base/races/$race/entries").contentType("application/json").body(json))
    def entry(name: String, age: Int, email: String, distance: String, tags: String, bib: String): String =
      s"""{"name":"$name","age":$age,"email":"$email","distance":"$distance","tags":$tags$bib}"""
    for {
      accepted <- register("RUN-042", entry("Ada Lovelace", 36, "ada@example.org", "half", """["club","pacer"]""", ""","bib":7"""))
      secondAccepted <- register("TRI-001", entry("Grace Hopper", 41, "grace@example.org", "10k", "[]", ""))
      shortName <- register("RUN-042", entry("A", 36, "a@example.org", "5k", "[]", ""))
      tooYoung <- register("RUN-042", entry("Tim Young", 12, "tim@example.org", "5k", "[]", ""))
      badEmail <- register("RUN-042", entry("Linus T", 30, "not-an-email", "5k", "[]", ""))
      unknownDistance <- register("RUN-042", entry("Linus T", 30, "linus@example.org", "ultra", "[]", ""))
      emptyTag <- register("RUN-042", entry("Linus T", 30, "linus@example.org", "5k", """["club",""]""", ""))
      negativeBib <- register("RUN-042", entry("Linus T", 30, "linus@example.org", "5k", "[]", ""","bib":-3"""))
      marathonUnderage <- register("RUN-042", entry("Sam Teen", 17, "sam@example.org", "full", "[]", ""))
      badRaceCode <- register("run42", entry("Linus T", 30, "linus@example.org", "5k", "[]", ""))
      limitTooLarge <- send(basicRequest.get(uri"$base/races/RUN-042/entries?limit=500"))
      listed <- send(basicRequest.get(uri"$base/races/RUN-042/entries?limit=10"))
      otherRace <- send(basicRequest.get(uri"$base/races/TRI-001/entries"))
    } yield Observed(
      accepted, secondAccepted, shortName, tooYoung, badEmail, unknownDistance, emptyTag, negativeBib,
      marathonUnderage, badRaceCode, limitTooLarge, listed, otherRace
    )
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object RaceEntryZioValidationTest {
  final case class Registration(
      @validate(Validator.minLength(2)) @validate(Validator.maxLength(40)) name: String,
      @validate(Validator.inRange(14, 99)) age: Int,
      @validate(Validator.pattern("^[^@\\s]+@[^@\\s]+\\.[a-z]+$")) email: String,
      @validate(Validator.enumeration(List("5k", "10k", "half", "full"))) distance: String,
      @validateEach(Validator.minLength(1)) tags: List[String],
      @validateEach(Validator.positive[Int]) bib: Option[Int]
  )

  object Registration {
    private val marathonAge: Validator[Registration] = Validator.custom { r =>
      if (r.distance != "full" || r.age >= 18) ValidationResult.Valid
      else ValidationResult.Invalid("a full marathon entrant must be at least 18")
    }
    given Schema[Registration] = Schema.derived[Registration].validate(marathonAge)
    given JsonCodec[Registration] = DeriveJsonCodec.gen[Registration]
  }

  final case class Entry(number: Int, race: String, registration: Registration) derives Schema

  object Entry {
    given JsonCodec[Entry] = DeriveJsonCodec.gen[Entry]
  }

  final case class Reply(code: StatusCode, body: String)

  final case class Observed(
      accepted: Reply,
      secondAccepted: Reply,
      shortName: Reply,
      tooYoung: Reply,
      badEmail: Reply,
      unknownDistance: Reply,
      emptyTag: Reply,
      negativeBib: Reply,
      marathonUnderage: Reply,
      badRaceCode: Reply,
      limitTooLarge: Reply,
      listed: Reply,
      otherRace: Reply
  )

  /** The service the endpoint logic requires from the ZIO environment. */
  trait Registry {
    def add(race: String, registration: Registration): UIO[Entry]
    def forRace(race: String, limit: Int): UIO[List[Entry]]
    def size: UIO[Int]
  }

  final class InMemoryRegistry(entries: Ref[Vector[Entry]]) extends Registry {
    def add(race: String, registration: Registration): UIO[Entry] =
      entries.modify { all =>
        val entry: Entry = Entry(all.size + 1, race, registration)
        (entry, all :+ entry)
      }

    def forRace(race: String, limit: Int): UIO[List[Entry]] =
      entries.get.map(_.filter(_.race == race).take(limit).toList)

    def size: UIO[Int] = entries.get.map(_.size)
  }

  object InMemoryRegistry {
    val layer: ZLayer[Any, Nothing, Registry] =
      ZLayer.fromZIO(Ref.make(Vector.empty[Entry]).map(new InMemoryRegistry(_)))
  }

  private val raceCode =
    path[String]("race").validate(Validator.pattern("^[A-Z]{3}-[0-9]{3}$"))

  private val registerEntry =
    endpoint.post.in("races" / raceCode / "entries").in(jsonBody[Registration])
      .out(statusCode(StatusCode.Created)).out(jsonBody[Entry])

  private val listEntries =
    endpoint.get.in("races" / raceCode / "entries")
      .in(query[Int]("limit").default(20).validate(Validator.inRange(1, 50)))
      .out(jsonBody[List[Entry]])

  val serverEndpoints: List[ZServerEndpoint[Registry, Any]] = List(
    registerEntry.zServerLogic[Registry] { (race, registration) =>
      ZIO.serviceWithZIO[Registry](_.add(race, registration))
    },
    listEntries.zServerLogic[Registry] { (race, limit) =>
      ZIO.serviceWithZIO[Registry](_.forRace(race, limit))
    }
  )
}
