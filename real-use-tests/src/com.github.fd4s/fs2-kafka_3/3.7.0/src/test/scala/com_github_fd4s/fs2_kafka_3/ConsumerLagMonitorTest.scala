/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.kafka.*
import fs2.kafka.admin.MkAdminClient
import fs2.kafka.consumer.MkConsumer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.MockAdminClient
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.TopicPartitionInfo
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Monitors a consumer group's lag by combining the group's committed offsets, read through
 * `KafkaAdminClient.listConsumerGroupOffsets`, with the log start and end offsets read
 * through a group-less fs2-kafka consumer, and flags lagging and stalled partitions.
 *
 * The admin client underneath is kafka-clients' `MockAdminClient`, plugged in through
 * `MkAdminClient`, and the consumer is a `MockConsumer`, plugged in through `MkConsumer`;
 * together they stand in for one cluster whose offsets the test advances between samples.
 */
class ConsumerLagMonitorTest {

  import ConsumerLagMonitorTest.*

  @Test
  def monitorReportsLagAlertsAndStalledPartitionsAcrossSamples(): Unit = {
    val cluster: WarehouseCluster = new WarehouseCluster
    given MkAdminClient[IO] = cluster.mkAdminClient
    given MkConsumer[IO] = cluster.mkConsumer

    cluster.publish(
      committed = Map(Shipments0 -> 40L, Shipments1 -> 95L, Returns0 -> 10L, Billing0 -> 7L),
      logStart = Map(Shipments0 -> 0L, Shipments1 -> 0L, Shipments2 -> 5L, Returns0 -> 12L),
      logEnd = Map(Shipments0 -> 100L, Shipments1 -> 100L, Shipments2 -> 30L, Returns0 -> 12L)
    )

    val (groupTopics, first, second): (Set[String], LagReport, LagReport) = LagMonitor
      .resource(WarehouseGroup, WatchedTopics, alertThreshold = 20L)
      .use { monitor =>
        for {
          groupTopics <- monitor.groupTopics
          first <- monitor.sample.rethrow
          _ <- IO(
            cluster.publish(
              committed = Map(Shipments0 -> 100L, Shipments1 -> 95L, Shipments2 -> 30L, Returns0 -> 12L),
              logStart = Map(Shipments0 -> 0L, Shipments1 -> 0L, Shipments2 -> 5L, Returns0 -> 12L),
              logEnd = Map(Shipments0 -> 120L, Shipments1 -> 160L, Shipments2 -> 30L, Returns0 -> 12L)
            )
          )
          second <- monitor.sample.rethrow
        } yield (groupTopics, first, second)
      }
      .unsafeRunSync()

    assertEquals(Set(ShipmentsTopic, ReturnsTopic, BillingTopic), groupTopics,
      "the group's committed offsets name every topic it consumes, watched or not")

    assertEquals(Set(Shipments0, Shipments1, Shipments2, Returns0), first.partitions.keySet,
      "only the partitions of watched topics are sampled")
    assertEquals(PartitionLag(Some(40L), 0L, 100L, 60L), first.partitions(Shipments0))
    assertEquals(PartitionLag(Some(95L), 0L, 100L, 5L), first.partitions(Shipments1))
    assertEquals(PartitionLag(None, 5L, 30L, 25L), first.partitions(Shipments2),
      "a partition without a commit is measured from its log start offset")
    assertEquals(PartitionLag(Some(10L), 12L, 12L, 0L), first.partitions(Returns0),
      "a commit behind the retained log is measured from the log start offset")
    assertEquals(Map(ShipmentsTopic -> 90L, ReturnsTopic -> 0L), first.topicLag)
    assertEquals(List(Shipments0, Shipments2), first.alerts)
    assertEquals(Set.empty, first.stalled, "the first sample has nothing to compare against")

    assertEquals(PartitionLag(Some(100L), 0L, 120L, 20L), second.partitions(Shipments0))
    assertEquals(PartitionLag(Some(95L), 0L, 160L, 65L), second.partitions(Shipments1))
    assertEquals(PartitionLag(Some(30L), 5L, 30L, 0L), second.partitions(Shipments2))
    assertEquals(PartitionLag(Some(12L), 12L, 12L, 0L), second.partitions(Returns0))
    assertEquals(Map(ShipmentsTopic -> 85L, ReturnsTopic -> 0L), second.topicLag)
    assertEquals(List(Shipments1, Shipments0), second.alerts, "alerts are ordered by lag, largest first")
    assertEquals(Set(Shipments1), second.stalled,
      "a partition whose log grew while its commit stood still is stalled")

    assertTrue(cluster.consumerClosed, "releasing the monitor closes the Kafka consumer")
    assertEquals("lag-monitor", cluster.adminSettings(AdminClientConfig.CLIENT_ID_CONFIG))
    assertEquals("localhost:9092", cluster.adminSettings(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG))
    assertEquals("lag-monitor-offsets", cluster.consumerSettings(ConsumerConfig.CLIENT_ID_CONFIG))
    assertFalse(cluster.consumerSettings.contains(ConsumerConfig.GROUP_ID_CONFIG),
      "the monitor reads offsets without joining the group it watches")
  }

