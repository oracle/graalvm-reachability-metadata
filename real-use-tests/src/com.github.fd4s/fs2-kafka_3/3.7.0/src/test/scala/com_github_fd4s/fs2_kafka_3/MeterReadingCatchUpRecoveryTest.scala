/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import fs2.kafka.*
import fs2.kafka.consumer.MkConsumer
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord as JavaConsumerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.DisconnectException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.TopicAuthorizationException
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Consumer failure recovery: a broker error on poll fails the fs2-kafka record stream with the
 * Kafka client's exception and closes the consumer, and the application's restart policy
 * re-creates the consumer for retriable errors, resuming from the committed offsets.
 *
 * The job is a catch-up replay of smart meter readings up to the topic's end offsets. The
 * Kafka client underneath is kafka-clients' `MockConsumer`, one per consumer incarnation,
 * plugged in through `MkConsumer`. A planned outage delivers the readings below a cutoff and
 * fails the first poll after the group has committed all of them, so what each incarnation
 * reads is deterministic.
 */
class MeterReadingCatchUpRecoveryTest {

  import MeterReadingCatchUpRecoveryTest.*

  @Test
  def retriableOutageRestartsTheConsumerFromTheCommittedOffsets(): Unit = {
    val cluster: MeterCluster = new MeterCluster(List(Outage(3L, new DisconnectException("broker 1 went away"))))
    given MkConsumer[IO] = cluster.mkConsumer

    val job: CatchUpJob = new CatchUpJob()
    val committed: Map[TopicPartition, OffsetAndMetadata] = job.run.unsafeRunSync()

    assertEquals(List(Incident(1, "DisconnectException", "broker 1 went away")), job.incidents,
      "the poll failure surfaces as the Kafka client's own exception")
    assertEquals(2, cluster.incarnations, "one restart replaces the failed consumer")
    assertTrue(cluster.allClosed, "the failed consumer is closed before the restart")
    assertEquals(Map(Partition0 -> committedAt(3L, 1), Partition1 -> committedAt(3L, 1)), cluster.offsetsAtOutage(0),
      "every reading delivered before the outage was committed with its incarnation's metadata")

    assertEquals(Wave(1, 0L, 3L), job.readWindow(1, Partition0))
    assertEquals(Wave(1, 0L, 3L), job.readWindow(1, Partition1))
    assertEquals(Wave(2, 3L, 6L), job.readWindow(2, Partition0),
      "the restarted consumer starts at the committed offset, so no reading is read twice")
    assertEquals(Wave(2, 3L, 5L), job.readWindow(2, Partition1))
    assertEquals(Readings.size, job.readCount)

    assertEquals(ExpectedTotals, job.totals)
    assertEquals(Map(Partition0 -> committedAt(6L, 2), Partition1 -> committedAt(5L, 2)), committed,
      "the job stops once the group has committed up to the end offsets")
    assertEquals("meter-catch-up", cluster.lastSettings(ConsumerConfig.GROUP_ID_CONFIG))
    assertEquals("catch-up-2", cluster.lastSettings(ConsumerConfig.CLIENT_ID_CONFIG))
  }

  @Test
  def authorizationFailureStopsTheJobWithoutRestarting(): Unit = {
    val cluster: MeterCluster =
      new MeterCluster(List(Outage(3L, new TopicAuthorizationException(Set(ReadingsTopic).asJava))))
    given MkConsumer[IO] = cluster.mkConsumer

    val job: CatchUpJob = new CatchUpJob()
    val failure: Either[Throwable, Map[TopicPartition, OffsetAndMetadata]] = job.run.attempt.unsafeRunSync()

    failure match {
      case Left(error: TopicAuthorizationException) =>
        assertEquals(Set(ReadingsTopic), error.unauthorizedTopics.asScala.toSet)
      case other => fail(s"expected a topic authorization failure, got $other")
    }
    assertEquals(Nil, job.incidents, "an authorization failure is not retried")
    assertEquals(1, cluster.incarnations)
    assertTrue(cluster.allClosed, "the consumer is closed when the job fails")
    assertEquals(Map(Partition0 -> committedAt(3L, 1), Partition1 -> committedAt(3L, 1)), cluster.offsetsAtOutage(0),
      "progress made before the failure stays committed for the next deployment")
    assertEquals(6, job.readCount)
  }

