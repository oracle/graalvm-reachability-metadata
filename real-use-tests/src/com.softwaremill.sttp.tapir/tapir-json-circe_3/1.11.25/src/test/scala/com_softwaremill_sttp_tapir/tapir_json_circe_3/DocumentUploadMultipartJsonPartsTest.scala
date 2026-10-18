/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client3.HttpClientSyncBackend
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.client3.multipart
import sttp.model.MediaType
import sttp.model.Part
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * JSON parts inside multipart form bodies: circe codecs encoding and decoding individual parts of a
 * `multipartBody` next to a binary file part, on a live JDK HTTP server.
 *
 * A document archive takes uploads as `multipart/form-data`: the file travels as a binary stream
 * part, the document metadata as one JSON part, and each reviewer as a repeated JSON part. The
 * client built from the endpoint definition and a hand-written multipart request both reach the
 * server; a JSON part that does not decode is rejected before the archive stores anything.
 */
class DocumentUploadMultipartJsonPartsTest {
  import DocumentUploadMultipartJsonPartsTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val archive: Archive = new Archive

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(archive.serverEndpoints)
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
  def uploadsADocumentWithJsonMetadataPartsThroughTheClient(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val content: Array[Byte] = "Quarterly figures: 42".getBytes(StandardCharsets.UTF_8)
    val form: UploadForm = UploadForm(
      file = Part("file", new ByteArrayInputStream(content), fileName = Some("q3-report.txt")),
      metadata = DocumentMetadata("Q3 report", List("finance", "quarterly"), confidential = true),
      reviewers = List(Reviewer("Ada", "ada@example.com"), Reviewer("Linus", "linus@example.com"))
    )

    val stored: Either[UploadRejected, StoredDocument] = client
      .toRequestThrowDecodeFailures(uploadDocument, Some(baseUri))
      .apply(form)
      .send(backend)
      .body

    assertEquals(
      Right(StoredDocument(1L, "Q3 report", content.length.toLong, List("ada@example.com", "linus@example.com"))),
      stored
    )
    val received: ReceivedUpload = archive.received(1L)
    assertArrayEquals(content, received.content)
    assertEquals(Some(MediaType.ApplicationJson.toString), received.metadataContentType.map(_.takeWhile(_ != ';')))
    assertEquals(form.metadata, received.metadata)
    assertEquals(form.reviewers, received.reviewers)
  }

  @Test
  def acceptsAHandWrittenMultipartRequestWithJsonParts(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/documents")
      .multipartBody(
        multipart("file", "draft".getBytes(StandardCharsets.UTF_8)).fileName("notes.md"),
        multipart("metadata", """{"title":"Meeting notes","tags":[],"confidential":false}""")
          .contentType(MediaType.ApplicationJson),
        multipart("reviewers", """{"name":"Grace","email":"grace@example.com"}""")
          .contentType(MediaType.ApplicationJson),
        multipart("reviewers", """{"name":"Alan","email":"alan@example.com"}""")
          .contentType(MediaType.ApplicationJson)
      )
      .send(backend)

    assertEquals(StatusCode.Created, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    assertEquals(
      """{"id":1,"title":"Meeting notes","sizeBytes":5,"reviewerEmails":["grace@example.com","alan@example.com"]}""",
      raw.body.fold(identity, identity)
    )
    assertEquals(DocumentMetadata("Meeting notes", Nil, confidential = false), archive.received(1L).metadata)
  }

  @Test
  def rejectsAMetadataPartThatDoesNotDecode(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/documents")
      .multipartBody(
        multipart("file", "x".getBytes(StandardCharsets.UTF_8)).fileName("x.txt"),
        multipart("metadata", """{"title":"Missing flags"}""").contentType(MediaType.ApplicationJson),
        multipart("reviewers", """{"name":"Grace","email":"grace@example.com"}""")
          .contentType(MediaType.ApplicationJson)
      )
      .send(backend)

    assertEquals(StatusCode.BadRequest, raw.code)
    assertEquals(0, archive.size)
  }

  @Test
  def reportsABusinessRuleViolationAsAJsonError(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val form: UploadForm = UploadForm(
      file = Part("file", new ByteArrayInputStream("secret".getBytes(StandardCharsets.UTF_8)), fileName = Some("secret.txt")),
      metadata = DocumentMetadata("Board minutes", List("board"), confidential = true),
      reviewers = Nil
    )

    val response: Response[Either[UploadRejected, StoredDocument]] = client
      .toRequestThrowDecodeFailures(uploadDocument, Some(baseUri))
      .apply(form)
      .send(backend)

    assertEquals(StatusCode.UnprocessableEntity, response.code)
    assertEquals(Left(UploadRejected("confidential documents need at least one reviewer")), response.body)
    assertEquals(0, archive.size)
  }
}

object DocumentUploadMultipartJsonPartsTest {

