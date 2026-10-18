/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.parser.decode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client3.HttpClientSyncBackend
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.jdkhttp.JdkHttpServer
import sttp.tapir.server.jdkhttp.JdkHttpServerOptions
import sttp.tapir.server.model.ValuedEndpointOutput

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Decode-failure reporting for circe JSON bodies: circe decoding errors surfaced as
 * `JsonDecodeException` field paths and schema validation errors, turned into structured JSON
 * problem responses by an application decode failure handler.
 *
 * A sign-up service tells API clients exactly which fields of a rejected registration are wrong.
 * A body circe cannot decode is answered with 400 and one entry per offending JSON path; a body
 * that decodes but breaks the schema's validators is answered with 422 and one entry per violated
 * rule. The service runs on a live JDK HTTP server and the handler is installed through the
 * server options, the way an application customises tapir's error responses.
 */
class RegistrationDecodeFailureReportingTest {
  import RegistrationDecodeFailureReportingTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val accounts: Accounts = new Accounts

  @BeforeEach
  def startServer(): Unit = {
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .decodeFailureHandler(DecodeFailureHandler.pure(problemFor))
      .options
    server = JdkHttpServer()
      .options(options)
      .host("localhost")
      .port(0)
      .addEndpoint(accounts.register)
      .start()
    backend = HttpClientSyncBackend()
    baseUri = uri"http://localhost:${server.getAddress.getPort}"
  }

  @AfterEach
  def stopServer(): Unit = {
    if (backend != null) backend.close()
    if (server != null) server.stop(0)
  }

  @Test
  def acceptsAValidRegistration(): Unit = {
    val registration: Registration = Registration(
      username = "ada",
      email = "ada@example.com",
      age = 36,
      address = Address("12 Analytical Row", "London", "N1 9GU")
    )

    val created: Account = SttpClientInterpreter()
      .toRequestThrowErrors(register, Some(baseUri))
      .apply(registration)
      .send(backend)
      .body

    assertEquals(Account(1, "ada", "London"), created)
    assertEquals(List(registration), accounts.registered)
  }

  @Test
  def reportsEveryFieldCirceCouldNotDecode(): Unit = {
    val response: Response[Either[String, String]] = post(
      """{"username":"bob","email":"bob@example.com","age":"forty","address":{"street":"1 Main St","postcode":"AB1"}}"""
    )

    assertEquals(StatusCode.BadRequest, response.code)
    assertTrue(response.header("Content-Type").exists(_.startsWith("application/json")))
    val problem: Problem = readProblem(response)
    assertEquals("malformed-registration", problem.kind)
    assertEquals(List("address.city", "age"), problem.fields.map(_.path).sorted)
    assertTrue(accounts.registered.isEmpty)
  }

  @Test
  def reportsABodyThatIsNotJsonWithoutAFieldPath(): Unit = {
    val response: Response[Either[String, String]] = post("""{"username":"bob",""")

    assertEquals(StatusCode.BadRequest, response.code)
    val problem: Problem = readProblem(response)
    assertEquals("malformed-registration", problem.kind)
    assertEquals(List(""), problem.fields.map(_.path))
    assertTrue(accounts.registered.isEmpty)
  }

  @Test
  def reportsEveryViolatedValidationRule(): Unit = {
    val response: Response[Either[String, String]] = post(
      """{"username":"al","email":"al@example.com","age":15,"address":{"street":"1 Main St","city":"Leeds","postcode":"LS1"}}"""
    )

    assertEquals(StatusCode.UnprocessableEntity, response.code)
    val problem: Problem = readProblem(response)
    assertEquals("invalid-registration", problem.kind)
    assertEquals(
      List(FieldProblem("age", "must be at least 18"), FieldProblem("username", "must have at least 3 characters")),
      problem.fields.sortBy(_.path)
    )
    assertTrue(accounts.registered.isEmpty)
  }

  @Test
  def exposesTheDecodingErrorsToApplicationCode(): Unit = {
    val codec: Codec[String, Registration, CodecFormat.Json] = circeCodec[Registration]

    codec.decode("""{"username":"carol","email":"carol@example.com","age":30,"address":{"street":7,"city":"York","postcode":"YO1"}}""") match {
      case DecodeResult.Error(_, JsonDecodeException(errors, _)) =>
        assertEquals(List(List("address", "street")), errors.map(_.path.map(_.name)))
      case other =>
        throw new AssertionError(s"expected a JSON decoding error, got $other")
    }
  }

  private def post(body: String): Response[Either[String, String]] =
    basicRequest
      .post(uri"$baseUri/registrations")
      .contentType(MediaType.ApplicationJson)
      .body(body)
      .send(backend)

  private def readProblem(response: Response[Either[String, String]]): Problem =
    decode[Problem](response.body.fold(identity, identity)).fold(throw _, identity)
}

object RegistrationDecodeFailureReportingTest {
  final case class Address(street: String, city: String, postcode: String) derives CirceCodec.AsObject, Schema

  final case class Registration(username: String, email: String, age: Int, address: Address)
      derives CirceCodec.AsObject

  object Registration {
    given Schema[Registration] = Schema
      .derived[Registration]
      .modify(_.username)(_.validate(Validator.minLength(3)))
      .modify(_.age)(_.validate(Validator.min(18)))
  }

  final case class Account(id: Long, username: String, city: String) derives CirceCodec.AsObject, Schema

  final case class FieldProblem(path: String, message: String) derives CirceCodec.AsObject, Schema

  final case class Problem(kind: String, fields: List[FieldProblem]) derives CirceCodec.AsObject, Schema

  val register: PublicEndpoint[Registration, Unit, Account, Any] = endpoint.post
    .in("registrations")
    .in(jsonBody[Registration])
    .out(statusCode(StatusCode.Created))
    .out(jsonBody[Account])

  private val problemOutput: EndpointOutput[(StatusCode, Problem)] = statusCode.and(jsonBody[Problem])

  /** Answers a failed JSON body with a problem document; other failures fall through to tapir. */
  def problemFor(context: DecodeFailureContext): Option[ValuedEndpointOutput[?]] =
    context.failure match {
      case DecodeResult.Error(_, JsonDecodeException(errors, _)) =>
        val fields: List[FieldProblem] = errors.map(error => FieldProblem(pathOf(error.path), error.msg))
        Some(ValuedEndpointOutput(problemOutput, (StatusCode.BadRequest, Problem("malformed-registration", fields))))
      case DecodeResult.InvalidValue(errors) =>
        val fields: List[FieldProblem] = errors.map(error => FieldProblem(pathOf(error.path), ruleOf(error)))
        Some(ValuedEndpointOutput(problemOutput, (StatusCode.UnprocessableEntity, Problem("invalid-registration", fields))))
      case _ =>
        None
    }

  private def pathOf(path: List[FieldName]): String = path.map(_.name).mkString(".")

  private def ruleOf(error: ValidationError[?]): String = error.validator match {
    case Validator.Min(value, _)       => s"must be at least $value"
    case Validator.MinLength(value, _) => s"must have at least $value characters"
    case other                         => s"violates $other"
  }

  /** Registered accounts, appended to only when the business logic runs. */
  final class Accounts {
    private val registrations: CopyOnWriteArrayList[Registration] = new CopyOnWriteArrayList[Registration]()

    def registered: List[Registration] = registrations.toArray(Array.empty[Registration]).toList

    val register = RegistrationDecodeFailureReportingTest.register.handleSuccess { registration =>
      registrations.add(registration)
      Account(registrations.size().toLong, registration.username, registration.address.city)
    }
  }
}
