/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Duration, FiberRef, Promise, Runtime, UIO, Unsafe, ZIO}

/** Exercises ZIO FiberRef request context by isolating tenant audit records across concurrent requests. */
class RequestAuditContextTest {
  private case class RequestContext(requestId: String, tenant: String)
  private case class AuditEntry(requestId: String, tenant: String, action: String)

  private def audit(context: FiberRef[RequestContext], action: String): UIO[AuditEntry] =
    context.get.map(current => AuditEntry(current.requestId, current.tenant, action))

  private def handleRequest(
      context: FiberRef[RequestContext],
      request: RequestContext,
      started: Promise[Nothing, Unit],
      otherStarted: Promise[Nothing, Unit]
  ): UIO[Vector[AuditEntry]] =
    context.locally(request) {
      for {
        _ <- started.succeed(())
        // Both requests must have installed their context before either writes an audit record.
        _ <- otherStarted.await
        received <- audit(context, "request-received")
        receiptWorker <- audit(context, "receipt-generated").fork
        receipt <- receiptWorker.join
        completed <- audit(context, "request-completed")
      } yield Vector(received, receipt, completed)
    }

  @Test
  @Timeout(60)
  def concurrentRequestsAndTheirChildFibersKeepTheirOwnAuditContext(): Unit = {
    val system: RequestContext = RequestContext("background", "operations")
    val first: RequestContext = RequestContext("REQ-101", "north-shop")
    val second: RequestContext = RequestContext("REQ-202", "south-shop")
    val program = ZIO.scoped {
      for {
        context <- FiberRef.make(system)
        firstStarted <- Promise.make[Nothing, Unit]
        secondStarted <- Promise.make[Nothing, Unit]
        records <- handleRequest(context, first, firstStarted, secondStarted).zipPar(
          handleRequest(context, second, secondStarted, firstStarted)
        )
        background <- audit(context, "maintenance")
      } yield (records, background)
    }

    val ((firstRecords, secondRecords), background) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Request auditing timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(
      Vector(
        AuditEntry("REQ-101", "north-shop", "request-received"),
        AuditEntry("REQ-101", "north-shop", "receipt-generated"),
        AuditEntry("REQ-101", "north-shop", "request-completed")
      ),
      firstRecords
    )
    assertEquals(
      Vector(
        AuditEntry("REQ-202", "south-shop", "request-received"),
        AuditEntry("REQ-202", "south-shop", "receipt-generated"),
        AuditEntry("REQ-202", "south-shop", "request-completed")
      ),
      secondRecords
    )
    assertEquals(AuditEntry("background", "operations", "maintenance"), background)
  }
}
