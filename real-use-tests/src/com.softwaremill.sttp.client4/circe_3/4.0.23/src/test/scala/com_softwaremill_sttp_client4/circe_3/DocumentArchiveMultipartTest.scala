/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.Printer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.model.MediaType
import sttp.model.StatusCode

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.regex.Pattern
import scala.concurrent.duration.*

/**
 * Exercises circe-serialized JSON parts inside multipart/form-data uploads: a document archive client
 * sends `asJson` metadata parts next to binary file parts, uses a custom `Printer` for one part, and
 * decodes the archive's JSON replies and validation errors with `asJsonEither`.
 */
class DocumentArchiveMultipartTest {
  private final case class DocumentMetadata(title: String, tags: List[String], description: Option[String])
  private final case class StoredDocument(
      id: String,
      title: String,
      tags: List[String],
      fileName: String,
      sizeBytes: Int,
      metadataContentType: String
  )
  private final case class BatchReceipt(stored: List[String], skipped: Int)
  private final case class ArchiveError(code: String, field: String)

  /** One decoded multipart section as the archive server sees it. */
  private final case class ReceivedPart(
      name: String,
      fileName: Option[String],
      contentType: Option[String],
      body: Array[Byte]
  ) {
    def text: String = new String(body, StandardCharsets.UTF_8)
  }

  private val receivedMetadata: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()

  private var server: HttpServer = null
  private var backend: SyncBackend = null
  private var baseUri: String = null

