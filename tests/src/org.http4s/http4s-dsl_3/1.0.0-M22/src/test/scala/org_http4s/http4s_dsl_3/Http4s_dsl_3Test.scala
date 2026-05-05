/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.AuthedRequest
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.impl.FlagQueryParamMatcher
import org.http4s.dsl.impl.MatrixVar
import org.http4s.dsl.impl.OptionalMultiQueryParamDecoderMatcher
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.dsl.impl.OptionalValidatingQueryParamDecoderMatcher
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.dsl.impl.QueryParamDecoderMatcherWithDefault
import org.http4s.dsl.impl.ValidatingQueryParamDecoderMatcher
import org.http4s.dsl.io.*
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.util.UUID

class Http4s_dsl_3Test {
  import Http4s_dsl_3Test.*

  @Test
  def typedPathExtractorsAndFileExtensionRoutesSelectExpectedResponses(): Unit = {
    val userId: Long = 922337203685477580L
    val userResponse: Response[IO] = run(Request[IO](Method.GET, Uri.unsafeFromString(s"/users/$userId")))
    assertEquals(Status.Ok, userResponse.status)
    assertEquals(s"user:$userId", bodyText(userResponse))

    val requestId: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val auditResponse: Response[IO] = run(Request[IO](Method.GET, Uri.unsafeFromString(s"/audits/$requestId")))
    assertEquals(Status.Ok, auditResponse.status)
    assertEquals("audit:123e4567-e89b-12d3-a456-426614174000", bodyText(auditResponse))

    val assetResponse: Response[IO] = run(Request[IO](Method.GET, uri"/assets/report.final.json"))
    assertEquals(Status.Ok, assetResponse.status)
    assertEquals("asset:report.final:json", bodyText(assetResponse))

    val invalidLongResponse: Response[IO] = run(Request[IO](Method.GET, uri"/users/not-a-number"))
    assertEquals(Status.NotFound, invalidLongResponse.status)
  }

  @Test
  def matrixVariablesAndHeadGetMethodConcatMatchStructuredPaths(): Unit = {
    val matrixResponse: Response[IO] = run(Request[IO](Method.GET, uri"/board/square;x=5;y=3"))
    assertEquals(Status.Ok, matrixResponse.status)
    assertEquals("square:5,3", bodyText(matrixResponse))

    val matrixWithReorderedVariables: Response[IO] = run(Request[IO](Method.GET, uri"/board/square;y=7;x=2"))
    assertEquals(Status.Ok, matrixWithReorderedVariables.status)
    assertEquals("square:2,7", bodyText(matrixWithReorderedVariables))

    val badMatrixResponse: Response[IO] = run(Request[IO](Method.GET, uri"/board/square;x=bad;y=3"))
    assertEquals(Status.NotFound, badMatrixResponse.status)

    val getHealthResponse: Response[IO] = run(Request[IO](Method.GET, uri"/health"))
    assertEquals(Status.NoContent, getHealthResponse.status)

    val headHealthResponse: Response[IO] = run(Request[IO](Method.HEAD, uri"/health"))
    assertEquals(Status.NoContent, headHealthResponse.status)
  }

  @Test
  def requiredOptionalDefaultFlagAndRepeatedQueryParametersCompose(): Unit = {
    val response: Response[IO] = run(Request[IO](Method.GET, uri"/items?limit=10&page=3&sort=name&details&tag=scala&tag=native"))
    assertEquals(Status.Ok, response.status)
    assertEquals("limit=10,page=3,sort=name,details=true,tags=scala|native", bodyText(response))

    val defaultedResponse: Response[IO] = run(Request[IO](Method.GET, uri"/items?limit=2"))
    assertEquals(Status.Ok, defaultedResponse.status)
    assertEquals("limit=2,page=1,sort=none,details=false,tags=", bodyText(defaultedResponse))

    val invalidRequiredParameter: Response[IO] = run(Request[IO](Method.GET, uri"/items?limit=abc"))
    assertEquals(Status.NotFound, invalidRequiredParameter.status)

    val invalidRepeatedParameter: Response[IO] = run(Request[IO](Method.GET, uri"/numbers?n=1&n=bad&n=3"))
    assertEquals(Status.BadRequest, invalidRepeatedParameter.status)

    val repeatedNumbers: Response[IO] = run(Request[IO](Method.GET, uri"/numbers?n=1&n=2&n=3"))
    assertEquals(Status.Ok, repeatedNumbers.status)
    assertEquals("sum=6", bodyText(repeatedNumbers))
  }

