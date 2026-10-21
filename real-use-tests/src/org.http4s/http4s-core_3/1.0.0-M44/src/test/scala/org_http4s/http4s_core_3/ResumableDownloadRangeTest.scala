/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.http4s.EntityTag
import org.http4s.Headers
import org.http4s.RangeUnit
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.headers.ETag
import org.http4s.headers.Range
import org.http4s.headers.`Accept-Ranges`
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`Content-Range`
import org.http4s.headers.`If-Range`
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

import java.nio.charset.StandardCharsets

/**
 * Serves byte-range requests with the `Range`, `Content-Range`, `Accept-Ranges`, and `If-Range`
 * headers of `org.http4s.headers`.
 *
 * A firmware download service advertises byte ranges, answers a resuming client with
 * `206 Partial Content` and the matching slice, falls back to the full image when the client's
 * `If-Range` validator is stale, and rejects ranges past the end of the image with `416`.
 */
class ResumableDownloadRangeTest {

  @Test
  def advertisesByteRangesOnAFullDownload(): Unit = {
    val response: Response[IO] = serve(Request[IO](uri = uri"/firmware/router-7.2.bin"))

    assertEquals(Status.Ok, response.status)
    assertEquals(Some(`Accept-Ranges`.bytes), response.headers.get[`Accept-Ranges`])
    assertEquals(Some("bytes"), rawValue(response.headers, ci"Accept-Ranges"))
    assertEquals(Some(Image.length.toLong), response.headers.get[`Content-Length`].map(_.length))
    assertEquals(FirmwareText, bodyText(response))
  }

  @Test
  def resumesAnInterruptedDownloadFromTheLastReceivedByte(): Unit = {
    val received: Int = 16
    val validator: `If-Range` = `If-Range`.ETag(EntityTag(ImageVersion))
    val resume: Request[IO] = Request[IO](uri = uri"/firmware/router-7.2.bin")
      .putHeaders(Range(received.toLong), validator)
    assertEquals(Some(s"bytes=$received-"), rawValue(resume.headers, ci"Range"))

    val response: Response[IO] = serve(resume)
    assertEquals(Status.PartialContent, response.status)
    val contentRange: `Content-Range` =
      response.headers.get[`Content-Range`].getOrElse(throw new AssertionError("no Content-Range"))
    assertEquals(RangeUnit.Bytes, contentRange.unit)
    assertEquals(received.toLong, contentRange.range.first)
    assertEquals(Some((Image.length - 1).toLong), contentRange.range.second)
    assertEquals(Some(Image.length.toLong), contentRange.length)
    assertEquals(
      Some(s"bytes $received-${Image.length - 1}/${Image.length}"),
      rawValue(response.headers, ci"Content-Range")
    )
    assertEquals(FirmwareText.substring(received), bodyText(response))
  }

  @Test
  def servesExplicitAndSuffixRangesParsedFromTheWire(): Unit = {
    val parsed: Range = Range.parse("bytes=0-7, -6").fold(failure => throw failure, identity)
    assertEquals(RangeUnit.Bytes, parsed.unit)
    assertEquals(List(Range.SubRange(0L, 7L), Range.SubRange(-6L)), parsed.ranges.toList)

    val header: Request[IO] = Request[IO](
      uri = uri"/firmware/router-7.2.bin",
      headers = Headers("Range" -> "bytes=0-7")
    )
    val head: Response[IO] = serve(header)
    assertEquals(Status.PartialContent, head.status)
    assertEquals(FirmwareText.substring(0, 8), bodyText(head))
    assertEquals(Some(8L), head.headers.get[`Content-Length`].map(_.length))

    val trailer: Request[IO] = Request[IO](uri = uri"/firmware/router-7.2.bin")
      .putHeaders(Range(Range.SubRange(-6L)))
    val tail: Response[IO] = serve(trailer)
    assertEquals(Status.PartialContent, tail.status)
    assertEquals(FirmwareText.takeRight(6), bodyText(tail))
    assertEquals(
      Some(`Content-Range`(Image.length.toLong - 6, Image.length.toLong - 1)
        .copy(length = Some(Image.length.toLong))),
      tail.headers.get[`Content-Range`]
    )
  }