  @BeforeEach
  def startServer(): Unit = {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/documents",
      (exchange: HttpExchange) => {
        try {
          val parts: List[ReceivedPart] = parseMultipart(exchange)
          val metadataPart: ReceivedPart = parts.find(_.name == "metadata").get
          val filePart: ReceivedPart = parts.find(_.name == "content").get
          receivedMetadata.add(metadataPart.text)
          val metadata: DocumentMetadata = decode[DocumentMetadata](metadataPart.text).fold(error => throw error, identity)
          if (metadata.title.isBlank) {
            respond(exchange, 422, ArchiveError("BLANK", "title").asJson.noSpaces)
          } else {
            val stored: StoredDocument = StoredDocument(
              s"doc-${metadata.title.toLowerCase.replace(' ', '-')}",
              metadata.title,
              metadata.tags,
              filePart.fileName.getOrElse("unnamed"),
              filePart.body.length,
              metadataPart.contentType.getOrElse("none")
            )
            respond(exchange, 201, stored.asJson.noSpaces)
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/documents/batch",
      (exchange: HttpExchange) => {
        try {
          val items: List[DocumentMetadata] = parseMultipart(exchange)
            .filter(_.name == "item")
            .map(part => decode[DocumentMetadata](part.text).fold(error => throw error, identity))
          val (kept, skipped) = items.partition(_.tags.nonEmpty)
          respond(exchange, 200, BatchReceipt(kept.map(_.title), skipped.size).asJson.noSpaces)
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    baseUri = s"http://127.0.0.1:${server.getAddress.getPort}"
    backend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
  }

  @Test
  def uploadsJsonMetadataPartNextToBinaryFile(): Unit = {
    val scan: Array[Byte] = Array.tabulate[Byte](512)(i => (i % 251).toByte)
    val metadata: DocumentMetadata = DocumentMetadata("Lease Agreement", List("legal", "2026"), Some("Signed copy"))

    val response: Response[Either[ResponseException[ArchiveError], StoredDocument]] = basicRequest
      .post(uri"$baseUri/documents")
      .multipartBody(
        multipart("metadata", asJson(metadata)).contentType(MediaType.ApplicationJson),
        multipart("content", scan).fileName("lease.pdf").contentType(MediaType.ApplicationPdf)
      )
      .response(asJsonEither[ArchiveError, StoredDocument])
      .send(backend)

    assertThat(response.code).isEqualTo(StatusCode.Created)
    assertThat(response.body).isEqualTo(
      Right(StoredDocument("doc-lease-agreement", "Lease Agreement", List("legal", "2026"), "lease.pdf", 512, "application/json"))
    )
    assertThat(decode[DocumentMetadata](receivedMetadata.peek())).isEqualTo(Right(metadata))
  }

  @Test
  def dropsAbsentOptionalFieldsWithCustomPrinterPart(): Unit = {
    given Printer = Printer.noSpaces.copy(dropNullValues = true)
    val metadata: DocumentMetadata = DocumentMetadata("Invoice 42", List("finance"), None)

    val stored: StoredDocument = basicRequest
      .post(uri"$baseUri/documents")
      .multipartBody(
        multipart("metadata", asJson(metadata)),
        multipart("content", "total=42.00").fileName("invoice.txt")
      )
      .response(asJsonOrFail[StoredDocument])
      .send(backend)
      .body

    assertThat(stored.id).isEqualTo("doc-invoice-42")
    assertThat(stored.sizeBytes).isEqualTo("total=42.00".length)
    assertThat(receivedMetadata.peek()).isEqualTo("""{"title":"Invoice 42","tags":["finance"]}""")
  }

  @Test
  def sendsRepeatedJsonPartsInOneBatch(): Unit = {
    val batch: List[DocumentMetadata] = List(
      DocumentMetadata("Policy", List("hr"), None),
      DocumentMetadata("Draft", Nil, Some("work in progress")),
      DocumentMetadata("Handbook", List("hr", "onboarding"), Some("v3"))
    )

    val receipt: Either[ResponseException[String], BatchReceipt] = basicRequest
      .post(uri"$baseUri/documents/batch")
      .multipartBody(batch.map(item => multipart("item", asJson(item))))
      .response(asJson[BatchReceipt])
      .send(backend)
      .body

    assertThat(receipt).isEqualTo(Right(BatchReceipt(List("Policy", "Handbook"), 1)))
  }

  @Test
  def decodesValidationErrorForRejectedMetadataPart(): Unit = {
    val response: Response[Either[ResponseException[ArchiveError], StoredDocument]] = basicRequest
      .post(uri"$baseUri/documents")
      .multipartBody(
        multipart("metadata", asJson(DocumentMetadata(" ", List("misc"), None))),
        multipart("content", Array[Byte](1, 2, 3)).fileName("blank.bin")
      )
      .response(asJsonEither[ArchiveError, StoredDocument])
      .send(backend)

    assertThat(response.code).isEqualTo(StatusCode.UnprocessableEntity)
    response.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, metadata)) =>
        assertThat(error).isEqualTo(ArchiveError("BLANK", "title"))
        assertThat(metadata.code).isEqualTo(StatusCode.UnprocessableEntity)
      case other =>
        throw new AssertionError(s"Expected a decoded archive error, got $other")
    }
  }

  /** Splits a multipart/form-data request body into its named sections. */
  private def parseMultipart(exchange: HttpExchange): List[ReceivedPart] = {
    val contentType: String = exchange.getRequestHeaders.getFirst("Content-Type")
    assertThat(contentType).startsWith("multipart/form-data")
    val boundary: String = contentType.split(";").map(_.trim).find(_.startsWith("boundary=")).get
      .stripPrefix("boundary=")
      .stripPrefix("\"")
      .stripSuffix("\"")
    // ISO-8859-1 maps every byte to one char, so binary parts survive the split unchanged.
    val raw: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.ISO_8859_1)
    raw.split(Pattern.quote(s"--$boundary")).toList
      .drop(1)
      .takeWhile(!_.startsWith("--"))
      .map { section =>
        val content: String = section.stripPrefix("\r\n").stripSuffix("\r\n")
        val headerEnd: Int = content.indexOf("\r\n\r\n")
        val headers: Map[String, String] = content.substring(0, headerEnd).split("\r\n").toList.map { line =>
          val colon: Int = line.indexOf(':')
          line.substring(0, colon).trim.toLowerCase -> line.substring(colon + 1).trim
        }.toMap
        val disposition: Map[String, String] = headers("content-disposition").split(";").toList.drop(1).map { param =>
          val (key, value) = param.trim.span(_ != '=')
          key -> value.drop(1).stripPrefix("\"").stripSuffix("\"")
        }.toMap
        ReceivedPart(
          disposition("name"),
          disposition.get("filename"),
          headers.get("content-type").map(_.split(";").head.trim),
          content.substring(headerEnd + 4).getBytes(StandardCharsets.ISO_8859_1)
        )
      }
  }

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