  @Test
  def repeatedOutagesExhaustTheRestartBudget(): Unit = {
    val cluster: MeterCluster = new MeterCluster(List(
      Outage(2L, new DisconnectException("broker 1 went away")),
      Outage(4L, new DisconnectException("broker 2 went away")),
      Outage(5L, new DisconnectException("broker 3 went away"))
    ))
    given MkConsumer[IO] = cluster.mkConsumer

    val job: CatchUpJob = new CatchUpJob()
    val failure: Either[Throwable, Map[TopicPartition, OffsetAndMetadata]] = job.run.attempt.unsafeRunSync()

    failure match {
      case Left(error: DisconnectException) => assertEquals("broker 3 went away", error.getMessage)
      case other => fail(s"expected the last outage to surface, got $other")
    }
    assertEquals(
      List(Incident(1, "DisconnectException", "broker 1 went away"), Incident(2, "DisconnectException", "broker 2 went away")),
      job.incidents
    )
    assertEquals(MaxRestarts + 1, cluster.incarnations)
    assertTrue(cluster.allClosed)
    assertEquals(Wave(1, 0L, 2L), job.readWindow(1, Partition0))
    assertEquals(Wave(2, 2L, 4L), job.readWindow(2, Partition0))
    assertEquals(Wave(3, 4L, 5L), job.readWindow(3, Partition0))
    assertEquals(Wave(3, 4L, 5L), job.readWindow(3, Partition1))
    assertEquals(10, job.readCount, "each incarnation reads only what its predecessor left uncommitted")
    assertEquals(Map(Partition0 -> committedAt(5L, 3), Partition1 -> committedAt(5L, 3)), cluster.offsetsAtOutage(2))
  }
}

object MeterReadingCatchUpRecoveryTest {

  val ReadingsTopic: String = "meter-readings"
  val Partition0: TopicPartition = new TopicPartition(ReadingsTopic, 0)
  val Partition1: TopicPartition = new TopicPartition(ReadingsTopic, 1)
  val Partitions: Set[TopicPartition] = Set(Partition0, Partition1)
  val MaxRestarts: Int = 2

  /** A meter reading in watt-hours, keyed by meter on the wire. */
  final case class Reading(partition: Int, meter: String, id: String, wattHours: Long)

  val Readings: List[Reading] = List(
    Reading(0, "meter-a", "r-01", 120), Reading(1, "meter-b", "r-02", 75),
    Reading(0, "meter-a", "r-03", 130), Reading(1, "meter-c", "r-04", 40),
    Reading(0, "meter-d", "r-05", 300), Reading(1, "meter-b", "r-06", 80),
    Reading(0, "meter-a", "r-07", 110), Reading(1, "meter-c", "r-08", 45),
    Reading(0, "meter-d", "r-09", 290), Reading(1, "meter-b", "r-10", 70),
    Reading(0, "meter-a", "r-11", 125)
  )

  val ExpectedTotals: Map[String, Long] = Map("meter-a" -> 485L, "meter-b" -> 225L, "meter-c" -> 85L, "meter-d" -> 590L)

  def committedAt(offset: Long, incarnation: Int): OffsetAndMetadata =
    new OffsetAndMetadata(offset, metadataFor(incarnation))

  def metadataFor(incarnation: Int): String = s"catch-up-$incarnation"

  /** The reading value is `<reading id>=<watt-hours>`. */
  val readingDeserializer: Deserializer[IO, (String, Long)] =
    Deserializer.string[IO].flatMap { text =>
      text.split('=') match {
        case Array(id, wattHours) => Deserializer.const[IO, (String, Long)](id -> wattHours.toLong)
        case _ => Deserializer.failWith[IO, (String, Long)](s"malformed reading $text")
      }
    }

