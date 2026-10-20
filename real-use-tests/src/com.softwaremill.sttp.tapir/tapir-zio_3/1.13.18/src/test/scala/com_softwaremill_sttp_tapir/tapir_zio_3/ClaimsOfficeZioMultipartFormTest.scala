/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.{MediaType, Part, StatusCode}
import sttp.tapir.{Schema, TapirFile}
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Form request bodies (`multipartBody` and `formBody` decoded into case classes) handled by
 * `zServerLogic` over the ZIO HTTP interpreter.
 *
 * An insurance claims office accepts claims as `multipart/form-data`: text fields, a numeric
 * field, an optional note, a scanned receipt that the interpreter spools to a temporary
 * `TapirFile`, and a photo read into memory as bytes. Follow-up remarks arrive as an
 * `application/x-www-form-urlencoded` form. The logic needs a `ClaimRegistry` service from the
 * ZIO environment. The test uploads real files with an sttp client and checks that every part
 * reaches the logic with its value, file name and content type, that the file contents are
 * intact, that typed `ZIO.fail` errors map to their `oneOf` status codes, and that a missing
 * required part or an unparsable number is rejected with 400 before the logic runs.
 */
class ClaimsOfficeZioMultipartFormTest {
  import ClaimsOfficeZioMultipartFormTest.*