  @Test
  def sendsTheWholeImageWhenTheIfRangeValidatorIsStale(): Unit = {
    val stale: Request[IO] = Request[IO](
      uri = uri"/firmware/router-7.2.bin",
      headers = Headers("Range" -> "bytes=10-", "If-Range" -> "\"fw-7.1\"")
    )
    val ifRange: `If-Range` =
      stale.headers.get[`If-Range`].getOrElse(throw new AssertionError("no If-Range"))
    assertEquals(`If-Range`.ETag(EntityTag("fw-7.1")), ifRange)

    val response: Response[IO] = serve(stale)
    assertEquals(Status.Ok, response.status)
    assertTrue(response.headers.get[`Content-Range`].isEmpty)
    assertEquals(FirmwareText, bodyText(response))
  }

  @Test
  def rejectsARangeThatStartsPastTheEndOfTheImage(): Unit = {
    val beyond: Request[IO] = Request[IO](uri = uri"/firmware/router-7.2.bin")
      .putHeaders(Range(Image.length.toLong + 100, Image.length.toLong + 200))
    val response: Response[IO] = serve(beyond)

    assertEquals(Status.RangeNotSatisfiable, response.status)
    assertEquals(Some(s"bytes */${Image.length}"), rawValue(response.headers, ci"Content-Range"))
    assertTrue(`Content-Range`.parse("bytes 0-9/*").exists(_.length.isEmpty))
    assertTrue(Range.parse("lines=1-2").exists(_.unit == RangeUnit("lines")))
    assertTrue(Range.parse("bytes=").isLeft)
  }

  /** The entity tag of the firmware image the service currently hosts. */
  val ImageVersion: String = "fw-7.2"

  /** The firmware image text; its bytes are what clients download. */
  val FirmwareText: String = "ROUTER-FW-7.2|bootloader|kernel|rootfs|checksum=9f3a"

  val Image: Array[Byte] = FirmwareText.getBytes(StandardCharsets.US_ASCII)

  /** Answers a download, honouring a single byte range unless `If-Range` names an older image. */
  def serve(request: Request[IO]): Response[IO] = {
    val total: Long = Image.length.toLong
    val validatorMatches: Boolean = request.headers.get[`If-Range`] match {
      case Some(`If-Range`.ETag(tag)) => tag == EntityTag(ImageVersion)
      case Some(_) => false
      case None => true
    }
    val requested: Option[Range.SubRange] = request.headers.get[Range]
      .filter(range => range.unit == RangeUnit.Bytes && range.ranges.tail.isEmpty)
      .map(_.ranges.head)
      .filter(_ => validatorMatches)

    requested match {
      case None =>
        imageSlice(Status.Ok, 0L, total - 1)
      case Some(Range.SubRange(first, _)) if first >= total =>
        Response[IO](Status.RangeNotSatisfiable)
          .putHeaders(`Accept-Ranges`.bytes, "Content-Range" -> s"bytes */$total")
      case Some(Range.SubRange(first, last)) =>
        val start: Long = if (first < 0) math.max(0L, total + first) else first
        val end: Long = if (first < 0) total - 1 else last.fold(total - 1)(math.min(_, total - 1))
        imageSlice(Status.PartialContent, start, end)
          .putHeaders(`Content-Range`(RangeUnit.Bytes, Range.SubRange(start, end), Some(total)))
    }
  }

  /** Builds a response carrying the image bytes from `start` to `end`, both inclusive. */
  def imageSlice(status: Status, start: Long, end: Long): Response[IO] = {
    val slice: Array[Byte] = Image.slice(start.toInt, end.toInt + 1)
    Response[IO](status)
      .withBodyStream(Stream.emits(slice.toSeq).covary[IO])
      .putHeaders(
        `Accept-Ranges`.bytes,
        ETag(EntityTag(ImageVersion)),
        `Content-Length`.unsafeFromLong(slice.length.toLong)
      )
  }

  /** Reads the downloaded bytes back as the text they encode. */
  def bodyText(response: Response[IO]): String =
    new String(response.body.compile.to(Array).unsafeRunSync(), StandardCharsets.US_ASCII)

  /** Returns the rendered value of a header the way it goes out on the wire. */
  def rawValue(headers: Headers, name: CIString): Option[String] =
    headers.get(name).map(_.head.value)
}
