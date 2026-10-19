/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import fs2.kafka.*
import fs2.kafka.consumer.KafkaConsumeChunk.CommitNow
import fs2.kafka.consumer.MkConsumer
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord as JavaConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Projects stock movements into an inventory store with chunk-wise consumption through
 * `consumeChunk`: each chunk becomes one batch write, and its offsets are committed only after
 * the write succeeded.
 *
 * The Kafka client underneath is kafka-clients' `MockConsumer`, plugged in through the
 * `MkConsumer` extension point. Every poll delivers one prepared batch per partition, so each
 * batch reaches the processor as one chunk and the commits are deterministic.
 */
class InventoryProjectionChunkTest {

  import InventoryProjectionChunkTest.*

  @Test
  def projectionWritesOneBatchPerChunkAndCommitsAfterEachWrite(): Unit = {
    val polls: List[List[Movement]] = List(
      List(
        Movement(0, 0, "north", """{"sku":"anvil","delta":10}"""),
        Movement(0, 1, "north", """{"sku":"bolt","delta":4}"""),
        Movement(0, 2, "north", """{"sku":"anvil","delta":-3}"""),
        Movement(1, 0, "south", """{"sku":"anvil","delta":6}"""),
        Movement(1, 1, "south", """{"sku":"crate","delta":2}""")
      ),
      List(
        Movement(0, 3, "north", """{"sku":"anvil","delta":-2}"""),
        Movement(0, 4, "north", """{"sku":"bolt","delta":1}"""),
        Movement(1, 2, "south", """{"sku":"crate","delta":-1}""")
      )
    )
    val cluster: WarehouseCluster = new WarehouseCluster(polls, Map.empty)
    given MkConsumer[IO] = cluster.mkConsumer

    val outcome: ProjectionOutcome = runProjection(new InventoryStore(failOnWrite = None), Map(Partition0 -> 5L, Partition1 -> 3L))
      .unsafeRunSync()

    assertEquals(
      Map(("north", "anvil") -> 5, ("north", "bolt") -> 5, ("south", "anvil") -> 6, ("south", "crate") -> 1),
      outcome.stock
    )
    assertEquals(
      List(
        BatchWrite(0, List(0L, 1L, 2L), Map(("north", "anvil") -> 7, ("north", "bolt") -> 4), None),
        BatchWrite(0, List(3L, 4L), Map(("north", "anvil") -> -2, ("north", "bolt") -> 1), Some(3L)),
        BatchWrite(1, List(0L, 1L), Map(("south", "anvil") -> 6, ("south", "crate") -> 2), None),
        BatchWrite(1, List(2L), Map(("south", "crate") -> -1), Some(2L))
      ),
      outcome.writes.sortBy(write => (write.partition, write.offsets.head)),
      "each chunk is one aggregated write, and the previous chunk of its partition is committed by then"
    )
    assertEquals(Map(Partition0 -> 5L, Partition1 -> 3L), outcome.result.committed)
    assertTrue(cluster.isClosed, "releasing the consumer resource closes the Kafka consumer")
  }

