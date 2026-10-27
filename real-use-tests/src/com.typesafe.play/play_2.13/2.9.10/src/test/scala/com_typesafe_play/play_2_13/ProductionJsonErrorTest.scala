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
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout => TestTimeout}
import play.api.{Environment, Mode}
import play.api.http.JsonHttpErrorHandler
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, DefaultActionBuilder, PlayBodyParsers, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, contentAsJson, contentAsString, contentType, status, writeableOf_AnyContentAsEmpty}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

/** Exercises Play production JSON error handling for a file-backed invoice download endpoint. */
@TestTimeout(60)
class ProductionJsonErrorTest {
  @Test
  def returnsActionableClientErrorsWithoutExposingStorageFailures(): Unit = {
    val system: ActorSystem = ActorSystem("production-json-error")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)
    val directory: Path = Files.createTempDirectory("private-invoices-")
    val invoice: Path = directory.resolve("INV-42.txt")

    try {
      Files.writeString(invoice, "Invoice INV-42: 7500 cents", UTF_8)
      val errors: JsonHttpErrorHandler = new JsonHttpErrorHandler(Environment.simple(mode = Mode.Prod))
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val download: Action[AnyContent] = action.async { request =>
        request.getQueryString("id").filter(_.matches("INV-[0-9]+")) match {
          case None => errors.onClientError(request, 400, "An invoice id such as INV-42 is required")
          case Some(id) =>
            Future {
              Results.Ok(Files.readString(directory.resolve(s"$id.txt"), UTF_8))
                .withHeaders("Content-Disposition" -> s"attachment; filename=\"$id.txt\"")
            }.recoverWith {
              case NonFatal(failure) => errors.onServerError(request, failure)
            }
        }
      }

      val invalidRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/invoices/download?id=invalid")
      val invalid: Future[Result] = call(download, invalidRequest)
      assertEquals(400, status(invalid))
      assertEquals(Some("application/json"), contentType(invalid))
      assertEquals(Json.obj("error" -> Json.obj(
        "requestId" -> invalidRequest.id,
        "message" -> "An invoice id such as INV-42 is required"
      )), contentAsJson(invalid))

      val successful: Future[Result] = call(download,
        FakeRequest("GET", "/invoices/download?id=INV-42"))
      assertEquals(200, status(successful))
      assertEquals("Invoice INV-42: 7500 cents", contentAsString(successful))
      assertEquals(Some("attachment; filename=\"INV-42.txt\""),
        Await.result(successful, 10.seconds).header.headers.get("Content-Disposition"))

      val failed: Future[Result] = call(download,
        FakeRequest("GET", "/invoices/download?id=INV-99"))
      assertEquals(500, status(failed))
      assertEquals(Some("application/json"), contentType(failed))
      val error: JsObject = (contentAsJson(failed) \ "error").as[JsObject]
      assertEquals(Set("id"), error.keys)
      assertTrue((error \ "id").as[String].nonEmpty, "The customer needs an error reference for support")
      val body: String = contentAsString(failed)
      assertFalse(body.contains(directory.toString))
      assertFalse(body.contains("INV-99.txt"))
      assertFalse(body.contains("NoSuchFileException"))

      val recovered: Future[Result] = call(download,
        FakeRequest("GET", "/invoices/download?id=INV-42"))
      assertEquals(200, status(recovered))
      assertEquals("Invoice INV-42: 7500 cents", contentAsString(recovered))
    } finally {
      try {
        Files.deleteIfExists(invoice)
        Files.deleteIfExists(directory)
      } finally {
        Await.result(system.terminate(), 10.seconds)
      }
    }
  }
}
