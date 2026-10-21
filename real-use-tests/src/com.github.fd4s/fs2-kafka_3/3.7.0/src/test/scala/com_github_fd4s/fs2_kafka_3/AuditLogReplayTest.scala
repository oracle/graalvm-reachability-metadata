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

import cats.data.NonEmptySet
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Stream
import fs2.kafka.*
import fs2.kafka.consumer.MkConsumer
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord as JavaConsumerRecord
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Replays an audit log through manual partition assignment on the fs2-kafka consumer:
 * discovering partitions, reading beginning and end offsets, seeking to checkpoints, and
 * reading every per-partition stream up to the end offset, all without a consumer group.
 *
 * The Kafka client underneath is kafka-clients' `MockConsumer`, plugged in through the
 * `MkConsumer` extension point; it knows the topic's partitions and offsets up front and
 * delivers the retained log on the first poll, so the replayed records are deterministic.
 */
class AuditLogReplayTest {

  import AuditLogReplayTest.*

  @Test
  def replayResumesFromCheckpointsAndStopsAtTheEndOffsets(): Unit = {
    val cluster: AuditCluster = new AuditCluster
    given MkConsumer[IO] = cluster.mkConsumer
    // Partition 1 lost offsets 0-2 to retention, and partition 2 was never checkpointed.
    val checkpoints: Map[TopicPartition, Long] = Map(Partition0 -> 2L, Partition1 -> 1L)

    val outcome: ReplayOutcome = replayAll(checkpoints).unsafeRunSync()

    assertEquals(List(Partition0, Partition1, Partition2), outcome.discovered)
    assertEquals(Map(Partition0 -> 0L, Partition1 -> 3L, Partition2 -> 0L), outcome.beginning)
    assertEquals(Map(Partition0 -> 4L, Partition1 -> 7L, Partition2 -> 2L), outcome.end)
    assertEquals(Map(Partition0 -> 2L, Partition1 -> 3L, Partition2 -> 0L), outcome.startPositions,
      "a checkpoint behind the retained log is clamped to the first retained offset")
    assertEquals(Set(Partition0, Partition1, Partition2), outcome.streamedPartitions,
      "each manually assigned partition gets its own record stream")
    assertEquals(
      List(2L -> AuditEvent("alice", "export-report"), 3L -> AuditEvent("alice", "logout")),
      outcome.replayed(Partition0)
    )
    assertEquals(
      List(
        3L -> AuditEvent("carol", "grant-admin"),
        4L -> AuditEvent("dave", "login"),
        5L -> AuditEvent("dave", "delete-user"),
        6L -> AuditEvent("carol", "revoke-admin")
      ),
      outcome.replayed(Partition1)
    )
    assertEquals(
      List(0L -> AuditEvent("erin", "login"), 1L -> AuditEvent("erin", "rotate-key")),
      outcome.replayed(Partition2)
    )
    assertEquals(List("alice", "alice"), outcome.keys(Partition0), "record keys are the actors")
    assertEquals(Some(Epoch + 5L), outcome.createTimes(Partition1).drop(2).headOption)
    assertEquals(outcome.end, outcome.endPositions, "the replay stops exactly at the end offsets")
    assertTrue(cluster.isClosed, "releasing the consumer resource closes the Kafka consumer")

    val config: Map[String, String] = cluster.lastSettings
    assertFalse(config.contains(ConsumerConfig.GROUP_ID_CONFIG), "the replay joins no consumer group")
    assertEquals("audit-replay", config(ConsumerConfig.CLIENT_ID_CONFIG))
    assertEquals("none", config(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG),
      "every start position is chosen by the application")
    assertEquals("false", config(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))
  }

  @Test
  def tailReadsTheLastEventsOfOneAssignedPartition(): Unit = {
    val cluster: AuditCluster = new AuditCluster
    given MkConsumer[IO] = cluster.mkConsumer

    val outcome: TailOutcome = tail(Partition1.partition, 2).unsafeRunSync()

    assertEquals(Set(Partition1), outcome.assignment, "only the requested partition is assigned")
    assertEquals(7L, outcome.endPosition, "seeking to the end positions the consumer at the end offset")
    assertEquals(
      List(5L -> AuditEvent("dave", "delete-user"), 6L -> AuditEvent("carol", "revoke-admin")),
      outcome.events
    )
    assertEquals(7L, outcome.finalPosition)
    assertTrue(cluster.isClosed)
  }
}

