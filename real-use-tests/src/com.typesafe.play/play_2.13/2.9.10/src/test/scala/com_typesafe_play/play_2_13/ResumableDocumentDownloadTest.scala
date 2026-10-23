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
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, DefaultActionBuilder, PlayBodyParsers, RangeResult, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, contentAsString, contentType, status, writeableOf_AnyContentAsEmpty}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play HTTP byte-range responses for resumable document downloads. */
@TestTimeout(60)
class ResumableDocumentDownloadTest {
  @Test
  def resumesAnInterruptedDownloadAndReportsRangesOutsideTheDocument(): Unit = {
    val system: ActorSystem = ActorSystem("resumable-document-download")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)
    val document: Path = Files.createTempFile("document-download-", ".txt")

    try {
      val contents: String = (0 until 20000).map(index => ('A' + index % 26).toChar).mkString
      Files.writeString(document, contents, UTF_8)
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val download: Action[AnyContent] = action { request =>
        RangeResult.ofPath(document, request.headers.get("Range"), "handbook.txt", Some("text/plain"))
      }

      def fetch(range: Option[String]): Future[Result] = {
        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/documents/handbook.txt")
        call(download, range.fold(request)(value => request.withHeaders("Range" -> value)))
      }

      val complete: Future[Result] = fetch(None)
      assertEquals(200, status(complete))
      assertEquals(Some("text/plain"), contentType(complete))
      val completeResult: Result = Await.result(complete, 10.seconds)
      assertEquals(Some("bytes"), completeResult.header.headers.get("Accept-Ranges"))
      assertEquals(Some("attachment; filename=\"handbook.txt\""),
        completeResult.header.headers.get("Content-Disposition"))
      assertEquals(Some(20000L), completeResult.body.contentLength)
      assertEquals(contents, contentAsString(complete))

      val firstPart: Future[Result] = fetch(Some("bytes=0-4095"))
      assertEquals(206, status(firstPart))
      val firstResult: Result = Await.result(firstPart, 10.seconds)
      assertEquals(Some("bytes 0-4095/20000"), firstResult.header.headers.get("Content-Range"))
      assertEquals(Some(4096L), firstResult.body.contentLength)
      val savedPrefix: String = contentAsString(firstPart)
      assertEquals(contents.take(4096), savedPrefix)

      val resumed: Future[Result] = fetch(Some("bytes=4096-"))
      assertEquals(206, status(resumed))
      val resumedResult: Result = Await.result(resumed, 10.seconds)
      assertEquals(Some("bytes 4096-19999/20000"), resumedResult.header.headers.get("Content-Range"))
      assertEquals(Some(15904L), resumedResult.body.contentLength)
      assertEquals(contents, savedPrefix + contentAsString(resumed))

      val tail: Future[Result] = fetch(Some("bytes=-128"))
      assertEquals(206, status(tail))
      assertEquals(Some("bytes 19872-19999/20000"),
        Await.result(tail, 10.seconds).header.headers.get("Content-Range"))
      assertEquals(contents.takeRight(128), contentAsString(tail))

      val staleOffset: Future[Result] = fetch(Some("bytes=25000-"))
      assertEquals(416, status(staleOffset))
      assertEquals(Some("bytes */20000"),
        Await.result(staleOffset, 10.seconds).header.headers.get("Content-Range"))
      assertEquals("", contentAsString(staleOffset))
    } finally {
      try {
        Await.result(system.terminate(), 10.seconds)
      } finally {
        Files.deleteIfExists(document)
      }
    }
  }
}