  @Test
  def failedOffsetLookupSkipsOneSampleAndKeepsTheBaseline(): Unit = {
    val cluster: WarehouseCluster = new WarehouseCluster
    given MkAdminClient[IO] = cluster.mkAdminClient
    given MkConsumer[IO] = cluster.mkConsumer

    cluster.publish(
      committed = Map(Shipments0 -> 10L, Shipments1 -> 10L, Shipments2 -> 10L, Returns0 -> 3L),
      logStart = Map(Shipments0 -> 0L, Shipments1 -> 0L, Shipments2 -> 0L, Returns0 -> 0L),
      logEnd = Map(Shipments0 -> 10L, Shipments1 -> 10L, Shipments2 -> 10L, Returns0 -> 3L)
    )

    val (first, failed, recovered): (LagReport, Either[Throwable, LagReport], LagReport) = LagMonitor
      .resource(WarehouseGroup, WatchedTopics, alertThreshold = 50L)
      .use { monitor =>
        for {
          first <- monitor.sample.rethrow
          _ <- IO(
            cluster.publish(
              committed = Map(Shipments0 -> 30L, Shipments1 -> 10L, Shipments2 -> 10L, Returns0 -> 3L),
              logStart = Map(Shipments0 -> 0L, Shipments1 -> 0L, Shipments2 -> 0L, Returns0 -> 0L),
              logEnd = Map(Shipments0 -> 30L, Shipments1 -> 80L, Shipments2 -> 10L, Returns0 -> 3L)
            )
          )
          _ <- IO(cluster.failNextOffsetLookup(new TimeoutException("Timeout fetching end offsets")))
          failed <- monitor.sample
          recovered <- monitor.sample.rethrow
        } yield (first, failed, recovered)
      }
      .unsafeRunSync()

    assertEquals(0L, first.partitions.values.map(_.lag).sum, "every watched partition starts caught up")
    val error: Throwable = failed.swap.getOrElse(fail("the sample during the broker timeout must fail"))
    assertInstanceOf(classOf[TimeoutException], error, "the Kafka client's own exception surfaces")
    assertEquals("Timeout fetching end offsets", error.getMessage)

    assertEquals(PartitionLag(Some(10L), 0L, 80L, 70L), recovered.partitions(Shipments1))
    assertEquals(List(Shipments1), recovered.alerts)
    assertEquals(Set(Shipments1), recovered.stalled,
      "the failed sample does not replace the baseline the next sample compares against")
    assertTrue(cluster.consumerClosed)
  }

  @Test
  def watchListNamingAMissingTopicFailsTheSample(): Unit = {
    val cluster: WarehouseCluster = new WarehouseCluster
    given MkAdminClient[IO] = cluster.mkAdminClient
    given MkConsumer[IO] = cluster.mkConsumer

    cluster.publish(
      committed = Map(Shipments0 -> 1L),
      logStart = Map(Shipments0 -> 0L, Shipments1 -> 0L, Shipments2 -> 0L, Returns0 -> 0L),
      logEnd = Map(Shipments0 -> 1L, Shipments1 -> 0L, Shipments2 -> 0L, Returns0 -> 0L)
    )

    val outcome: Either[Throwable, LagReport] = LagMonitor
      .resource(WarehouseGroup, List(ShipmentsTopic, "refunds"), alertThreshold = 1L)
      .use(_.sample)
      .unsafeRunSync()

    val error: Throwable = outcome.swap.getOrElse(fail("a watch list with an unknown topic must fail"))
    assertInstanceOf(classOf[UnknownTopicOrPartitionException], error,
      "describing the unknown topic fails with the broker's error")
    assertTrue(cluster.consumerClosed)
  }
}

