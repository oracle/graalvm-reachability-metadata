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
import play.api.libs.json.Json
import play.api.libs.typedmap.TypedKey
import play.api.mvc.{Action, AnyContent, DefaultActionBuilder, EssentialAction, Filter, PlayBodyParsers, RequestHeader, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, status}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/** Exercises Play HTTP filters for request correlation and access auditing across catalog responses. */
@TestTimeout(60)
class CorrelatedAccessFilterTest {
  case class AccessRecord(correlationId: String, path: String, status: Int)

  class CorrelatedAccessFilter(
      correlationKey: TypedKey[String],
      journal: ConcurrentLinkedQueue[AccessRecord]
  )(implicit override val mat: Materializer, executionContext: ExecutionContext) extends Filter {
    override def apply(next: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
      val correlationId: String = request.headers.get("X-Request-Id")
        .filter(_.matches("[A-Za-z0-9_-]{1,64}"))
        .getOrElse(s"server-${request.id}")
      next(request.addAttr(correlationKey, correlationId)).map { result =>
        journal.add(AccessRecord(correlationId, request.path, result.header.status))
        result.withHeaders("X-Request-Id" -> correlationId)
      }
    }
  }

  @Test
  def correlatesSuccessfulAndMissingProductsWithoutTrustingInvalidClientIds(): Unit = {
    val system: ActorSystem = ActorSystem("correlated-access-filter")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)

    try {
      val correlationKey: TypedKey[String] = TypedKey[String]("catalog-correlation-id")
      val journal: ConcurrentLinkedQueue[AccessRecord] = new ConcurrentLinkedQueue[AccessRecord]()
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val catalog: Action[AnyContent] = action { request =>
        val correlationId: String = request.attrs(correlationKey)
        request.getQueryString("sku") match {
          case Some("desk-lamp") => Results.Ok(Json.obj(
            "sku" -> "desk-lamp", "available" -> 12, "requestId" -> correlationId
          ))
          case _ => Results.NotFound(Json.obj(
            "error" -> "Product not found", "requestId" -> correlationId
          ))
        }
      }
      val accessFilter: CorrelatedAccessFilter = new CorrelatedAccessFilter(correlationKey, journal)
      val filtered: EssentialAction = accessFilter(catalog)

      val found: Future[Result] = filtered(
        FakeRequest("GET", "/catalog?sku=desk-lamp").withHeaders("X-Request-Id" -> "checkout-42")
      ).run()
      assertEquals(200, status(found))
      assertEquals(Json.obj("sku" -> "desk-lamp", "available" -> 12, "requestId" -> "checkout-42"),
        contentAsJson(found))
      assertEquals(Some("checkout-42"),
        Await.result(found, 10.seconds).header.headers.get("X-Request-Id"))

      val missingRequest: RequestHeader = FakeRequest("GET", "/catalog?sku=unknown")
      val missingId: String = s"server-${missingRequest.id}"
      val missing: Future[Result] = filtered(missingRequest).run()
      assertEquals(404, status(missing))
      assertEquals(Json.obj("error" -> "Product not found", "requestId" -> missingId),
        contentAsJson(missing))
      assertEquals(Some(missingId), Await.result(missing, 10.seconds).header.headers.get("X-Request-Id"))

      val invalidRequest: RequestHeader = FakeRequest("GET", "/catalog?sku=desk-lamp")
        .withHeaders("X-Request-Id" -> "untrusted request id")
      val replacementId: String = s"server-${invalidRequest.id}"
      val sanitized: Future[Result] = filtered(invalidRequest).run()
      assertEquals(200, status(sanitized))
      assertEquals(Json.obj("sku" -> "desk-lamp", "available" -> 12, "requestId" -> replacementId),
        contentAsJson(sanitized))
      assertEquals(Some(replacementId),
        Await.result(sanitized, 10.seconds).header.headers.get("X-Request-Id"))

      assertEquals(Seq(
        AccessRecord("checkout-42", "/catalog", 200),
        AccessRecord(missingId, "/catalog", 404),
        AccessRecord(replacementId, "/catalog", 200)
      ), journal.iterator().asScala.toSeq)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