  /** Each incarnation gets its own client id and stamps its commits with it. */
  def consumerSettings(incarnation: Int): ConsumerSettings[IO, String, (String, Long)] =
    ConsumerSettings(Deserializer[IO, String], readingDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId("meter-catch-up")
      .withClientId(s"catch-up-$incarnation")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withPollInterval(10.millis)
      .withRecordMetadata(_ => metadataFor(incarnation))

  final case class Incident(incarnation: Int, error: String, message: String)

  final case class Read(incarnation: Int, partition: TopicPartition, offset: Long)

  /** The first and one-past-last offsets one incarnation read from one partition. */
  final case class Wave(incarnation: Int, from: Long, until: Long)

  /**
   * Replays the readings topic up to its end offsets into per-meter totals, committing in
   * batches. A retriable Kafka error restarts the consumer up to [[MaxRestarts]] times; any
   * other error fails the job.
   */
  final class CatchUpJob {

    private val reads: Ref[IO, Vector[Read]] = Ref.unsafe(Vector.empty)
    private val totalsRef: Ref[IO, Map[String, Long]] = Ref.unsafe(Map.empty)
    private val incidentsRef: Ref[IO, List[Incident]] = Ref.unsafe(Nil)

    def run(using MkConsumer[IO]): IO[Map[TopicPartition, OffsetAndMetadata]] = supervise(1)

    private def supervise(incarnation: Int)(using MkConsumer[IO]): IO[Map[TopicPartition, OffsetAndMetadata]] =
      runIncarnation(incarnation).handleErrorWith {
        case error: RetriableException if incarnation <= MaxRestarts =>
          incidentsRef.update(_ :+ Incident(incarnation, error.getClass.getSimpleName, error.getMessage)) *>
            supervise(incarnation + 1)
        case error => IO.raiseError(error)
      }

    private def runIncarnation(incarnation: Int)(using MkConsumer[IO]): IO[Map[TopicPartition, OffsetAndMetadata]] =
      KafkaConsumer.resource(consumerSettings(incarnation)).use { consumer =>
        for {
          _ <- consumer.subscribeTo(ReadingsTopic)
          end <- consumer.endOffsets(Partitions)
          _ <- consumer.records
            .evalMap(committable => apply(incarnation, committable.record).as(committable.offset))
            .groupWithin(100, 20.millis)
            .map(chunk => CommittableOffsetBatch.fromFoldable(chunk))
            .evalTap(_.commit)
            .scan(Map.empty[TopicPartition, Long])((done, batch) => done ++ batch.offsets.view.mapValues(_.offset))
            .takeThrough(done => end.exists((partition, offset) => done.getOrElse(partition, 0L) < offset))
            .compile
            .drain
            .timeout(30.seconds)
          committed <- consumer.committed(Partitions)
        } yield committed
      }

    private def apply(incarnation: Int, record: ConsumerRecord[String, (String, Long)]): IO[Unit] = {
      val (_, wattHours): (String, Long) = record.value
      reads.update(_ :+ Read(incarnation, new TopicPartition(record.topic, record.partition), record.offset)) *>
        totalsRef.update(totals => totals.updated(record.key, totals.getOrElse(record.key, 0L) + wattHours))
    }

    def incidents: List[Incident] = incidentsRef.get.unsafeRunSync()

    def totals: Map[String, Long] = totalsRef.get.unsafeRunSync()

    def readCount: Int = reads.get.unsafeRunSync().size

    def readWindow(incarnation: Int, partition: TopicPartition): Wave = {
      val offsets: Vector[Long] =
        reads.get.unsafeRunSync().filter(read => read.incarnation == incarnation && read.partition == partition).map(_.offset)
      assertEquals(offsets.distinct, offsets, "an incarnation reads each offset once")
      assertEquals((offsets.head until offsets.last + 1).toVector, offsets, "offsets are read in order without gaps")
      Wave(incarnation, offsets.head, offsets.last + 1)
    }
  }

  /** Delivers the readings below `cutoff`, then fails the next poll once all are committed. */
  final case class Outage(cutoff: Long, error: KafkaException)

  /**
   * Stands in for the brokers of a two-partition readings topic and the consumer group's
   * coordinator. Each consumer incarnation is assigned both partitions on its first poll,
   * starting from the offsets the group committed before, and follows the outage planned for
   * it, if any.
   */
  final class MeterCluster(outages: List[Outage]) {

    private val log: Map[TopicPartition, List[Reading]] =
      Readings.groupBy(reading => new TopicPartition(ReadingsTopic, reading.partition))

    private val consumers: CopyOnWriteArrayList[MockConsumer[Array[Byte], Array[Byte]]] = new CopyOnWriteArrayList()

    private val groupOffsets: AtomicReference[Map[TopicPartition, OffsetAndMetadata]] = new AtomicReference(Map.empty)

    private val outageSnapshots: CopyOnWriteArrayList[Map[TopicPartition, OffsetAndMetadata]] = new CopyOnWriteArrayList()

    private val settings: AtomicReference[Map[String, String]] = new AtomicReference(Map.empty)

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](consumerSettings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          settings.set(consumerSettings.properties)
          val outage: Option[Outage] = outages.lift(consumers.size)
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(OffsetResetStrategy.EARLIEST)
          consumer.updateBeginningOffsets(Partitions.map(_ -> Long.box(0L)).toMap.asJava)
          consumer.updateEndOffsets(log.map((partition, readings) => partition -> Long.box(readings.size.toLong)).asJava)
          consumer.schedulePollTask { () =>
            consumer.rebalance(Partitions.asJava)
            // Subscribing resets MockConsumer's offsets, so the group's offsets come with the assignment.
            consumer.commitSync(groupOffsets.get.asJava)
            log.foreach { (partition, readings) =>
              readings.zipWithIndex
                .filter((_, offset) => outage.forall(offset < _.cutoff))
                .foreach((reading, offset) => consumer.addRecord(toJava(partition, offset.toLong, reading)))
            }
            outage.foreach(planned => watch(consumer, planned))
          }
          consumers.add(consumer)
          consumer
        }
    }