  @Test
  def failedBatchWriteLeavesItsChunkUncommittedAndIsReplayedAfterRestart(): Unit = {
    val polls: List[List[Movement]] = List(
      List(
        Movement(0, 0, "north", """{"sku":"anvil","delta":10}"""),
        Movement(0, 1, "north", """{"sku":"bolt","delta":3}""")
      ),
      List(
        Movement(0, 2, "north", """{"sku":"anvil","delta":-4}"""),
        Movement(0, 3, "north", """{"sku":"bolt","delta":-1}""")
      )
    )
    val firstCluster: WarehouseCluster = new WarehouseCluster(polls, Map.empty)
    val firstStore: InventoryStore = new InventoryStore(failOnWrite = Some(2))
    val firstRun: ProjectionOutcome =
      runProjection(firstStore, Map(Partition0 -> 4L))(using firstCluster.mkConsumer).unsafeRunSync()

    firstRun.result.failure match {
      case Some(StoreUnavailable(offsets)) => assertEquals(List(2L, 3L), offsets)
      case other => fail(s"the projection stops with the failed batch write, got $other")
    }
    assertEquals(Map(Partition0 -> 2L), firstRun.result.committed,
      "only the chunk whose write succeeded is committed")
    assertEquals(Map(("north", "anvil") -> 10, ("north", "bolt") -> 3), firstRun.stock)
    assertTrue(firstCluster.isClosed)

    val restartedCluster: WarehouseCluster = new WarehouseCluster(polls, firstRun.result.committed)
    val secondRun: ProjectionOutcome =
      firstStore.recovered
        .flatMap(store => runProjection(store, Map(Partition0 -> 4L))(using restartedCluster.mkConsumer))
        .unsafeRunSync()

    assertEquals(None, secondRun.result.failure)
    assertEquals(
      List(BatchWrite(0, List(2L, 3L), Map(("north", "anvil") -> -4, ("north", "bolt") -> -1), Some(2L))),
      secondRun.writes,
      "the restarted projection replays only the uncommitted chunk"
    )
    assertEquals(Map(("north", "anvil") -> 6, ("north", "bolt") -> 2), secondRun.stock)
    assertEquals(Map(Partition0 -> 4L), secondRun.result.committed)
    assertTrue(restartedCluster.isClosed)
  }
}

object InventoryProjectionChunkTest {

  val MovementsTopic: String = "stock-movements"
  val Partition0: TopicPartition = new TopicPartition(MovementsTopic, 0)
  val Partition1: TopicPartition = new TopicPartition(MovementsTopic, 1)
  val Partitions: Set[TopicPartition] = Set(Partition0, Partition1)

  final case class StockMovement(sku: String, delta: Int)

  object StockMovement {
    given Decoder[StockMovement] = deriveDecoder[StockMovement]
  }

  val movementDeserializer: Deserializer[IO, StockMovement] =
    Deserializer.string[IO].flatMap(text => Deserializer.lift[IO, StockMovement](_ => IO.fromEither(decode[StockMovement](text))))

