/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.data.Chain
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.Charset
import org.http4s.DecodeFailure
import org.http4s.DecodeResult
import org.http4s.EntityDecoder
import org.http4s.Headers
import org.http4s.HttpVersion
import org.http4s.InvalidMessageBodyFailure
import org.http4s.MediaType
import org.http4s.MediaTypeMismatch
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.UrlForm
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`Content-Type`
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Encodes and decodes `application/x-www-form-urlencoded` bodies with `UrlForm` and the
 * `EntityEncoder`/`EntityDecoder` entity codecs.
 *
 * A newsletter service receives an HTML signup form, decodes it into a domain value through a
 * custom entity decoder layered on the `UrlForm` decoder, and turns decoding failures into the
 * HTTP responses an application returns to the browser.
 */
class NewsletterSignupFormTest {

  @Test
  def submitsAndDecodesARepeatedFieldSignupForm(): Unit = {
    val form: UrlForm = UrlForm(
      "email" -> "ada+news@example.org",
      "name" -> "Ada Lovelace",
      "topic" -> "scala",
      "topic" -> "native image"
    )
    val request: Request[IO] = Request[IO](Method.POST, uri"/newsletter/signup").withEntity(form)

    val contentType: `Content-Type` =
      request.headers.get[`Content-Type`].getOrElse(throw new AssertionError("no Content-Type"))
    assertEquals(MediaType.application.`x-www-form-urlencoded`, contentType.mediaType)
    assertEquals(Some(Charset.`UTF-8`), contentType.charset)

    val wireBody: String = request.as[String].unsafeRunSync()
    assertTrue(wireBody.contains("email=ada%2Bnews%40example.org"), wireBody)
    assertTrue(wireBody.contains("topic=scala&topic=native"), wireBody)
    assertEquals(
      Some(wireBody.getBytes(Charset.`UTF-8`.nioCharset).length.toLong),
      request.headers.get[`Content-Length`].map(_.length)
    )

    val signup: Signup = request.as[Signup].unsafeRunSync()
    assertEquals(Signup("ada+news@example.org", "Ada Lovelace", List("scala", "native image")), signup)

    val response: Response[IO] = handle(request)
    assertEquals(Status.Created, response.status)
    assertEquals("Subscribed ada+news@example.org to native image, scala", response.as[String].unsafeRunSync())
    assertEquals(
      Some(MediaType.text.plain),
      response.headers.get[`Content-Type`].map(_.mediaType)
    )
  }

  @Test
  def decodesARawBrowserBodyWithPlusEncodedSpaces(): Unit = {
    val request: Request[IO] = Request[IO](Method.POST, uri"/newsletter/signup")
      .withEntity("email=grace%40example.org&name=Grace+Hopper&topic=compilers")
      .withHeaders(Headers("Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8"))

    val form: UrlForm = request.as[UrlForm].unsafeRunSync()
    assertEquals(Some("Grace Hopper"), form.getFirst("name"))
    assertEquals(Chain("compilers"), form.get("topic"))
    assertEquals(Chain.empty[String], form.get("referrer"))

    assertEquals(Right(form), UrlForm.decodeString(Charset.`UTF-8`)(UrlForm.encodeString(Charset.`UTF-8`)(form)))
    assertEquals(Status.Created, handle(request).status)
  }

  @Test
  def rejectsAFormWithoutAValidEmailAsUnprocessable(): Unit = {
    val request: Request[IO] = Request[IO](Method.POST, uri"/newsletter/signup")
      .withEntity(UrlForm("email" -> "not-an-address", "topic" -> "scala"))

    val failure: DecodeFailure = decodeSignup(request) match {
      case Left(decodeFailure) => decodeFailure
      case Right(signup) => throw new AssertionError(s"unexpected signup $signup")
    }
    assertTrue(failure.isInstanceOf[InvalidMessageBodyFailure])
    assertEquals("Invalid message body: email is missing or malformed", failure.message)

    val response: Response[IO] = handle(request)
    assertEquals(Status.UnprocessableEntity, response.status)
    assertEquals("The request body was invalid.", response.as[String].unsafeRunSync())
  }

  @Test
  def rejectsABodyWithAnUnsupportedMediaType(): Unit = {
    val request: Request[IO] = Request[IO](Method.POST, uri"/newsletter/signup")
      .withEntity("{\"email\":\"ada@example.org\"}")
      .withHeaders(Headers("Content-Type" -> "application/json"))

    val failure: DecodeFailure = decodeSignup(request) match {
      case Left(decodeFailure) => decodeFailure
      case Right(signup) => throw new AssertionError(s"unexpected signup $signup")
    }
    failure match {
      case mismatch: MediaTypeMismatch =>
        assertEquals(MediaType.application.json, mismatch.messageType)
        assertTrue(mismatch.expected.exists(range =>
          MediaType.application.`x-www-form-urlencoded`.satisfies(range)))
      case other => throw new AssertionError(s"unexpected failure $other")
    }
    assertEquals(Status.UnsupportedMediaType, handle(request).status)
  }

  /** A confirmed newsletter subscription. */
  case class Signup(email: String, name: String, topics: List[String])

  /** Decodes a signup from the form the browser posts, validating the e-mail address. */
  given EntityDecoder[IO, Signup] = EntityDecoder[IO, UrlForm].flatMapR { form =>
    form.getFirst("email").filter(_.matches("[^@\\s]+@[^@\\s]+\\.[a-z]+")) match {
      case Some(email) =>
        DecodeResult.successT[IO, Signup](
          Signup(email, form.getFirstOrElse("name", "subscriber"), form.get("topic").toList)
        )
      case None =>
        DecodeResult.failureT[IO, Signup](InvalidMessageBodyFailure("email is missing or malformed"))
    }
  }

  /** Decodes a signup strictly, so a body of any other media type is rejected unread. */
  def decodeSignup(request: Request[IO]): Either[DecodeFailure, Signup] =
    EntityDecoder[IO, Signup].decode(request, strict = true).value.unsafeRunSync()

  /** Handles a signup request the way the service endpoint does. */
  def handle(request: Request[IO]): Response[IO] =
    decodeSignup(request) match {
      case Right(signup) =>
        Response[IO](Status.Created)
          .withEntity(s"Subscribed ${signup.email} to ${signup.topics.sorted.mkString(", ")}")
      case Left(failure) =>
        failure.toHttpResponse[IO](HttpVersion.`HTTP/1.1`)
    }
}
