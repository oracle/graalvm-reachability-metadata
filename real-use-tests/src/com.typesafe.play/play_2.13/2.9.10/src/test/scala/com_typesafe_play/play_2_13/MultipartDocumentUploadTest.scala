/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.Timeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout => TestTimeout}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.{Action, DefaultActionBuilder, MultipartFormData, PlayBodyParsers, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, contentAsJson, contentAsString, status}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play multipart file uploads for storing a document with accompanying catalog fields. */
@TestTimeout(60)
class MultipartDocumentUploadTest {
  @Test
  def storesTheUploadedDocumentAndRejectsSubmissionsWithoutAFile(): Unit = {
    val system: ActorSystem = ActorSystem("multipart-document-upload")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)
    val directory: Path = Files.createTempDirectory("document-upload-")
    val storedDocument: Path = directory.resolve("catalog.csv")

    try {
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val upload: Action[MultipartFormData[TemporaryFile]] = action(parsers.multipartFormData) { request =>
        request.body.file("document") match {
          case Some(document) =>
            try {
              document.ref.copyTo(storedDocument, replace = true)
              Results.Created(Json.obj(
                "filename" -> document.filename,
                "contentType" -> document.contentType,
                "size" -> document.fileSize,
                "collection" -> request.body.dataParts("collection").head,
                "tags" -> request.body.dataParts("tag")
              )).withHeaders("Location" -> "/documents/catalog")
            } finally {
              Files.deleteIfExists(document.ref.path)
            }
          case None => Results.BadRequest("A document file is required")
        }
      }

      val boundary: String = "catalog-upload-boundary"
      val document: String = "sku,quantity\nLAMP-1,12\nCHAIR-2,4\n"
      val fields: String =
        s"--$boundary\r\nContent-Disposition: form-data; name=\"collection\"\r\n\r\noffice\r\n" +
          s"--$boundary\r\nContent-Disposition: form-data; name=\"tag\"\r\n\r\nstock\r\n" +
          s"--$boundary\r\nContent-Disposition: form-data; name=\"tag\"\r\n\r\nquarterly\r\n"
      val filePart: String =
        s"--$boundary\r\n" +
          "Content-Disposition: form-data; name=\"document\"; filename=\"inventory.csv\"\r\n" +
          "Content-Type: text/csv\r\n\r\n" + document + "\r\n"
      val closing: String = s"--$boundary--\r\n"

      val accepted: Future[Result] = call(upload,
        FakeRequest("POST", "/documents")
          .withHeaders("Content-Type" -> s"multipart/form-data; boundary=$boundary")
          .withBody(fields + filePart + closing))
      assertEquals(201, status(accepted))
      assertEquals(Some("/documents/catalog"),
        Await.result(accepted, 10.seconds).header.headers.get("Location"))
      assertEquals(Json.obj(
        "filename" -> "inventory.csv",
        "contentType" -> "text/csv",
        "size" -> document.getBytes(UTF_8).length,
        "collection" -> "office",
        "tags" -> Json.arr("stock", "quarterly")
      ), contentAsJson(accepted))
      assertEquals(document, Files.readString(storedDocument, UTF_8))

      val missingFile: Future[Result] = call(upload,
        FakeRequest("POST", "/documents")
          .withHeaders("Content-Type" -> s"multipart/form-data; boundary=$boundary")
          .withBody(fields + closing))
      assertEquals(400, status(missingFile))
      assertEquals("A document file is required", contentAsString(missingFile))
      assertEquals(document, Files.readString(storedDocument, UTF_8))
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
