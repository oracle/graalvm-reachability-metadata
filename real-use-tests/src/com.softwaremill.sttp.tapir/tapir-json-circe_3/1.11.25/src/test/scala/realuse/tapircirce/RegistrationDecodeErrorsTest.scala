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
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.model.ValuedEndpointOutput

import scala.concurrent.duration.*

/** Exercises Circe accumulated decoding errors for registration form field feedback. */
class RegistrationDecodeErrorsTest extends TapirJsonCirce {
  case class Address(postcode: String, houseNumber: Int) derives Codec.AsObject, Schema
  case class Registration(name: String, age: Int, address: Address) derives Codec.AsObject, Schema
  case class InvalidFields(fields: List[String]) derives Codec.AsObject, Schema

  @Test
  @Timeout(60)
  def returnsAllInvalidFieldsBeforeAcceptingCorrectedRegistration(): Unit = {
    val registrations: Ref[IO, List[Registration]] = Ref.of[IO, List[Registration]](Nil).unsafeRunSync()
    val failureHandler: DecodeFailureHandler[IO] = DecodeFailureHandler.pure[IO] { context =>
      context.failure match {
        case DecodeResult.Error(_, error: JsonDecodeException) =>
          val fields: List[String] = error.errors.map { detail =>
            if (detail.path.isEmpty) "$" else detail.path.map(_.encodedName).mkString(".")
          }.sorted
          Some(ValuedEndpointOutput(
            statusCode(sttp.model.StatusCode.BadRequest).and(jsonBody[InvalidFields]),
            InvalidFields(fields)
          ))
        case _ => None
      }
    }
    val options: Http4sServerOptions[IO] = Http4sServerOptions.customiseInterceptors[IO]
      .decodeFailureHandler(failureHandler)
      .options
    val register = endpoint.post
      .in("registrations")
      .in(jsonBody[Registration])
      .out(stringBody)
      .serverLogicSuccess[IO] { registration =>
        registrations.update(_ :+ registration).as(s"Registered ${registration.name}")
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO](options).toRoutes(register).orNotFound

    def submit(body: String): (Status, Option[MediaType], String) = {
      val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/registrations"))
        .withEntity(body)
        .putHeaders(`Content-Type`(MediaType.application.json))
      (for {
        response <- app.run(request)
        text <- response.bodyText.compile.string
      } yield (response.status, response.contentType.map(_.mediaType), text))
        .timeout(15.seconds).unsafeRunSync()
    }

    val invalid: (Status, Option[MediaType], String) = submit("""{
      "name": 123,
      "age": "adult",
      "address": {"postcode": "OX1 1AA", "houseNumber": "twelve"}
    }""")
    val expectedFields: Json = parse(
      """{"fields":["address.houseNumber","age","name"]}"""
    ).fold(throw _, identity)
    assertEquals(Status.BadRequest, invalid._1)
    assertEquals(Some(MediaType.application.json), invalid._2)
    assertEquals(expectedFields, parse(invalid._3).fold(throw _, identity))
    assertEquals(List.empty[Registration], registrations.get.unsafeRunSync())

    val malformed: (Status, Option[MediaType], String) = submit("{\"name\":")
    assertEquals(Status.BadRequest, malformed._1)
    assertEquals(Some(MediaType.application.json), malformed._2)
    assertEquals(
      parse("""{"fields":["$"]}""").fold(throw _, identity),
      parse(malformed._3).fold(throw _, identity)
    )
    assertEquals(List.empty[Registration], registrations.get.unsafeRunSync())

    val accepted: (Status, Option[MediaType], String) = submit("""{
      "name": "Ada",
      "age": 28,
      "address": {"postcode": "OX1 1AA", "houseNumber": 12}
    }""")
    assertEquals(Status.Ok, accepted._1)
    assertEquals("Registered Ada", accepted._3)
    assertEquals(
      List(Registration("Ada", 28, Address("OX1 1AA", 12))),
      registrations.get.unsafeRunSync()
    )
  }
}