    /** Runs on every poll until the group has committed all delivered readings, then fails it. */
    private def watch(consumer: MockConsumer[Array[Byte], Array[Byte]], outage: Outage): Unit =
      consumer.schedulePollTask { () =>
        val committed: Map[TopicPartition, OffsetAndMetadata] =
          consumer.committed(Partitions.asJava).asScala.toMap.filter((_, offset) => offset != null)
        val caughtUp: Boolean = log.forall { (partition, readings) =>
          committed.get(partition).exists(_.offset >= math.min(outage.cutoff, readings.size.toLong))
        }
        if (caughtUp) {
          groupOffsets.set(committed)
          outageSnapshots.add(committed)
          consumer.setPollException(outage.error)
        } else {
          watch(consumer, outage)
        }
      }

    private def toJava(partition: TopicPartition, offset: Long, reading: Reading): JavaConsumerRecord[Array[Byte], Array[Byte]] = {
      val key: Array[Byte] = reading.meter.getBytes(StandardCharsets.UTF_8)
      val value: Array[Byte] = s"${reading.id}=${reading.wattHours}".getBytes(StandardCharsets.UTF_8)
      new JavaConsumerRecord(
        partition.topic,
        partition.partition,
        offset,
        1_700_000_000_000L + offset,
        TimestampType.CREATE_TIME,
        key.length,
        value.length,
        key,
        value,
        new RecordHeaders(),
        Optional.empty[Integer]()
      )
    }

    def incarnations: Int = consumers.size

    def allClosed: Boolean = consumers.asScala.forall(_.closed)

    def offsetsAtOutage(index: Int): Map[TopicPartition, OffsetAndMetadata] = outageSnapshots.get(index)

    def lastSettings: Map[String, String] = settings.get
  }
}