object AuditLogReplayTest {

  val AuditTopic: String = "audit-log"
  val Partition0: TopicPartition = new TopicPartition(AuditTopic, 0)
  val Partition1: TopicPartition = new TopicPartition(AuditTopic, 1)
  val Partition2: TopicPartition = new TopicPartition(AuditTopic, 2)
  val Epoch: Long = 1_700_000_000_000L

  final case class AuditEvent(actor: String, action: String)

  object AuditEvent {
    given Decoder[AuditEvent] = deriveDecoder[AuditEvent]
  }

  /** The retained log of each partition: its first retained offset and the records from there. */
  final case class RetainedLog(firstOffset: Long, events: List[AuditEvent]) {
    def endOffset: Long = firstOffset + events.size
  }

  val Log: Map[TopicPartition, RetainedLog] = Map(
    Partition0 -> RetainedLog(
      0L,
      List(
        AuditEvent("alice", "login"),
        AuditEvent("bob", "view-report"),
        AuditEvent("alice", "export-report"),
        AuditEvent("alice", "logout")
      )
    ),
    Partition1 -> RetainedLog(
      3L,
      List(
        AuditEvent("carol", "grant-admin"),
        AuditEvent("dave", "login"),
        AuditEvent("dave", "delete-user"),
        AuditEvent("carol", "revoke-admin")
      )
    ),
    Partition2 -> RetainedLog(0L, List(AuditEvent("erin", "login"), AuditEvent("erin", "rotate-key")))
  )

  val auditEventDeserializer: Deserializer[IO, AuditEvent] =
    Deserializer.string[IO].flatMap { text =>
      Deserializer.lift[IO, AuditEvent](_ => IO.fromEither(decode[AuditEvent](text)))
    }

  val consumerSettings: ConsumerSettings[IO, String, AuditEvent] =
    ConsumerSettings(Deserializer[IO, String], auditEventDeserializer)
      .withBootstrapServers("localhost:9092")
      .withClientId("audit-replay")
      .withAutoOffsetReset(AutoOffsetReset.None)
      .withEnableAutoCommit(false)
      .withPollInterval(10.millis)

  final case class ReplayOutcome(
      discovered: List[TopicPartition],
      beginning: Map[TopicPartition, Long],
      end: Map[TopicPartition, Long],
      startPositions: Map[TopicPartition, Long],
      streamedPartitions: Set[TopicPartition],
      records: List[(TopicPartition, ConsumerRecord[String, AuditEvent])],
      endPositions: Map[TopicPartition, Long]
  ) {
    private def of(partition: TopicPartition): List[ConsumerRecord[String, AuditEvent]] =
      records.collect { case (`partition`, record) => record }

    def replayed(partition: TopicPartition): List[(Long, AuditEvent)] =
      of(partition).map(record => record.offset -> record.value)

    def keys(partition: TopicPartition): List[String] = of(partition).map(_.key)

    def createTimes(partition: TopicPartition): List[Long] = of(partition).flatMap(_.timestamp.createTime)
  }