  @Test
  def validatingQueryParametersExposeParseFailuresWithoutBypassingRoutes(): Unit = {
    val valid: Response[IO] = run(Request[IO](Method.GET, uri"/validate?age=42&score=98.5"))
    assertEquals(Status.Ok, valid.status)
    assertEquals("age=42,score=98.5", bodyText(valid))

    val missingOptional: Response[IO] = run(Request[IO](Method.GET, uri"/validate?age=42"))
    assertEquals(Status.Ok, missingOptional.status)
    assertEquals("age=42,score=missing", bodyText(missingOptional))

    val invalidAge: Response[IO] = run(Request[IO](Method.GET, uri"/validate?age=old"))
    assertEquals(Status.BadRequest, invalidAge.status)
    assertTrue(bodyText(invalidAge).contains("age"))

    val invalidScore: Response[IO] = run(Request[IO](Method.GET, uri"/validate?age=42&score=excellent"))
    assertEquals(Status.BadRequest, invalidScore.status)
    assertTrue(bodyText(invalidScore).contains("score"))

    val missingRequiredAge: Response[IO] = run(Request[IO](Method.GET, uri"/validate?score=1.0"))
    assertEquals(Status.NotFound, missingRequiredAge.status)
  }

  @Test
  def prefixPathExtractorCapturesFirstSegmentAndRemainingRelativePath(): Unit = {
    val nested: Response[IO] = run(Request[IO](Method.GET, uri"/docs/guides/native-image"))
    assertEquals(Status.Ok, nested.status)
    assertEquals("section=docs,remaining=guides/native-image", bodyText(nested))

    val emptyTail: Response[IO] = run(Request[IO](Method.GET, uri"/docs"))
    assertEquals(Status.Ok, emptyTail.status)
    assertEquals("section=docs,remaining=", bodyText(emptyTail))

    val unmatchedPrefix: Response[IO] = run(Request[IO](Method.GET, uri"/articles/guides/native-image"))
    assertEquals(Status.NotFound, unmatchedPrefix.status)
  }

  @Test
  def requestBodyDecodingAndEntityResponseGeneratorsCreateStatusHeadersAndBodies(): Unit = {
    val created: Response[IO] = run(Request[IO](Method.POST, uri"/echo").withEntity("native-image"))
    assertEquals(Status.Created, created.status)
    assertEquals("egami-evitan", bodyText(created))

    val redirect: Response[IO] = run(Request[IO](Method.GET, uri"/redirect"))
    assertEquals(Status.SeeOther, redirect.status)
    assertEquals(Some(Location(uri"/target")), redirect.headers.get[Location])

    val gone: Response[IO] = run(Request[IO](Method.DELETE, uri"/temporary"))
    assertEquals(Status.Gone, gone.status)
    assertEquals("removed", bodyText(gone))
  }

  @Test
  def methodResourceExtractorProducesAllowedResponsesAndMethodErrors(): Unit = {
    val read: Response[IO] = run(Request[IO](Method.GET, uri"/resource"))
    assertEquals(Status.Ok, read.status)
    assertEquals("read", bodyText(read))

    val updated: Response[IO] = run(Request[IO](Method.PUT, uri"/resource"))
    assertEquals(Status.Created, updated.status)
    assertEquals("updated", bodyText(updated))

    val notAllowed: Response[IO] = run(Request[IO](Method.POST, uri"/resource"))
    assertEquals(Status.MethodNotAllowed, notAllowed.status)
  }

