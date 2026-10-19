/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.{BAD_REQUEST, CREATED}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, DefaultActionBuilder, MultipartFormData, PlayBodyParsers, RequestHeader, Result, Results}
import play.api.test.FakeRequest

import java.nio.file.{Files, Path}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/** Exercises Play multipart form parsing for a document upload with an attachment and descriptive fields. */
@Timeout(60)
class MultipartDocumentUploadTest {
  @Test
  def storesUploadedDocumentAndRejectsMissingAttachments(): Unit = {
    val system: ActorSystem = ActorSystem("multipart-document-upload")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    val directory: Path = Files.createTempDirectory("document-upload-")
    val storedDocument: Path = directory.resolve("attachment.bin")
    try {
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val upload: Action[MultipartFormData[TemporaryFile]] = action(parsers.multipartFormData) { request =>
        request.body.file("attachment") match {
          case Some(file) =>
            // Store under an application-owned name, not a path supplied by the client.
            file.ref.moveTo(storedDocument, replace = false)
            val description: Option[String] = request.body.dataParts.get("description").flatMap(_.headOption)
            val tags: Seq[String] = request.body.dataParts.getOrElse("tag", Seq.empty[String])
            Results.Created(Json.obj(
              "filename" -> file.filename,
              "contentType" -> file.contentType,
              "size" -> file.fileSize,
              "description" -> description,
              "tags" -> tags
            ))
          case None => Results.BadRequest("An attachment is required")
        }
      }

      val boundary: String = "DocumentUploadBoundary"
      def field(name: String, value: String): ByteString = ByteString(
        s"--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
      )
      val closing: ByteString = ByteString(s"--$boundary--\r\n")
      def submit(body: ByteString): Result = {
        val request: RequestHeader = FakeRequest("POST", "/documents")
          .withHeaders(CONTENT_TYPE -> s"multipart/form-data; boundary=$boundary")
        // Network chunks deliberately split both multipart headers and boundary delimiters.
        val chunks: Source[ByteString, _] = Source(body.grouped(7).toList)
        Await.result(upload(request).run(chunks), 20.seconds)
      }

      val missing: Result = submit(field("description", "No attachment") ++ closing)
      assertEquals(BAD_REQUEST, missing.header.status)
      assertEquals("An attachment is required", Await.result(missing.body.consumeData, 10.seconds).utf8String)
      assertFalse(Files.exists(storedDocument))

      val contents: ByteString = ByteString(Array[Byte](0, 1, 2, 13, 10, 42, 127, -1)) ++
        ByteString("Document body\r\nwith multiple lines\r\n")
      val fileHeader: ByteString = ByteString(
        s"--$boundary\r\n" +
          "Content-Disposition: form-data; name=\"attachment\"; filename=\"invoice.bin\"\r\n" +
          "Content-Type: application/octet-stream\r\n\r\n"
      )
      val body: ByteString = field("description", "March invoice") ++
        field("tag", "finance") ++ field("tag", "approved") ++
        fileHeader ++ contents ++ ByteString("\r\n") ++ closing
      val accepted: Result = submit(body)
      assertEquals(CREATED, accepted.header.status)
      val receipt: JsValue = Json.parse(Await.result(accepted.body.consumeData, 10.seconds).utf8String)
      assertEquals(Json.obj(
        "filename" -> "invoice.bin",
        "contentType" -> "application/octet-stream",
        "size" -> contents.length,
        "description" -> "March invoice",
        "tags" -> Json.arr("finance", "approved")
      ), receipt)
      assertArrayEquals(contents.toArray, Files.readAllBytes(storedDocument))
    } finally {
      try {
        Files.deleteIfExists(storedDocument)
        Files.deleteIfExists(directory)
      } finally {
        Await.result(system.terminate(), 10.seconds)
      }
    }
  }
}