object ConsumerLagMonitorTest {

  val WarehouseGroup: String = "warehouse-sync"
  val ShipmentsTopic: String = "shipments"
  val ReturnsTopic: String = "returns"
  val BillingTopic: String = "billing"
  val WatchedTopics: List[String] = List(ShipmentsTopic, ReturnsTopic)

  val Shipments0: TopicPartition = new TopicPartition(ShipmentsTopic, 0)
  val Shipments1: TopicPartition = new TopicPartition(ShipmentsTopic, 1)
  val Shipments2: TopicPartition = new TopicPartition(ShipmentsTopic, 2)
  val Returns0: TopicPartition = new TopicPartition(ReturnsTopic, 0)
  val Billing0: TopicPartition = new TopicPartition(BillingTopic, 0)

  val adminSettings: AdminClientSettings =
    AdminClientSettings("localhost:9092").withClientId("lag-monitor")

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings(Deserializer[IO, String], Deserializer[IO, String])
      .withBootstrapServers("localhost:9092")
      .withClientId("lag-monitor-offsets")
      .withEnableAutoCommit(false)

  /** How far one partition's group position trails the end of its log. */
  final case class PartitionLag(committed: Option[Long], logStart: Long, logEnd: Long, lag: Long)

  /** One sample of the watched partitions, compared against the previous good sample. */
  final case class LagReport(
      partitions: Map[TopicPartition, PartitionLag],
      alerts: List[TopicPartition],
      stalled: Set[TopicPartition]
  ) {
    def topicLag: Map[String, Long] =
      partitions.toList.groupMapReduce((partition, _) => partition.topic)((_, lag) => lag.lag)(_ + _)
  }

  /**
   * Samples the lag of one consumer group on a fixed watch list of topics, keeping the last
   * good sample to detect partitions whose consumers stopped committing.
   */
  final class LagMonitor(
      admin: KafkaAdminClient[IO],
      consumer: KafkaConsumer[IO, String, String],
      groupId: String,
      topics: List[String],
      alertThreshold: Long,
      baseline: Ref[IO, Option[Map[TopicPartition, PartitionLag]]]
  ) {

    /** Every topic in the cluster the group has committed offsets for, watched or not. */
    def groupTopics: IO[Set[String]] =
      for {
        names <- admin.listTopics.names
        descriptions <- admin.describeTopics(names.toList)
        committed <- admin
          .listConsumerGroupOffsets(groupId)
          .forPartitions(partitionsOf(descriptions).toList)
          .partitionsToOffsetAndMetadata
      } yield committed.keySet.map(_.topic)

    /** Takes one sample; a failed sample leaves the baseline untouched. */
    def sample: IO[Either[Throwable, LagReport]] =
      measure.attempt.flatMap {
        case Right(partitions) =>
          baseline.getAndSet(Some(partitions)).map(previous => Right(report(partitions, previous)))
        case Left(error) =>
          IO.pure(Left(error))
      }

    private def measure: IO[Map[TopicPartition, PartitionLag]] =
      for {
        descriptions <- admin.describeTopics(topics)
        partitions = partitionsOf(descriptions)
        committed <- admin
          .listConsumerGroupOffsets(groupId)
          .forPartitions(partitions.toList)
          .partitionsToOffsetAndMetadata
        logEnd <- consumer.endOffsets(partitions, 5.seconds)
        logStart <- consumer.beginningOffsets(partitions, 5.seconds)
      } yield partitions.map { partition =>
        val commit: Option[Long] = committed.get(partition).map((offset: OffsetAndMetadata) => offset.offset)
        val position: Long = math.max(commit.getOrElse(logStart(partition)), logStart(partition))
        partition -> PartitionLag(commit, logStart(partition), logEnd(partition), logEnd(partition) - position)
      }.toMap

    private def partitionsOf(descriptions: Map[String, TopicDescription]): Set[TopicPartition] =
      descriptions.values.flatMap { description =>
        description.partitions.asScala.map(info => new TopicPartition(description.name, info.partition))
      }.toSet

    private def report(
        partitions: Map[TopicPartition, PartitionLag],
        previous: Option[Map[TopicPartition, PartitionLag]]
    ): LagReport = {
      val alerts: List[TopicPartition] = partitions.toList
        .filter((_, lag) => lag.lag >= alertThreshold)
        .sortBy((partition, lag) => (-lag.lag, partition.topic, partition.partition))
        .map((partition, _) => partition)
      val stalled: Set[TopicPartition] = previous.toList.flatMap { before =>
        partitions.collect {
          case (partition, now) if before.get(partition).exists { earlier =>
                now.lag > 0 && now.committed == earlier.committed && now.logEnd > earlier.logEnd
              } =>
            partition
        }
      }.toSet
      LagReport(partitions, alerts, stalled)
    }
  }