  @Test
  def authedRoutesExtractAuthenticatedContextWithDslAsMatcher(): Unit = {
    val response: Response[IO] = runAuthed("test-user", Request[IO](Method.GET, uri"/profile"))
    assertEquals(Status.Ok, response.status)
    assertEquals("authenticated:test-user", bodyText(response))
  }

  private def run(request: Request[IO]): Response[IO] = routes.orNotFound(request).unsafeRunSync()

  private def runAuthed(user: String, request: Request[IO]): Response[IO] =
    authedRoutes.run(AuthedRequest(user, request)).getOrElse(Response[IO](Status.NotFound)).unsafeRunSync()

  private def bodyText(response: Response[IO]): String = response.as[String].unsafeRunSync()
}

object Http4s_dsl_3Test {
  private object LimitParam extends QueryParamDecoderMatcher[Int]("limit")
  private object PageParam extends QueryParamDecoderMatcherWithDefault[Int]("page", 1)
  private object SortParam extends OptionalQueryParamDecoderMatcher[String]("sort")
  private object DetailsFlag extends FlagQueryParamMatcher("details")
  private object TagParams extends OptionalMultiQueryParamDecoderMatcher[String]("tag")
  private object NumbersParam extends OptionalMultiQueryParamDecoderMatcher[Int]("n")
  private object AgeParam extends ValidatingQueryParamDecoderMatcher[Int]("age")
  private object ScoreParam extends OptionalValidatingQueryParamDecoderMatcher[Double]("score")
  private object BoardSquare extends MatrixVar[List]("square", List("x", "y"))

  private val authedRoutes: AuthedRoutes[String, IO] = AuthedRoutes.of[String, IO] {
    case GET -> Root / "profile" as user =>
      Ok(s"authenticated:$user")
  }

  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "users" / LongVar(userId) =>
      Ok(s"user:$userId")

    case GET -> Root / "audits" / UUIDVar(requestId) =>
      Ok(s"audit:$requestId")

    case GET -> Root / "assets" / (baseName ~ extension) =>
      Ok(s"asset:$baseName:$extension")

    case GET -> Root / "board" / BoardSquare(IntVar(x), IntVar(y)) =>
      Ok(s"square:$x,$y")

    case GET -> "docs" /: remaining =>
      Ok(s"section=docs,remaining=${remaining.renderString}")

    case (GET | HEAD) -> Root / "health" =>
      NoContent()

    case GET -> Root / "items" :? LimitParam(limit) +& PageParam(page) +& SortParam(sort) +& DetailsFlag(details) +& TagParams(tags) =>
      tags.fold(
        failures => BadRequest(failures.toList.map(_.sanitized).mkString("\n")),
        values => Ok(s"limit=$limit,page=$page,sort=${sort.getOrElse("none")},details=$details,tags=${values.mkString("|")}")
      )

    case GET -> Root / "numbers" :? NumbersParam(numbers) =>
      numbers.fold(
        failures => BadRequest(failures.toList.map(_.sanitized).mkString("\n")),
        values => Ok(s"sum=${values.sum}")
      )

    case GET -> Root / "validate" :? AgeParam(age) +& ScoreParam(score) =>
      age.fold(
        failures => BadRequest(failures.toList.map(failure => s"age:${failure.sanitized}").mkString("\n")),
        ageValue => score match {
          case Some(scoreValue) =>
            scoreValue.fold(
              failures => BadRequest(failures.toList.map(failure => s"score:${failure.sanitized}").mkString("\n")),
              value => Ok(s"age=$ageValue,score=$value")
            )
          case None =>
            Ok(s"age=$ageValue,score=missing")
        }
      )

    case request @ POST -> Root / "echo" =>
      request.as[String].flatMap(value => Created(value.reverse))

    case GET -> Root / "redirect" =>
      SeeOther(Location(uri"/target"))

    case DELETE -> Root / "temporary" =>
      Gone("removed")

    case method ->> Root / "resource" =>
      method {
        case GET => Ok("read")
        case PUT => Created("updated")
      }
  }
}
