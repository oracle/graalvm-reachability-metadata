/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Codec
import io.circe.Json
import io.circe.parser.parse
import org.http4s.Header
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import sttp.model.Part
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises Circe JSON part codecs in multipart document uploads with structured metadata. */
class DocumentMultipartJsonTest extends TapirJsonCirce {
  case class Metadata(title: String, tags: List[String]) derives Codec.AsObject, Schema
  case class Upload(metadata: Metadata, document: Part[String])
  case class Receipt(title: String, tags: List[String], filename: String, content: String)
      derives Codec.AsObject, Schema

  @Test
  @Timeout(60)
  def acceptsJsonMetadataAlongsideDocumentAndRejectsInvalidParts(): Unit = {
    val saved: Ref[IO, List[String]] = Ref.of[IO, List[String]](Nil).unsafeRunSync()
    val upload = endpoint.post
      .in("documents")
      .in(multipartBody[Upload])
      .out(statusCode(sttp.model.StatusCode.Created))
      .out(jsonBody[Receipt])
      .serverLogicSuccess[IO] { form =>
        saved.update(_ :+ form.metadata.title).as(Receipt(
          form.metadata.title,
          form.metadata.tags,
          form.document.fileName.getOrElse("unnamed"),
          form.document.body
        ))
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(upload).orNotFound
    val boundary: String = "document-upload-boundary"
    val document: String = "First line.\nSecond line."

    def submit(metadata: Option[String]): (Status, Option[MediaType], String) = {
      val metadataPart: String = metadata.map { value =>
        s"--$boundary\r\n" +
          "Content-Disposition: form-data; name=\"metadata\"\r\n" +
          "Content-Type: application/json\r\n\r\n" + value + "\r\n"
      }.getOrElse("")
      val body: String = metadataPart +
        s"--$boundary\r\n" +
        "Content-Disposition: form-data; name=\"document\"; filename=\"minutes.txt\"\r\n" +
        "Content-Type: text/plain; charset=UTF-8\r\n\r\n" + document + "\r\n" +
        s"--$boundary--\r\n"
      val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/documents"))
        .withEntity(body)
        .putHeaders(Header.Raw(CIString("Content-Type"), s"multipart/form-data; boundary=$boundary"))
      (for {
        response <- app.run(request)
        text <- response.bodyText.compile.string
      } yield (response.status, response.contentType.map(_.mediaType), text))
        .timeout(15.seconds).unsafeRunSync()
    }

    val accepted: (Status, Option[MediaType], String) = submit(Some(
      """{"title":"Board minutes","tags":["internal","approved"]}"""
    ))
    val expected: Json = parse("""{
      "title":"Board minutes",
      "tags":["internal","approved"],
      "filename":"minutes.txt",
      "content":"First line.\nSecond line."
    }""").fold(throw _, identity)
    assertEquals(Status.Created, accepted._1)
    assertEquals(Some(MediaType.application.json), accepted._2)
    assertEquals(expected, parse(accepted._3).fold(throw _, identity))

    assertEquals(Status.BadRequest, submit(Some("""{"title":17,"tags":false}"""))._1)
    assertEquals(Status.BadRequest, submit(Some("{broken-json"))._1)
    assertEquals(Status.BadRequest, submit(None)._1)
    assertEquals(List("Board minutes"), saved.get.unsafeRunSync())
  }
}
