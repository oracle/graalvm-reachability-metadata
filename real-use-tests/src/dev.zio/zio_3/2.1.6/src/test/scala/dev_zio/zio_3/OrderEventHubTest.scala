/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Dequeue, Duration, Hub, Runtime, UIO, Unsafe, ZIO}

/** Exercises ZIO Hub publish-subscribe by broadcasting orders to sales and dispatch projections. */
class OrderEventHubTest {
  private case class OrderPlaced(id: String, totalCents: Long, destination: String)
  private case class SalesSummary(orderIds: Vector[String], revenueCents: Long)
  private case class DispatchRequest(orderId: String, destination: String)

  private def project[A](
      events: Dequeue[Option[OrderPlaced]],
      state: A
  )(update: (A, OrderPlaced) => A): UIO[A] =
    events.take.flatMap {
      case Some(order) => project(events, update(state, order))(update)
      case None => ZIO.succeed(state)
    }

  @Test
  @Timeout(60)
  def eachSubscriberReceivesEveryOrderInPublicationOrder(): Unit = {
    val orders: List[OrderPlaced] = List(
      OrderPlaced("ORDER-301", 2400L, "Berlin"),
      OrderPlaced("ORDER-302", 1750L, "Paris"),
      OrderPlaced("ORDER-303", 3100L, "Amsterdam"),
      OrderPlaced("ORDER-304", 850L, "Madrid")
    )
    val program: UIO[(SalesSummary, Vector[DispatchRequest])] = ZIO.scoped {
      for {
        events <- ZIO.acquireRelease(Hub.bounded[Option[OrderPlaced]](2))(_.shutdown)
        // Subscribe both projections before publishing: Hub does not replay earlier orders.
        salesEvents <- events.subscribe
        dispatchEvents <- events.subscribe
        sales <- project(salesEvents, SalesSummary(Vector.empty[String], 0L)) {
          (summary, order) =>
            SalesSummary(summary.orderIds :+ order.id, summary.revenueCents + order.totalCents)
        }.forkScoped
        dispatch <- project(dispatchEvents, Vector.empty[DispatchRequest]) {
          (requests, order) => requests :+ DispatchRequest(order.id, order.destination)
        }.forkScoped
        _ <- ZIO.foreachDiscard(orders)(order => events.publish(Some(order)))
        // Every subscriber drains the preceding orders before observing the end of the batch.
        _ <- events.publish(None)
        summary <- sales.join
        requests <- dispatch.join
      } yield (summary, requests)
    }

    val (summary, requests) = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Order projections timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(SalesSummary(Vector("ORDER-301", "ORDER-302", "ORDER-303", "ORDER-304"), 8100L), summary)
    assertEquals(
      Vector(
        DispatchRequest("ORDER-301", "Berlin"),
        DispatchRequest("ORDER-302", "Paris"),
        DispatchRequest("ORDER-303", "Amsterdam"),
        DispatchRequest("ORDER-304", "Madrid")
      ),
      requests
    )
  }
}
