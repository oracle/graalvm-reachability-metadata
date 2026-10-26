/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.IOLocal
import cats.effect.syntax.all._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises IOLocal request context propagation and isolation for concurrent request audit logs. */
class RequestContextIsolationTest {
  private case class RequestContext(requestId: String, span: String)
  private case class RequestLog(events: List[String], afterRequest: String)

  private def audit(context: IOLocal[RequestContext], event: String): IO[String] = {
    context.get.map(current => s"${current.requestId}/${current.span}:$event")
  }

  private def handle(
      requestId: String,
      context: IOLocal[RequestContext],
      ready: Deferred[IO, Unit],
      otherReady: Deferred[IO, Unit]
  ): IO[RequestLog] = {
    // Request middleware restores the caller's context even if processing fails.
    val scoped: IO[List[String]] = context.getAndSet(RequestContext(requestId, "request")).bracket {
      _ => for {
        _ <- ready.complete(())
        // Both request contexts must be installed before either handler emits its audit log.
        _ <- otherReady.get
        accepted <- audit(context, "accepted")
        receipt <- (for {
          inherited <- audit(context, "receipt-started")
          _ <- context.update(_.copy(span = "receipt"))
          queued <- audit(context, "receipt-queued")
        } yield List(inherited, queued)).start
        receiptEvents <- receipt.joinWithNever
        // A child span must not overwrite the request span in the parent fiber.
        completed <- audit(context, "completed")
      } yield List(accepted) ++ receiptEvents ++ List(completed)
    }(saved => context.set(saved))

    for {
      events <- scoped
      afterRequest <- audit(context, "idle")
    } yield RequestLog(events, afterRequest)
  }

  @Test
  @Timeout(30)
  def concurrentRequestsKeepTheirOwnCorrelationIdsAndChildrenInheritWithoutLeaking(): Unit = {
    val program: IO[(RequestLog, RequestLog, String)] = for {
      context <- IOLocal(RequestContext("service", "root"))
      firstReady <- Deferred[IO, Unit]
      secondReady <- Deferred[IO, Unit]
      logs <- IO.both(
        handle("REQ-101", context, firstReady, secondReady),
        handle("REQ-102", context, secondReady, firstReady)
      )
      serviceLog <- audit(context, "batch-completed")
    } yield (logs._1, logs._2, serviceLog)

    val (first, second, serviceLog) = program.unsafeRunTimed(20.seconds).getOrElse {
      throw new AssertionError("Request audit logging did not finish within 20 seconds")
    }

    assertEquals(List(
      "REQ-101/request:accepted",
      "REQ-101/request:receipt-started",
      "REQ-101/receipt:receipt-queued",
      "REQ-101/request:completed"
    ), first.events)
    assertEquals(List(
      "REQ-102/request:accepted",
      "REQ-102/request:receipt-started",
      "REQ-102/receipt:receipt-queued",
      "REQ-102/request:completed"
    ), second.events)
    assertEquals("service/root:idle", first.afterRequest,
      "Request middleware must restore the inherited service context")
    assertEquals("service/root:idle", second.afterRequest)
    assertEquals("service/root:batch-completed", serviceLog,
      "Request correlation IDs must not escape into the service fiber")
  }
}