  object LagMonitor {

    def resource(groupId: String, topics: List[String], alertThreshold: Long)(using
        MkAdminClient[IO],
        MkConsumer[IO]
    ): Resource[IO, LagMonitor] =
      for {
        admin <- KafkaAdminClient.resource[IO](adminSettings)
        consumer <- KafkaConsumer.resource(consumerSettings)
        baseline <- Resource.eval(Ref.of[IO, Option[Map[TopicPartition, PartitionLag]]](None))
      } yield new LagMonitor(admin, consumer, groupId, topics, alertThreshold, baseline)
  }

  /**
   * Stands in for the warehouse cluster: the admin view knows the topics and the group's
   * committed offsets, and the consumer view knows each partition's log start and end offsets.
   */
  final class WarehouseCluster {

    private val admin: MockAdminClient = MockAdminClient.create().numBrokers(1).build()

    private val broker: Node = admin.brokers.get(0)

    private val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(OffsetResetStrategy.NONE)

    private val adminProperties: AtomicReference[Map[String, String]] = new AtomicReference(Map.empty)

    private val consumerProperties: AtomicReference[Map[String, String]] = new AtomicReference(Map.empty)

    seed(ShipmentsTopic, 3)
    seed(ReturnsTopic, 1)
    seed(BillingTopic, 1)

    private def seed(topic: String, partitions: Int): Unit = {
      admin.addTopic(
        false,
        topic,
        (0 until partitions).map { partition =>
          new TopicPartitionInfo(partition, broker, List(broker).asJava, List(broker).asJava)
        }.asJava,
        Map.empty[String, String].asJava
      )
      consumer.updatePartitions(
        topic,
        (0 until partitions).map { partition =>
          new PartitionInfo(topic, partition, broker, Array(broker), Array(broker))
        }.asJava
      )
    }

    /** Sets the group's committed offsets and the retained log bounds of each partition. */
    def publish(
        committed: Map[TopicPartition, Long],
        logStart: Map[TopicPartition, Long],
        logEnd: Map[TopicPartition, Long]
    ): Unit = {
      admin.updateConsumerGroupOffsets(committed.view.mapValues(Long.box).toMap.asJava)
      consumer.updateBeginningOffsets(logStart.view.mapValues(Long.box).toMap.asJava)
      consumer.updateEndOffsets(logEnd.view.mapValues(Long.box).toMap.asJava)
    }

    /** Makes the next offset lookup fail as a broker timeout would. */
    def failNextOffsetLookup(error: TimeoutException): Unit = consumer.setOffsetsException(error)

    val mkAdminClient: MkAdminClient[IO] = new MkAdminClient[IO] {
      def apply(settings: AdminClientSettings): IO[AdminClient] =
        IO {
          adminProperties.set(settings.properties)
          admin
        }
    }

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](settings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          consumerProperties.set(settings.properties)
          consumer
        }
    }

    def consumerClosed: Boolean = consumer.closed

    def adminSettings: Map[String, String] = adminProperties.get

    def consumerSettings: Map[String, String] = consumerProperties.get
  }
}
