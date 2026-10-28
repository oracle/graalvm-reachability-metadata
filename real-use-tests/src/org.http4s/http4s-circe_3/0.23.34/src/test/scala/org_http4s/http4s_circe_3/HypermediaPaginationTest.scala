/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.decodeUri
import org.http4s.circe.encodeUri
import org.http4s.circe.jsonEncoderOf
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises URI JSON codecs for a hypermedia client following catalog pagination links. */
class HypermediaPaginationTest {
  private final case class Document(name: String, download: Uri)
  private final case class Page(documents: List[Document], next: Option[Uri])

  private given Encoder[Document] = deriveEncoder[Document]
  private given Decoder[Document] = deriveDecoder[Document]
  private given Encoder[Page] = deriveEncoder[Page]
  private given Decoder[Page] = deriveDecoder[Page]
  private given EntityEncoder[IO, Page] = jsonEncoderOf[IO, Page]
  private given EntityDecoder[IO, Page] = jsonOf[IO, Page]
  private given EntityDecoder[IO, Json] = jsonOf[IO, Json]

  @Test
  @Timeout(60)
  def followServerLinksWithoutRebuildingOrLosingEscapedQueries(): Unit = {
    val firstUri: Uri = Uri.unsafeFromString("/documents")
    val nextUri: Uri = Uri.unsafeFromString("/documents?cursor=release%2F2026%2Bdraft&limit=1")
    val guideUri: Uri = Uri.unsafeFromString(
      "https://files.example.test/manuals/quick%20start.pdf?download=true#page=2"
    )
    val notesUri: Uri = Uri.unsafeFromString("/downloads/release%20notes.txt?language=en&format=text")
    val first: Page = Page(List(Document("Quick start", guideUri)), Some(nextUri))
    val last: Page = Page(List(Document("Release notes", notesUri)), None)

    val result: IO[Unit] = for {
      visited <- Ref.of[IO, List[Uri]](Nil)
      routes = HttpRoutes.of[IO] {
        case request @ GET -> Root / "documents" =>
          visited.update(_ :+ request.uri) *> (request.uri match {
            case uri if uri == firstUri => Ok(first)
            case uri if uri == nextUri => Ok(last)
            case _ => BadRequest("Unknown pagination cursor")
          })
      }
      client = Client.fromHttpApp(routes.orNotFound)
      pageOne <- client.expect[Page](Request[IO](uri = firstUri))
      link <- IO.fromOption(pageOne.next)(new IllegalStateException("Missing next-page link"))
      pageTwo <- client.expect[Page](Request[IO](uri = link))
      requests <- visited.get
      // Inspect the public JSON representation as well as consuming its typed links.
      document <- client.expect[Json](Request[IO](uri = firstUri))
      wireNext <- IO.fromEither(document.hcursor.get[String]("next"))
      wireDownload <- IO.fromEither(
        document.hcursor.downField("documents").downArray.get[String]("download")
      )
      _ <- IO {
        assertEquals(first, pageOne)
        assertEquals(last, pageTwo)
        assertEquals(None, pageTwo.next)
        assertEquals(List(firstUri, nextUri), requests)
        assertEquals(List("Quick start", "Release notes"),
          (pageOne.documents ++ pageTwo.documents).map(_.name))
        assertEquals(nextUri.renderString, wireNext)
        assertEquals(guideUri.renderString, wireDownload)
        assertEquals(notesUri, pageTwo.documents.head.download)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }
}