  @Test
  def claimsAreUploadedAsMultipartFormsAndAnnotatedWithUrlEncodedForms(@TempDir directory: Path): Unit = {
    val receipt: Path = directory.resolve("receipt-0042.txt")
    Files.writeString(receipt, "Taxi 18.50 EUR\nHotel 120.00 EUR\n", StandardCharsets.UTF_8)
    val photo: Array[Byte] = Array[Byte](0x89.toByte, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3)

    val program: ZIO[Server & ClaimRegistry, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend, receipt, photo)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program.provide(Server.defaultWithPort(0), InMemoryClaimRegistry.layer)).getOrThrowFiberFailure()
    }

    assertEquals(StatusCode.Created, observed.filed.code, observed.filed.body)
    val claim: Claim = decode[Claim](observed.filed.body)
    assertEquals(1L, claim.id)
    assertEquals("Ada Lovelace", claim.claimant)
    assertEquals(3, claim.nights)
    assertEquals(Some("conference in Lisbon"), claim.note)
    assertEquals(Some("receipt-0042.txt"), claim.receiptName)
    assertEquals("Taxi 18.50 EUR\nHotel 120.00 EUR\n", claim.receiptText)
    assertEquals(Some("image/png"), claim.photoContentType)
    assertEquals(photo.length, claim.photoSize)
    assertEquals(List(), claim.remarks)

    assertEquals(StatusCode.Created, observed.withoutNote.code)
    val second: Claim = decode[Claim](observed.withoutNote.body)
    assertEquals(2L, second.id)
    assertEquals(None, second.note)
    assertEquals(Some("scan.txt"), second.receiptName)

    assertEquals(StatusCode.UnprocessableEntity, observed.emptyReceipt.code)
    assertEquals(Rejected("the receipt must not be empty"), decode[Rejected](observed.emptyReceipt.body))

    assertEquals(StatusCode.BadRequest, observed.missingPart.code)
    assertEquals(StatusCode.BadRequest, observed.badNumber.code)

    assertEquals(StatusCode.Ok, observed.remarked.code)
    assertEquals(
      List(Remark("Grace", "approved & paid, see ref #7")),
      decode[Claim](observed.remarked.body).remarks
    )

    assertEquals(StatusCode.NotFound, observed.remarkMissing.code)
    assertEquals(UnknownClaim(99L), decode[UnknownClaim](observed.remarkMissing.body))

    assertEquals(StatusCode.Ok, observed.listed.code)
    val listed: List[Claim] = decode[List[Claim]](observed.listed.body)
    assertEquals(List(1L, 2L), listed.map(_.id))
    assertEquals(List(1, 0), listed.map(_.remarks.size))
  }

  private def exercise(port: Int, backend: Backend[Task], receipt: Path, photo: Array[Byte]): Task[Observed] = {
    val base: String = s"http://localhost:$port"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map(r => Reply(r.code, r.body.fold(identity, identity)))
    def photoPart: Part[BasicBodyPart] = multipart("photo", photo).fileName("photo.png").contentType(MediaType.ImagePng)
    for {
      emptyFile <- ZIO.attemptBlocking(Files.createTempFile(receipt.getParent, "empty", ".txt"))
      filed <- send(basicRequest.post(uri"$base/claims").multipartBody(
        multipart("claimant", "Ada Lovelace"),
        multipart("nights", "3"),
        multipart("note", "conference in Lisbon"),
        multipartFile("receipt", receipt).contentType(MediaType.TextPlain),
        photoPart
      ))
      withoutNote <- send(basicRequest.post(uri"$base/claims").multipartBody(
        multipart("claimant", "Charles Babbage"),
        multipart("nights", "1"),
        multipartFile("receipt", receipt).fileName("scan.txt"),
        photoPart
      ))
      emptyReceipt <- send(basicRequest.post(uri"$base/claims").multipartBody(
        multipart("claimant", "Alan Turing"),
        multipart("nights", "2"),
        multipartFile("receipt", emptyFile),
        photoPart
      ))
      missingPart <- send(basicRequest.post(uri"$base/claims").multipartBody(
        multipart("claimant", "Alan Turing"),
        multipart("nights", "2"),
        photoPart
      ))
      badNumber <- send(basicRequest.post(uri"$base/claims").multipartBody(
        multipart("claimant", "Alan Turing"),
        multipart("nights", "two"),
        multipartFile("receipt", receipt),
        photoPart
      ))
      remarked <- send(basicRequest.post(uri"$base/claims/1/remarks")
        .body(Map("author" -> "Grace", "text" -> "approved & paid, see ref #7")))
      remarkMissing <- send(basicRequest.post(uri"$base/claims/99/remarks")
        .body(Map("author" -> "Grace", "text" -> "lost")))
      listed <- send(basicRequest.get(uri"$base/claims"))
    } yield Observed(filed, withoutNote, emptyReceipt, missingPart, badNumber, remarked, remarkMissing, listed)
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object ClaimsOfficeZioMultipartFormTest {

  /** The multipart form of a new claim: every field is one part, decoded by its codec. */
  final case class ClaimForm(
      claimant: String,
      nights: Int,
      note: Option[String],
      receipt: Part[TapirFile],
      photo: Part[Array[Byte]]
  ) derives Schema

  /** The url-encoded form of a remark on an existing claim. */
  final case class RemarkForm(author: String, text: String) derives Schema

  final case class Remark(author: String, text: String) derives Schema
  final case class Claim(
      id: Long,
      claimant: String,
      nights: Int,
      note: Option[String],
      receiptName: Option[String],
      receiptText: String,
      photoContentType: Option[String],
      photoSize: Int,
      remarks: List[Remark]
  ) derives Schema

  sealed trait ClaimError
  final case class Rejected(reason: String) extends ClaimError derives Schema
  final case class UnknownClaim(id: Long) extends ClaimError derives Schema

  given JsonCodec[Remark] = DeriveJsonCodec.gen[Remark]
  given JsonCodec[Claim] = DeriveJsonCodec.gen[Claim]
  given JsonCodec[Rejected] = DeriveJsonCodec.gen[Rejected]
  given JsonCodec[UnknownClaim] = DeriveJsonCodec.gen[UnknownClaim]

  final case class Reply(code: StatusCode, body: String)

  final case class Observed(
      filed: Reply,
      withoutNote: Reply,
      emptyReceipt: Reply,
      missingPart: Reply,
      badNumber: Reply,
      remarked: Reply,
      remarkMissing: Reply,
      listed: Reply
  )

  /** The service the endpoint logic requires from the ZIO environment. */
  trait ClaimRegistry {
    def file(claim: Long => Claim): UIO[Claim]
    def remark(id: Long, remark: Remark): UIO[Option[Claim]]
    def all: UIO[List[Claim]]
  }

  final class InMemoryClaimRegistry(claims: Ref[Map[Long, Claim]], ids: Ref[Long]) extends ClaimRegistry {
    def file(claim: Long => Claim): UIO[Claim] = for {
      id <- ids.updateAndGet(_ + 1)
      filed = claim(id)
      _ <- claims.update(_.updated(id, filed))
    } yield filed

    def remark(id: Long, remark: Remark): UIO[Option[Claim]] =
      claims.modify { current =>
        current.get(id).map(c => c.copy(remarks = c.remarks :+ remark)) match {
          case Some(updated) => (Some(updated), current.updated(id, updated))
          case None          => (None, current)
        }
      }

    def all: UIO[List[Claim]] = claims.get.map(_.values.toList.sortBy(_.id))
  }

  object InMemoryClaimRegistry {
    val layer: ZLayer[Any, Nothing, ClaimRegistry] = ZLayer.fromZIO(for {
      claims <- Ref.make(Map.empty[Long, Claim])
      ids <- Ref.make(0L)
    } yield new InMemoryClaimRegistry(claims, ids))
  }

  private val claimErrors = oneOf[ClaimError](
    oneOfVariant(StatusCode.UnprocessableEntity, jsonBody[Rejected]),
    oneOfVariant(StatusCode.NotFound, jsonBody[UnknownClaim])
  )

  private val fileClaim =
    endpoint.post.in("claims").in(multipartBody[ClaimForm]).errorOut(claimErrors)
      .out(statusCode(StatusCode.Created)).out(jsonBody[Claim])

  private val remarkClaim =
    endpoint.post.in("claims" / path[Long]("id") / "remarks").in(formBody[RemarkForm])
      .errorOut(claimErrors).out(jsonBody[Claim])

  private val listClaims =
    endpoint.get.in("claims").out(jsonBody[List[Claim]])

  private def readReceipt(receipt: Part[TapirFile]): ZIO[Any, ClaimError, String] =
    ZIO.attemptBlocking(Files.readString(receipt.body.toPath, StandardCharsets.UTF_8)).orDie
      .filterOrFail(_.nonEmpty)(Rejected("the receipt must not be empty"))

  val serverEndpoints: List[ZServerEndpoint[ClaimRegistry, Any]] = List(
    fileClaim.zServerLogic[ClaimRegistry] { form =>
      for {
        receiptText <- readReceipt(form.receipt)
        claim <- ZIO.serviceWithZIO[ClaimRegistry](_.file(id =>
          Claim(
            id,
            form.claimant,
            form.nights,
            form.note,
            form.receipt.fileName,
            receiptText,
            form.photo.contentType,
            form.photo.body.length,
            List()
          )
        ))
      } yield claim
    },
    remarkClaim.zServerLogic[ClaimRegistry] { (id, form) =>
      ZIO.serviceWithZIO[ClaimRegistry](_.remark(id, Remark(form.author, form.text))).someOrFail(UnknownClaim(id))
    },
    listClaims.zServerLogic[ClaimRegistry](_ => ZIO.serviceWithZIO[ClaimRegistry](_.all))
  )
}