  /**
   * Replays every partition of the audit topic from its checkpoint, or from the beginning
   * when it has none, and reads each partition up to the end offset seen at startup.
   */
  def replayAll(checkpoints: Map[TopicPartition, Long])(using MkConsumer[IO]): IO[ReplayOutcome] =
    KafkaConsumer.resource(consumerSettings).use { consumer =>
      for {
        infos <- consumer.partitionsFor(AuditTopic)
        discovered = infos.map(info => new TopicPartition(info.topic, info.partition)).sortBy(_.partition)
        _ <- consumer.assign(AuditTopic)
        beginning <- consumer.beginningOffsets(discovered.toSet)
        end <- consumer.endOffsets(discovered.toSet)
        unchecked = discovered.filterNot(checkpoints.contains)
        _ <- consumer.seekToBeginning(unchecked)
        _ <- checkpoints.toList.traverse_((partition, offset) =>
          consumer.seek(partition, math.max(offset, beginning(partition))))
        startPositions <- discovered.traverse(partition => consumer.position(partition).map(partition -> _))
        remaining = startPositions.map((partition, position) => end(partition) - position).sum
        streamed <- Ref.of[IO, Set[TopicPartition]](Set.empty)
        records <- consumer.partitionsMapStream
          .evalTap(streams => streamed.update(_ ++ streams.keySet))
          .flatMap { streams =>
            Stream
              .iterable(streams)
              .map((partition, records) => records.map(committable => partition -> committable.record))
              .parJoinUnbounded
          }
          .take(remaining)
          .compile
          .toList
          .timeout(30.seconds)
        endPositions <- discovered.traverse(partition => consumer.position(partition).map(partition -> _))
        streamedPartitions <- streamed.get
      } yield ReplayOutcome(
        discovered,
        beginning,
        end,
        startPositions.toMap,
        streamedPartitions,
        records.sortBy((partition, record) => (partition.partition, record.offset)),
        endPositions.toMap
      )
    }

  final case class TailOutcome(
      assignment: Set[TopicPartition],
      endPosition: Long,
      events: List[(Long, AuditEvent)],
      finalPosition: Long
  )

  /** Reads the last `count` events of one audit partition. */
  def tail(partition: Int, count: Int)(using MkConsumer[IO]): IO[TailOutcome] =
    KafkaConsumer.resource(consumerSettings).use { consumer =>
      val topicPartition: TopicPartition = new TopicPartition(AuditTopic, partition)
      for {
        _ <- consumer.assign(AuditTopic, NonEmptySet.one(partition))
        assignment <- consumer.assignment
        _ <- consumer.seekToEnd(List(topicPartition))
        endPosition <- consumer.position(topicPartition)
        _ <- consumer.seek(topicPartition, endPosition - count)
        events <- consumer.stream
          .map(committable => committable.record.offset -> committable.record.value)
          .take(count.toLong)
          .compile
          .toList
          .timeout(30.seconds)
        finalPosition <- consumer.position(topicPartition)
      } yield TailOutcome(assignment.toSet, endPosition, events, finalPosition)
    }

  /**
   * Stands in for the brokers of the three-partition audit topic: it reports the partitions
   * and their retained offsets, and delivers the retained log of every assigned partition on
   * the first poll, from which the consumer keeps only the records at or after its position.
   */
  final class AuditCluster {

    private val current: AtomicReference[MockConsumer[Array[Byte], Array[Byte]]] = new AtomicReference()

    private val settings: AtomicReference[Map[String, String]] = new AtomicReference(Map.empty)

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](consumerSettings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          settings.set(consumerSettings.properties)
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(OffsetResetStrategy.NONE)
          val leader: Node = new Node(1, "localhost", 9092)
          consumer.updatePartitions(
            AuditTopic,
            Log.keys.toList
              .sortBy(_.partition)
              .map(partition => new PartitionInfo(AuditTopic, partition.partition, leader, Array(leader), Array(leader)))
              .asJava
          )
          consumer.updateBeginningOffsets(Log.view.mapValues(log => Long.box(log.firstOffset)).toMap.asJava)
          consumer.updateEndOffsets(Log.view.mapValues(log => Long.box(log.endOffset)).toMap.asJava)
          consumer.schedulePollTask { () =>
            consumer.assignment.asScala.foreach { partition =>
              val log: RetainedLog = Log(partition)
              log.events.zipWithIndex.foreach { (event, index) =>
                consumer.addRecord(toJava(partition, log.firstOffset + index, event))
              }
            }
          }
          current.set(consumer)
          consumer
        }
    }

    private def toJava(partition: TopicPartition, offset: Long, event: AuditEvent): JavaConsumerRecord[Array[Byte], Array[Byte]] = {
      val key: Array[Byte] = event.actor.getBytes(StandardCharsets.UTF_8)
      val value: Array[Byte] =
        s"""{"actor":"${event.actor}","action":"${event.action}"}""".getBytes(StandardCharsets.UTF_8)
      new JavaConsumerRecord(
        partition.topic,
        partition.partition,
        offset,
        Epoch + offset,
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

    def lastSettings: Map[String, String] = settings.get
  }
}
