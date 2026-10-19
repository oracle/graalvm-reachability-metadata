/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisChannel
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.data.RedisPattern
import dev.profunktor.redis4cats.data.RedisPatternEvent
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import dev.profunktor.redis4cats.pubsub.PubSub
import dev.profunktor.redis4cats.pubsub.PubSubCommands
import dev.profunktor.redis4cats.pubsub.PublishCommands
import dev.profunktor.redis4cats.pubsub.data.Subscription
import fs2.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.DurationInt

/**
 * Delivers Pub/Sub messages over `RedisChannel` subscriptions and `RedisPattern` subscriptions.
 *
 * An order service announces new orders on the `orders` channel and raises
 * operational alerts on `alerts.<area>` channels. A dashboard listens to the
 * order channel and to every alert channel through one pattern subscription,
 * while the service publishes over its own connection and inspects the
 * server's subscription counters. Before the real events go out, the service
 * keeps sending a probe until each listener has seen it, so no event is
 * published before the dashboard is live. Once the dashboard has what it needs,
 * its subscriptions end and the server no longer counts them.
 */
class OrderEventsPubSubTest {

  private val Probe: String = "probe"
  private val Orders: RedisChannel[String] = RedisChannel("orders")
  private val Alerts: RedisPattern[String] = RedisPattern("alerts.*")

  /** What the application reads back from the server and the dashboard. */
  private final case class Observed(
      liveOrderSubscribers: Option[Subscription[String]],
      livePatterns: Long,
      liveChannels: List[RedisChannel[String]],
      orders: List[String],
      alerts: Set[RedisPatternEvent[String, String]],
      orderSubscribersAfter: Option[Subscription[String]],
      patternsAfter: Long
  )

  /** Collects order events and alerts, signalling once each subscription has seen a probe. */
  private final class Dashboard(subscriber: PubSubCommands[IO, [A] =>> Stream[IO, A], String, String]) {

    def orders(live: Deferred[IO, Unit], count: Int): IO[List[String]] =
      subscriber
        .subscribe(Orders)
        .evalTap(message => live.complete(()).void.whenA(message == Probe))
        .filter(_ != Probe)
        .take(count.toLong)
        .compile
        .toList

    def alerts(live: Deferred[IO, Unit], count: Int): IO[Set[RedisPatternEvent[String, String]]] =
      subscriber
        .psubscribe(Alerts)
        .evalTap(event => live.complete(()).void.whenA(event.data == Probe))
        .filter(_.data != Probe)
        .take(count.toLong)
        .compile
        .to(Set)
  }

  /** Publishes events and reads the server's Pub/Sub counters. */
  private final class OrderService(publisher: PublishCommands[IO, [A] =>> Stream[IO, A], String, String]) {

    def announce(order: String): IO[Unit] = publisher.publish(Orders, order).void

    def alert(area: String, text: String): IO[Unit] = publisher.publish(RedisChannel(s"alerts.$area"), text).void

    /** Publishes `probe` until `live` completes, bounded so a dead listener fails the run. */
    def awaitListener(probe: IO[Unit], live: Deferred[IO, Unit]): IO[Unit] =
      (probe *> live.get.timeout(100.millis).attempt)
        .iterateUntil(_.isRight)
        .void
        .timeout(30.seconds)

    /** Polls the server until no client subscribes to the order channel or any pattern. */
    def awaitUnsubscribed: IO[(Option[Subscription[String]], Long)] =
      (publisher.pubSubSubscriptions(Orders), publisher.numPat).tupled
        .flatTap(_ => IO.sleep(50.millis))
        .iterateUntil((orders, patterns) => orders.forall(_.number == 0L) && patterns == 0L)
        .timeout(30.seconds)
  }

  @Test
  def deliversOrderEventsAndAlertsToTheDashboard(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("order-events")
      given Log[IO] = log4CatsInstance[IO]

      type Connections = (
          PubSubCommands[IO, [A] =>> Stream[IO, A], String, String],
          PublishCommands[IO, [A] =>> Stream[IO, A], String, String]
      )
      val connections: Resource[IO, Connections] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          subscriber <- PubSub.mkPubSubConnection[IO, String, String](client, RedisCodec.Utf8)
          publisher <- PubSub.mkPublisherConnection[IO, String, String](client, RedisCodec.Utf8)
        } yield (subscriber, publisher)

      val observed: Observed = connections
        .use { (subscriber, publisher) =>
          val dashboard: Dashboard = new Dashboard(subscriber)
          val service: OrderService = new OrderService(publisher)
          for {
            ordersLive <- Deferred[IO, Unit]
            alertsLive <- Deferred[IO, Unit]
            ordersFiber <- dashboard.orders(ordersLive, 3).start
            alertsFiber <- dashboard.alerts(alertsLive, 2).start
            _ <- service.awaitListener(service.announce(Probe), ordersLive)
            _ <- service.awaitListener(service.alert("probe", Probe), alertsLive)
            liveOrders <- publisher.pubSubSubscriptions(Orders)
            livePatterns <- publisher.numPat
            liveChannels <- publisher.pubSubChannels
            _ <- service.announce("order-1001")
            _ <- service.alert("disk", "disk 91% full")
            _ <- service.announce("order-1002")
            _ <- service.alert("payments", "gateway latency high")
            _ <- service.announce("order-1003")
            orders <- ordersFiber.joinWithNever.timeout(30.seconds)
            alerts <- alertsFiber.joinWithNever.timeout(30.seconds)
            (ordersAfter, patternsAfter) <- service.awaitUnsubscribed
          } yield Observed(liveOrders, livePatterns, liveChannels, orders, alerts, ordersAfter, patternsAfter)
        }
        .unsafeRunSync()

      assertEquals(Some(Subscription(Orders, 1L)), observed.liveOrderSubscribers)
      assertEquals(1L, observed.livePatterns)
      assertEquals(List(Orders), observed.liveChannels)
      assertEquals(List("order-1001", "order-1002", "order-1003"), observed.orders)
      assertEquals(
        Set(
          RedisPatternEvent("alerts.*", "alerts.disk", "disk 91% full"),
          RedisPatternEvent("alerts.*", "alerts.payments", "gateway latency high")
        ),
        observed.alerts
      )
      assertEquals(0L, observed.orderSubscribersAfter.fold(0L)(_.number))
      assertEquals(0L, observed.patternsAfter)
    } finally {
      server.stop()
    }
  }
}