  final case class DocumentMetadata(title: String, tags: List[String], confidential: Boolean)
      derives CirceCodec.AsObject, Schema

  final case class Reviewer(name: String, email: String) derives CirceCodec.AsObject, Schema

  /**
   * The upload form: a binary stream part, one JSON part for the metadata and one repeated JSON
   * part per reviewer. The file is a stream because circe also encodes byte arrays as JSON.
   */
  final case class UploadForm(file: Part[InputStream], metadata: DocumentMetadata, reviewers: List[Reviewer])
      derives Schema

  final case class StoredDocument(
      id: Long,
      title: String,
      sizeBytes: Long,
      reviewerEmails: List[String]
  ) derives CirceCodec.AsObject, Schema

  final case class UploadRejected(reason: String) derives CirceCodec.AsObject, Schema

  /** What the archive saw of an accepted upload, including the metadata part's content type. */
  final case class ReceivedUpload(
      content: Array[Byte],
      metadataContentType: Option[String],
      metadata: DocumentMetadata,
      reviewers: List[Reviewer]
  )

  /** The same form as the server reads it, keeping the metadata part to inspect its headers. */
  final case class ReceivedForm(file: Part[InputStream], metadata: Part[DocumentMetadata], reviewers: List[Reviewer])
      derives Schema

  val uploadDocument: PublicEndpoint[UploadForm, UploadRejected, StoredDocument, Any] = endpoint.post
    .in("documents")
    .in(multipartBody[UploadForm])
    .out(statusCode(StatusCode.Created).and(jsonBody[StoredDocument]))
    .errorOut(statusCode(StatusCode.UnprocessableEntity).and(jsonBody[UploadRejected]))

  val receiveDocument: PublicEndpoint[ReceivedForm, UploadRejected, StoredDocument, Any] = endpoint.post
    .in("documents")
    .in(multipartBody[ReceivedForm])
    .out(statusCode(StatusCode.Created).and(jsonBody[StoredDocument]))
    .errorOut(statusCode(StatusCode.UnprocessableEntity).and(jsonBody[UploadRejected]))

  /** In-memory document archive behind the upload endpoint. */
  final class Archive {
    private val ids: AtomicLong = new AtomicLong
    private val uploads: ConcurrentHashMap[Long, ReceivedUpload] = new ConcurrentHashMap

    def received(id: Long): ReceivedUpload = uploads.get(id)

    def size: Int = uploads.size

    private def store(form: ReceivedForm): Either[UploadRejected, StoredDocument] = {
      val metadata: DocumentMetadata = form.metadata.body
      if (metadata.confidential && form.reviewers.isEmpty)
        Left(UploadRejected("confidential documents need at least one reviewer"))
      else {
        val content: Array[Byte] = form.file.body.readAllBytes()
        val id: Long = ids.incrementAndGet()
        uploads.put(id, ReceivedUpload(content, form.metadata.contentType, metadata, form.reviewers))
        Right(StoredDocument(id, metadata.title, content.length.toLong, form.reviewers.map(_.email)))
      }
    }

    val serverEndpoints = List(receiveDocument.handle(store))
  }
}