  val consumerSettings: ConsumerSettings[IO, String, StockMovement] =
    ConsumerSettings(Deserializer[IO, String], movementDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId("inventory-projection")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withPollInterval(10.millis)

  /** One write to the inventory store, with the chunk's committed offset when it was issued. */
  final case class BatchWrite(
      partition: Int,
      offsets: List[Long],
      deltas: Map[(String, String), Int],
      committedBefore: Option[Long]
  )

  final case class StoreUnavailable(offsets: List[Long]) extends RuntimeException(s"store unavailable for $offsets")

  /** The inventory store; it rejects its `failOnWrite`-th batch write, as during an outage. */
  final class InventoryStore(failOnWrite: Option[Int], initialStock: Map[(String, String), Int] = Map.empty) {

    val stock: Ref[IO, Map[(String, String), Int]] = Ref.unsafe(initialStock)

    val attempts: Ref[IO, Int] = Ref.unsafe(0)

    val writes: Ref[IO, List[BatchWrite]] = Ref.unsafe(Nil)

    def write(batch: BatchWrite): IO[Unit] =
      attempts.updateAndGet(_ + 1).flatMap { attempt =>
        if (failOnWrite.contains(attempt)) IO.raiseError(StoreUnavailable(batch.offsets))
        else
          stock.update(levels =>
            batch.deltas.foldLeft(levels) { case (current, (item, delta)) =>
              current.updated(item, current.getOrElse(item, 0) + delta)
            }
          ) >> writes.update(_ :+ batch)
      }

    /** The same stock after the outage, with a fresh write log. */
    def recovered: IO[InventoryStore] = stock.get.map(levels => new InventoryStore(failOnWrite = None, levels))
  }

  final case class RunResult(failure: Option[Throwable], committed: Map[TopicPartition, Long])

  final case class ProjectionOutcome(result: RunResult, writes: List[BatchWrite], stock: Map[(String, String), Int])

  def committedOffsets(consumer: KafkaConsumer[IO, String, StockMovement]): IO[Map[TopicPartition, Long]] =
    consumer.committed(Partitions).map(_.collect { case (partition, offset) if offset != null => partition -> offset.offset })

  /**
   * Runs the projection until the group has committed `target` or the processor fails, then
   * reads the committed offsets back before the consumer is released.
   */
  def runProjection(store: InventoryStore, target: Map[TopicPartition, Long])(using MkConsumer[IO]): IO[ProjectionOutcome] = {
    val result: IO[RunResult] = KafkaConsumer.resource(consumerSettings).use { consumer =>
      def project(chunk: Chunk[ConsumerRecord[String, StockMovement]]): IO[CommitNow] = {
        val records: List[ConsumerRecord[String, StockMovement]] = chunk.toList
        val partition: TopicPartition = new TopicPartition(MovementsTopic, records.head.partition)
        val deltas: Map[(String, String), Int] = records
          .groupMapReduce(record => (record.key, record.value.sku))(_.value.delta)(_ + _)
        committedOffsets(consumer).flatMap { committed =>
          store.write(BatchWrite(partition.partition, records.map(_.offset), deltas, committed.get(partition)))
        }.as(CommitNow)
      }

      def awaitTarget(remaining: Int): IO[Unit] =
        committedOffsets(consumer).flatMap { committed =>
          if (committed == target) IO.unit
          else if (remaining == 0) IO.raiseError(new AssertionError(s"committed offsets stayed at $committed"))
          else IO.sleep(20.millis) >> awaitTarget(remaining - 1)
        }

      for {
        _ <- consumer.subscribeTo(MovementsTopic)
        finished <- IO.race(consumer.consumeChunk(project), awaitTarget(1500)).attempt
        committed <- committedOffsets(consumer)
      } yield RunResult(finished.left.toOption, committed)
    }
    for {
      outcome <- result
      writes <- store.writes.get
      stock <- store.stock.get
    } yield ProjectionOutcome(outcome, writes, stock)
  }

  final case class Movement(partition: Int, offset: Long, warehouse: String, payload: String)

  /**
   * Stands in for the brokers of a two-partition movements topic: the group is assigned both
   * partitions on the first poll, and poll `n` delivers batch `n`, starting from the group's
   * previously committed offsets where there are any.
   */
  final class WarehouseCluster(polls: List[List[Movement]], committedBefore: Map[TopicPartition, Long]) {

    private val current: AtomicReference[MockConsumer[Array[Byte], Array[Byte]]] = new AtomicReference()

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](settings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(OffsetResetStrategy.EARLIEST)
          consumer.updateBeginningOffsets(Partitions.map(_ -> Long.box(0L)).toMap.asJava)
          polls.zipWithIndex.foreach { (batch, index) =>
            consumer.schedulePollTask { () =>
              if (index == 0) {
                consumer.rebalance(Partitions.asJava)
                // Subscribing resets MockConsumer's offsets, so the group's offsets come with the assignment.
                consumer.commitSync(
                  committedBefore.map((partition, offset) => partition -> new OffsetAndMetadata(offset)).asJava
                )
              }
              batch.foreach(movement => consumer.addRecord(toJava(movement)))
            }
          }
          current.set(consumer)
          consumer
        }
    }

    private def toJava(movement: Movement): JavaConsumerRecord[Array[Byte], Array[Byte]] = {
      val key: Array[Byte] = movement.warehouse.getBytes(StandardCharsets.UTF_8)
      val value: Array[Byte] = movement.payload.getBytes(StandardCharsets.UTF_8)
      new JavaConsumerRecord(
        MovementsTopic,
        movement.partition,
        movement.offset,
        1_700_000_000_000L + movement.offset,
        TimestampType.CREATE_TIME,
        key.length,
        value.length,
        key,
        value,
        new RecordHeaders(),
        Optional.empty[Integer]()
      )
    }

    def isClosed: Boolean = current.get.closed
  }
}
