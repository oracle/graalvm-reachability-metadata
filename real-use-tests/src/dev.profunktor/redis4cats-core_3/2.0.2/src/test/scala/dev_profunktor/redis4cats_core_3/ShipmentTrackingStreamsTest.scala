/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.effects.MessageId
import dev.profunktor.redis4cats.effects.StreamMessage
import dev.profunktor.redis4cats.effects.XAddArgs
import dev.profunktor.redis4cats.effects.XRangePoint
import dev.profunktor.redis4cats.effects.XReadOffsets
import dev.profunktor.redis4cats.effects.XTrimArgs
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import dev.profunktor.redis4cats.streams.RedisStream
import dev.profunktor.redis4cats.streams.Streaming
import dev.profunktor.redis4cats.streams.data.XAddMessage
import fs2.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/**
 * Appends to and reads from Redis Streams through `RedisStream` built on a core `RedisClient` connection.
 *
 * A logistics service records every shipment scan as a stream entry: an fs2
 * pipeline appends scans to a per-warehouse stream, a dashboard consumes the
 * scans of both warehouses with XREAD starting from the beginning, a worker
 * resumes from the last entry ID it processed, and a capped audit stream keeps
 * only the newest entries with an exact MAXLEN trim.
 */
class ShipmentTrackingStreamsTest {

  private type Streams = Streaming[IO, [A] =>> Stream[IO, A], String, String]

  /** One shipment scan as the application models it. */
  private final case class Scan(shipment: String, status: String, warehouse: String) {
    def body: Map[String, String] = Map("shipment" -> shipment, "status" -> status)
  }

  private object Scan {
    def fromMessage(message: StreamMessage[String, String]): Scan =
      Scan(message.body("shipment"), message.body("status"), message.key.stripPrefix("scans:"))
  }

  /** What the tracking service observed after a working day of scans. */
  private final case class Observed(
      appendedIds: List[MessageId],
      dashboard: Map[String, List[Scan]],
      resumed: List[Scan],
      auditLength: Long,
      auditStatuses: List[String]
  )

  /** Appends scans and replays them through the streaming API. */
  private final class TrackingService(streams: Streams, redis: RedisCommands[IO, String, String]) {

    private val auditKey: String = "audit:shipments"

    def record(scans: List[Scan]): IO[List[MessageId]] =
      Stream
        .emits(scans)
        .map(scan => XAddMessage(s"scans:${scan.warehouse}", scan.body))
        .through(streams.append)
        .compile
        .toList

    def audit(entries: List[String]): IO[Unit] = {
      val capped: XAddArgs = XAddArgs(xTrimArgs = Some(XTrimArgs(XTrimArgs.Strategy.MAXLEN(3L))))
      Stream
        .emits(entries)
        .evalMap(status => streams.append(XAddMessage(auditKey, Map("status" -> status), capped)))
        .compile
        .drain
    }

    def dashboard(warehouses: List[String], expected: Int): IO[Map[String, List[Scan]]] =
      streams
        .read(XReadOffsets.all(warehouses.map(w => s"scans:$w")*), block = None, count = Some(2L))
        .take(expected.toLong)
        .map(Scan.fromMessage)
        .compile
        .toList
        .map(_.groupBy(_.warehouse))

    def resumeAfter(warehouse: String, lastSeen: MessageId, expected: Int): IO[List[Scan]] =
      streams
        .read(XReadOffsets.custom(lastSeen.value, s"scans:$warehouse"), block = None)
        .take(expected.toLong)
        .map(Scan.fromMessage)
        .compile
        .toList

    def auditTrail: IO[(Long, List[String])] =
      for {
        length <- redis.xLen(auditKey)
        entries <- redis.xRange(auditKey, XRangePoint.Unbounded, XRangePoint.Unbounded)
      } yield (length, entries.map(_.body("status")))
  }

  @Test
  def recordsAndReplaysShipmentScans(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("shipment-tracking")
      given Log[IO] = log4CatsInstance[IO]

      val connections: Resource[IO, (Streams, RedisCommands[IO, String, String])] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          streams <- RedisStream.mkStreamingConnectionResource[IO, String, String](client, RedisCodec.Utf8)
          redis <- Redis[IO].fromClient(client, RedisCodec.Utf8)
        } yield (streams, redis)

      val scans: List[Scan] = List(
        Scan("S-100", "received", "berlin"),
        Scan("S-101", "received", "lyon"),
        Scan("S-100", "sorted", "berlin"),
        Scan("S-102", "received", "berlin"),
        Scan("S-101", "dispatched", "lyon"),
        Scan("S-100", "dispatched", "berlin")
      )

      val observed: Observed = connections
        .use { (streams, redis) =>
          val service: TrackingService = new TrackingService(streams, redis)
          for {
            ids <- service.record(scans)
            board <- service.dashboard(List("berlin", "lyon"), scans.size)
            resumed <- service.resumeAfter("berlin", ids(2), expected = 2)
            _ <- service.audit(List("open", "scan", "scan", "close", "archive"))
            (auditLength, auditStatuses) <- service.auditTrail
          } yield Observed(ids, board, resumed, auditLength, auditStatuses)
        }
        .unsafeRunSync()

      // Entry IDs are unique per stream, so two warehouses may share one.
      val idsPerWarehouse: Map[String, List[MessageId]] =
        scans.zip(observed.appendedIds).groupMap(_._1.warehouse)(_._2)
      assertEquals(scans.size, observed.appendedIds.size)
      idsPerWarehouse.values.foreach(ids => assertEquals(ids.size, ids.distinct.size))
      assertEquals(scans.filter(_.warehouse == "berlin"), observed.dashboard("berlin"))
      assertEquals(scans.filter(_.warehouse == "lyon"), observed.dashboard("lyon"))
      assertEquals(List(Scan("S-102", "received", "berlin"), Scan("S-100", "dispatched", "berlin")), observed.resumed)
      assertEquals(3L, observed.auditLength)
      assertEquals(List("scan", "close", "archive"), observed.auditStatuses)
    } finally {
      server.stop()
    }
  }
}
