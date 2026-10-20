/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import java.nio.charset.StandardCharsets

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.{CodecFormat, Schema}
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.stream.{ZPipeline, ZStream}
import zio.{Chunk, Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Binary streaming request and response bodies (`streamBinaryBody(ZioStreams)`) handled by
 * `zServerLogic`.
 *
 * A bookkeeping service imports ledger entries uploaded as a raw CSV byte stream and exports a
 * ledger back as a byte stream with a download header. The import logic decodes the incoming
 * `ZStream` with ZIO pipelines and stores the parsed rows in a `Ledgers` service from the ZIO
 * environment; the export logic turns the stored rows into a `ZStream` of bytes. The endpoints
 * run on the ZIO HTTP interpreter and are called by a streaming sttp client that uploads the CSV
 * in small chunks that split lines and multi-byte characters. The test checks the import
 * summaries, that a malformed row fails the whole upload with a typed 400 naming the line, that
 * the export reproduces every stored row with its attachment header, and that exporting an
 * unknown ledger fails with a typed 404.
 */
class LedgerTransferZioBinaryStreamTest {
  import LedgerTransferZioBinaryStreamTest.*

  @Test
  def ledgerEntriesAreUploadedAndDownloadedAsByteStreams(): Unit = {
    val program: ZIO[Server & Ledgers, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program.provide(Server.defaultWithPort(0), Ledgers.layer)).getOrThrowFiberFailure()
    }

    assertEquals(StatusCode.Ok, observed.firstImport.code)
    assertEquals(ImportSummary("household", 3, -4250L), decode[ImportSummary](observed.firstImport.body))

    assertEquals(StatusCode.Ok, observed.secondImport.code)
    assertEquals(ImportSummary("household", 5, 120000L), decode[ImportSummary](observed.secondImport.body))

    assertEquals(StatusCode.BadRequest, observed.malformedImport.code)
    assertEquals(
      InvalidRow(2, "amount 'twelve' is not a whole number of cents"),
      decode[InvalidRow](observed.malformedImport.body)
    )

    assertEquals(StatusCode.Ok, observed.bulkImport.code)
    assertEquals(ImportSummary("bulk", 1500, (1L to 1500L).sum), decode[ImportSummary](observed.bulkImport.body))

    assertEquals(StatusCode.Ok, observed.exported.code)
    assertEquals(Some("attachment; filename=\"household.csv\""), observed.exported.disposition)
    assertTrue(
      observed.exported.contentType.exists(_.startsWith("application/octet-stream")),
      s"expected a binary download, got ${observed.exported.contentType}"
    )
    assertEquals(
      List(
        "account,amount_cents",
        "groceries,-5400",
        "caf\u00e9,-350",
        "refund,1500",
        "salary,130000",
        "rent,-5750"
      ),
      observed.exported.text.split("\n").toList
    )

    assertEquals(StatusCode.Ok, observed.bulkExport.code)
    assertEquals(1501, observed.bulkExport.text.split("\n").length)
    assertTrue(observed.bulkExport.text.endsWith("acct-1500,1500\n"))

    assertEquals(StatusCode.NotFound, observed.unknownExport.code)
    assertEquals(LedgerNotFound("pension"), decode[LedgerNotFound](observed.unknownExport.text))
  }

  private def exercise(port: Int, backend: StreamBackend[Task, ZioStreams]): Task[Observed] = {
    val base: String = s"http://localhost:$port"

    def upload(ledger: String, csv: String, chunkSize: Int): Task[Reply] = {
      val bytes: ZStream[Any, Throwable, Byte] =
        ZStream.fromChunk(Chunk.fromArray(csv.getBytes(StandardCharsets.UTF_8))).rechunk(chunkSize)
      basicRequest.post(uri"$base/ledgers/$ledger/entries")
        .contentType("application/octet-stream")
        .streamBody(ZioStreams)(bytes)
        .send(backend)
        .map(r => Reply(r.code, r.body.fold(identity, identity)))
    }

    def download(ledger: String): Task[Download] =
      basicRequest.get(uri"$base/ledgers/$ledger/export")
        .response(asStreamUnsafe(ZioStreams))
        .send(backend)
        .flatMap { r =>
          val text: Task[String] = r.body match {
            case Right(bytes) => bytes.via(ZPipeline.utf8Decode).runCollect.map(_.mkString)
            case Left(error) => ZIO.succeed(error)
          }
          text.map(t => Download(r.code, r.header(HeaderNames.ContentType), r.header(HeaderNames.ContentDisposition), t))
        }

    val bulkCsv: String = (1 to 1500).map(i => s"acct-$i,$i").mkString("", "\n", "\n")
    for {
      firstImport <- upload("household", "groceries,-5400\ncaf\u00e9,-350\nrefund,1500\n", 3)
      secondImport <- upload("household", "salary,130000\r\nrent,-5750", 4)
      malformedImport <- upload("household", "fuel,-6000\ngift,twelve\n", 5)
      bulkImport <- upload("bulk", bulkCsv, 64)
      exported <- download("household")
      bulkExport <- download("bulk")
      unknownExport <- download("pension")
    } yield Observed(firstImport, secondImport, malformedImport, bulkImport, exported, bulkExport, unknownExport)
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object LedgerTransferZioBinaryStreamTest {
  final case class Entry(account: String, amountCents: Long)
  final case class ImportSummary(ledger: String, rows: Int, balanceCents: Long) derives Schema

  sealed trait LedgerError
  final case class InvalidRow(line: Int, reason: String) extends LedgerError derives Schema
  final case class LedgerNotFound(ledger: String) extends LedgerError derives Schema

  given JsonCodec[ImportSummary] = DeriveJsonCodec.gen[ImportSummary]
  given JsonCodec[InvalidRow] = DeriveJsonCodec.gen[InvalidRow]
  given JsonCodec[LedgerNotFound] = DeriveJsonCodec.gen[LedgerNotFound]

  final case class Reply(code: StatusCode, body: String)

  final case class Download(code: StatusCode, contentType: Option[String], disposition: Option[String], text: String)

  final case class Observed(
      firstImport: Reply,
      secondImport: Reply,
      malformedImport: Reply,
      bulkImport: Reply,
      exported: Download,
      bulkExport: Download,
      unknownExport: Download
  )

  /** Stores the entries of each ledger; required by both streaming endpoints. */
  trait Ledgers {
    def append(ledger: String, entries: Chunk[Entry]): UIO[Chunk[Entry]]
    def find(ledger: String): UIO[Option[Chunk[Entry]]]
  }

  object Ledgers {
    val layer: ZLayer[Any, Nothing, Ledgers] = ZLayer.fromZIO(Ref.make(Map.empty[String, Chunk[Entry]]).map { store =>
      new Ledgers {
        def append(ledger: String, entries: Chunk[Entry]): UIO[Chunk[Entry]] =
          store.modify { all =>
            val updated: Chunk[Entry] = all.getOrElse(ledger, Chunk.empty) ++ entries
            (updated, all.updated(ledger, updated))
          }

        def find(ledger: String): UIO[Option[Chunk[Entry]]] = store.get.map(_.get(ledger))
      }
    })
  }

  private val ledgerErrors = oneOf[LedgerError](
    oneOfVariant(StatusCode.BadRequest, jsonBody[InvalidRow]),
    oneOfVariant(StatusCode.NotFound, jsonBody[LedgerNotFound])
  )

  private val importEntries =
    endpoint.post
      .in("ledgers" / path[String]("ledger") / "entries")
      .in(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))
      .errorOut(ledgerErrors)
      .out(jsonBody[ImportSummary])

  private val exportEntries =
    endpoint.get
      .in("ledgers" / path[String]("ledger") / "export")
      .errorOut(ledgerErrors)
      .out(header[String](HeaderNames.ContentDisposition))
      .out(streamBinaryBody(ZioStreams)(CodecFormat.OctetStream()))

  private def parse(line: String, number: Int): Either[InvalidRow, Entry] =
    line.split(',').toList match {
      case account :: amount :: Nil =>
        amount.trim.toLongOption.toRight(InvalidRow(number, s"amount '${amount.trim}' is not a whole number of cents"))
          .map(cents => Entry(account.trim, cents))
      case _ => Left(InvalidRow(number, "expected 'account,amount'"))
    }

  private def importLedger(ledger: String, body: ZStream[Any, Throwable, Byte]): ZIO[Ledgers, LedgerError, ImportSummary] =
    for {
      lines <- body.via(ZPipeline.utf8Decode >>> ZPipeline.splitLines).filter(_.trim.nonEmpty).runCollect.orDie
      entries <- ZIO.foreach(lines.zipWithIndex) { case (line, index) => ZIO.fromEither(parse(line, index + 1)) }
      stored <- ZIO.serviceWithZIO[Ledgers](_.append(ledger, entries))
    } yield ImportSummary(ledger, stored.size, stored.map(_.amountCents).sum)

  private def exportLedger(ledger: String): ZIO[Ledgers, LedgerError, (String, ZStream[Any, Throwable, Byte])] =
    ZIO.serviceWithZIO[Ledgers](_.find(ledger)).someOrFail(LedgerNotFound(ledger)).map { entries =>
      val rows: ZStream[Any, Nothing, String] =
        ZStream.succeed("account,amount_cents\n") ++ ZStream.fromChunk(entries).map(e => s"${e.account},${e.amountCents}\n")
      (s"attachment; filename=\"$ledger.csv\"", rows.via(ZPipeline.utf8Encode))
    }

  val serverEndpoints: List[ZServerEndpoint[Ledgers, ZioStreams]] = List(
    importEntries.zServerLogic[Ledgers] { case (ledger, body) => importLedger(ledger, body) },
    exportEntries.zServerLogic[Ledgers](exportLedger)
  )
}
