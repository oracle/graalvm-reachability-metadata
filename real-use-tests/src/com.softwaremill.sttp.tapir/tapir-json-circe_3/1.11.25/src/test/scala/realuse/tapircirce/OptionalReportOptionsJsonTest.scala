/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Codec
import io.circe.Json
import io.circe.parser.parse
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises optional Circe JSON bodies for report jobs with optional execution overrides. */
class OptionalReportOptionsJsonTest extends TapirJsonCirce {
  case class Options(format: String, includeDetails: Boolean) derives Codec.AsObject, Schema
  case class Job(number: Int, options: Options) derives Codec.AsObject, Schema

  @Test
  @Timeout(60)
  def usesDefaultsForAbsentOptionsAndPreservesExplicitOverrides(): Unit = {
    val defaults: Options = Options("csv", false)
    val jobs: Ref[IO, List[Option[Options]]] =
      Ref.of[IO, List[Option[Options]]](Nil).unsafeRunSync()
    val launch = endpoint.post
      .in("reports")
      .in(jsonBody[Option[Options]])
      .out(statusCode(sttp.model.StatusCode.Created))
      .out(jsonBody[Job])
      .serverLogicSuccess[IO] { overrideOptions =>
        jobs.modify { previous =>
          (previous :+ overrideOptions, Job(previous.size + 1, overrideOptions.getOrElse(defaults)))
        }
      }
    val lastOverride = endpoint.get
      .in("reports" / "last-override")
      .out(jsonBody[Option[Options]])
      .serverLogicSuccess[IO] { _ => jobs.get.map(_.lastOption.flatten) }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]()
      .toRoutes(List(launch, lastOverride)).orNotFound

    def execute(request: Request[IO]): (Status, String) = (for {
      response <- app.run(request)
      text <- response.bodyText.compile.string
    } yield (response.status, text)).timeout(15.seconds).unsafeRunSync()

    def submit(body: Option[String]): (Status, String) = {
      val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/reports"))
      val withBody: Request[IO] = body.fold(request)(request.withEntity(_))
      execute(withBody.putHeaders(`Content-Type`(MediaType.application.json)))
    }

    def assertJob(body: Option[String], expected: String): Unit = {
      val result: (Status, String) = submit(body)
      assertEquals(Status.Created, result._1)
      assertEquals(parse(expected).fold(throw _, identity), parse(result._2).fold(throw _, identity))
    }

    def readOverride(): (Status, String) = execute(
      Request[IO](Method.GET, Uri.unsafeFromString("/reports/last-override"))
    )

    // A caller can omit the entire body, not just optional fields inside an object.
    assertJob(None, """{"number":1,"options":{"format":"csv","includeDetails":false}}""")
    assertEquals((Status.Ok, ""), readOverride())

    assertJob(Some("null"), """{"number":2,"options":{"format":"csv","includeDetails":false}}""")
    assertEquals((Status.Ok, ""), readOverride())

    val explicit: String = """{"format":"pdf","includeDetails":true}"""
    assertJob(Some(explicit), """{"number":3,"options":{"format":"pdf","includeDetails":true}}""")
    val saved: (Status, String) = readOverride()
    val expectedOptions: Json = parse(explicit).fold(throw _, identity)
    assertEquals(Status.Ok, saved._1)
    assertEquals(expectedOptions, parse(saved._2).fold(throw _, identity))

    // Optional does not mean malformed or incomplete objects may silently become defaults.
    assertEquals(Status.BadRequest, submit(Some("{"))._1)
    assertEquals(Status.BadRequest, submit(Some("{}"))._1)
    assertEquals(Status.BadRequest, submit(Some("""{"format":"pdf","includeDetails":"yes"}"""))._1)
    assertEquals(
      List(None, None, Some(Options("pdf", true))),
      jobs.get.unsafeRunSync()
    )
    assertEquals(expectedOptions, parse(readOverride()._2).fold(throw _, identity))

    assertJob(None, """{"number":4,"options":{"format":"csv","includeDetails":false}}""")
    assertEquals((Status.Ok, ""), readOverride())
  }
}
