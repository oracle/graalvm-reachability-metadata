/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.Charset
import org.http4s.DecodeFailure
import org.http4s.Entity
import org.http4s.EntityDecoder
import org.http4s.Headers
import org.http4s.MediaType
import org.http4s.MediaTypeMismatch
import org.http4s.Method
import org.http4s.Request
import org.http4s.headers.`Content-Disposition`
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.Multipart
import org.http4s.multipart.Multiparts
import org.http4s.multipart.Part
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scodec.bits.ByteVector

/**
 * Builds, encodes, and parses `multipart/form-data` bodies with the `org.http4s.multipart`
 * model (`Multiparts`, `Multipart`, `Part`) and its entity codecs.
 *
 * An expense-tracking client uploads a receipt scan together with form fields, and the expense
 * service parses the multipart body back into named fields and a file part.
 */
class ExpenseReceiptUploadTest {

  @Test
  def uploadsAReceiptAndParsesItBackIntoAnExpenseClaim(): Unit = {
    val scan: Array[Byte] = Array[Byte](0x25, 0x50, 0x44, 0x46, 0x2d, 0x00, 0x7f, -1, -128, 0x0a)
    val request: Request[IO] = (for {
      multiparts <- Multiparts.forSync[IO]
      multipart <- multiparts.multipart(
        Vector[Part[IO]](
          Part.formData("amount", "42.50"),
          Part.formData("description", "Team lunch \u2013 Z\u00fcrich"),
          Part.fileData[IO](
            "receipt",
            "lunch-receipt.pdf",
            Entity.strict(ByteVector(scan)),
            `Content-Type`(MediaType.application.pdf)
          )
        )
      )
    } yield Request[IO](Method.POST, uri"/expenses/claims")
      .withEntity(multipart)
      .putHeaders(multipart.headers)).unsafeRunSync()

    val contentType: `Content-Type` =
      request.headers.get[`Content-Type`].getOrElse(throw new AssertionError("no Content-Type"))
    assertTrue(contentType.mediaType.satisfies(MediaType.multipart.`form-data`), contentType.toString)
    val boundary: String =
      contentType.mediaType.extensions.getOrElse("boundary", throw new AssertionError("no boundary"))
    assertTrue(boundary.nonEmpty)

    val wireBody: String = request.as[String].unsafeRunSync()
    assertTrue(wireBody.contains(s"--$boundary"), wireBody)
    assertTrue(wireBody.contains(s"--$boundary--"), wireBody)
    assertTrue(wireBody.contains("Content-Disposition: form-data; name=\"amount\""), wireBody)
    assertTrue(
      wireBody.contains("Content-Disposition: form-data; name=\"receipt\"; filename=\"lunch-receipt.pdf\""),
      wireBody
    )

    val claim: ExpenseClaim = receive(request)
    assertEquals(BigDecimal("42.50"), claim.amount)
    assertEquals("Team lunch \u2013 Z\u00fcrich", claim.description)
    assertEquals("lunch-receipt.pdf", claim.receiptName)
    assertEquals(Some(MediaType.application.pdf), claim.receiptType)
    assertArrayEquals(scan, claim.receipt)
  }

  @Test
  def parsesARawBrowserUploadWithAFixedBoundary(): Unit = {
    val boundary: String = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
    val body: String = List(
      s"--$boundary",
      "Content-Disposition: form-data; name=\"amount\"",
      "",
      "18.00",
      s"--$boundary",
      "Content-Disposition: form-data; name=\"description\"",
      "",
      "Taxi to airport",
      s"--$boundary",
      "Content-Disposition: form-data; name=\"receipt\"; filename=\"taxi.txt\"",
      "Content-Type: text/plain",
      "",
      "Fare: 18.00 CHF",
      s"--$boundary--",
      ""
    ).mkString("\r\n")
    val request: Request[IO] = Request[IO](Method.POST, uri"/expenses/claims")
      .withEntity(body)
      .withHeaders(Headers("Content-Type" -> s"multipart/form-data; boundary=$boundary"))

    val multipart: Multipart[IO] = request.as[Multipart[IO]].unsafeRunSync()
    assertEquals(boundary, multipart.boundary.value)
    assertEquals(Vector(Some("amount"), Some("description"), Some("receipt")), multipart.parts.map(_.name))
    val receiptPart: Part[IO] = multipart.parts.find(_.name.contains("receipt")).get
    assertEquals(Some("taxi.txt"), receiptPart.filename)
    assertEquals(Some(MediaType.text.plain), receiptPart.contentType.map(_.mediaType))
    val disposition: `Content-Disposition` = receiptPart.headers
      .get[`Content-Disposition`]
      .getOrElse(throw new AssertionError("no Content-Disposition"))
    assertEquals("form-data", disposition.dispositionType)

    val claim: ExpenseClaim = receive(request)
    assertEquals(BigDecimal("18.00"), claim.amount)
    assertEquals("Taxi to airport", claim.description)
    assertEquals("Fare: 18.00 CHF", new String(claim.receipt, Charset.`UTF-8`.nioCharset))
  }

  @Test
  def rejectsANonMultipartBodyBeforeReadingIt(): Unit = {
    val request: Request[IO] = Request[IO](Method.POST, uri"/expenses/claims")
      .withEntity("amount=12.00&description=Coffee")
      .withHeaders(Headers("Content-Type" -> "application/x-www-form-urlencoded"))

    val result: Either[DecodeFailure, Multipart[IO]] =
      EntityDecoder[IO, Multipart[IO]].decode(request, strict = true).value.unsafeRunSync()
    result match {
      case Left(mismatch: MediaTypeMismatch) =>
        assertEquals(MediaType.application.`x-www-form-urlencoded`, mismatch.messageType)
        assertTrue(mismatch.expected.exists(range => MediaType.multipart.`form-data`.satisfies(range)))
      case other => throw new AssertionError(s"unexpected decode result $other")
    }
  }

  /** An expense claim as the service stores it after parsing the upload. */
  case class ExpenseClaim(
      amount: BigDecimal,
      description: String,
      receiptName: String,
      receiptType: Option[MediaType],
      receipt: Array[Byte]
  )

  /** Parses an uploaded claim the way the expense service endpoint does. */
  def receive(request: Request[IO]): ExpenseClaim = (for {
    multipart <- request.as[Multipart[IO]]
    amount <- field(multipart, "amount")
    description <- field(multipart, "description")
    receiptPart = multipart.parts
      .find(part => part.name.contains("receipt") && part.filename.isDefined)
      .getOrElse(throw new AssertionError("no receipt file part"))
    receipt <- receiptPart.body.compile.to(Array)
  } yield ExpenseClaim(
    BigDecimal(amount),
    description,
    receiptPart.filename.get,
    receiptPart.contentType.map(_.mediaType),
    receipt
  )).unsafeRunSync()

  /** Reads the text of the named form field. */
  def field(multipart: Multipart[IO], name: String): IO[String] =
    multipart.parts.find(_.name.contains(name)) match {
      case Some(part) => part.bodyText.compile.string
      case None => IO.raiseError(new AssertionError(s"missing field $name"))
    }
}
