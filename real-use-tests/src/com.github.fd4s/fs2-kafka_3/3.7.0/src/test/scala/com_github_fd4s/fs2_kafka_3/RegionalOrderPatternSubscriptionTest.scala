/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.kafka.*
import fs2.kafka.consumer.MkConsumer
import org.apache.kafka.clients.consumer.Consumer
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
 * Subscribes the fs2-kafka consumer to topics by regular expression with `subscribe(Regex)`,
 * so a regional order aggregator reads every `orders.<region>` topic, picks up a region topic
 * created while it runs, ignores look-alike topics, and gives its partitions up on `unsubscribe`.
 *
 * The Kafka client underneath is kafka-clients' `MockConsumer`, plugged in through the
 * `MkConsumer` extension point. It knows the cluster's topics and matches the subscribed
 * pattern against them itself; the new region topic arrives in a poll task that waits until
 * every earlier order is aggregated, so the outcome does not depend on poll timing.
 */
class RegionalOrderPatternSubscriptionTest {

  import RegionalOrderPatternSubscriptionTest.*

  @Test
  def aggregatorFollowsEveryRegionTopicTheSubscriptionPatternMatches(): Unit = {
    val board: RegionBoard = new RegionBoard
    val cluster: OrdersCluster = new OrdersCluster(board)
    given MkConsumer[IO] = cluster.mkConsumer

    val outcome: AggregationOutcome = runAggregator(board).unsafeRunSync()

    assertEquals(
      Set(new TopicPartition(EuTopic, 0), new TopicPartition(EuTopic, 1), new TopicPartition(UsTopic, 0)),
      outcome.subscribedAssignment.toSet,
      "only the region topics match; the archive, retry and payments topics do not"
    )
    assertEquals(
      outcome.subscribedAssignment.toSet + new TopicPartition(ApTopic, 0),
      outcome.assignmentAfterNewRegion.toSet,
      "a region topic created later joins the pattern subscription"
    )
    assertEquals(Map("eu" -> 4200L, "us" -> 1999L, "ap" -> 3500L), board.totals,
      "orders are summed per region, which is the topic name after the prefix")
    assertEquals(
      Set("EU-1", "EU-2", "EU-3", "US-1", "AP-1", "AP-2"),
      board.orders.asScala.map(_.id).toSet
    )
    assertEquals(List("EU-1", "EU-3"), board.orders.asScala.toList.filter(_.partition == 1).map(_.id),
      "records of one partition arrive in offset order")
    assertTrue(outcome.assignmentAfterUnsubscribe.left.exists(_.isInstanceOf[NotSubscribedException]),
      "after unsubscribing, the consumer reports that it holds no subscription")
    assertEquals(1, outcome.apPartitions.size, "the new region topic is visible through the consumer")
    assertTrue(cluster.isClosed, "releasing the consumer resource closes the Kafka consumer")
  }
}

object RegionalOrderPatternSubscriptionTest {

  val RegionTopics: Regex = """orders\.[a-z]{2}""".r

  val EuTopic: String = "orders.eu"
  val UsTopic: String = "orders.us"
  val ApTopic: String = "orders.ap"

  /** Topics of the cluster at start-up, with their partition counts. */
  val InitialTopics: Map[String, Int] = Map(
    EuTopic -> 2,
    UsTopic -> 1,
    "orders-archive" -> 1,
    "orders.eu.retry" -> 1,
    "payments.eu" -> 1
  )

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings(Deserializer[IO, String], Deserializer[IO, String])
      .withBootstrapServers("localhost:9092")
      .withGroupId("regional-order-aggregator")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withPollInterval(10.millis)

  final case class Order(region: String, partition: Int, id: String, cents: Long)

  /** The aggregator's observable state, which the cluster also reads to pace the new topic. */
  final class RegionBoard {

    val orders: ConcurrentLinkedQueue[Order] = new ConcurrentLinkedQueue()

    def record(record: ConsumerRecord[String, String]): IO[Unit] =
      IO(orders.add(Order(record.topic.stripPrefix("orders."), record.partition, record.key, record.value.toLong))).void

    def totals: Map[String, Long] =
      orders.asScala.toList.groupMapReduce(_.region)(_.cents)(_ + _)
  }

  final case class AggregationOutcome(
      subscribedAssignment: Set[TopicPartition],
      assignmentAfterNewRegion: Set[TopicPartition],
      assignmentAfterUnsubscribe: Either[Throwable, Set[TopicPartition]],
      apPartitions: List[PartitionInfo]
  )

  /**
   * Subscribes by pattern, aggregates the six orders of all matching topics, reads the
   * assignment the pattern produced, then unsubscribes.
   */
  def runAggregator(board: RegionBoard)(using MkConsumer[IO]): IO[AggregationOutcome] =
    KafkaConsumer.resource(consumerSettings).use { consumer =>
      for {
        _ <- consumer.subscribe(RegionTopics)
        subscribed <- consumer.assignment
        _ <- consumer.records
          .evalMap(committable => board.record(committable.record).as(committable.offset))
          .take(6)
          .through(commitBatchWithin[IO](6, 10.seconds))
          .compile
          .drain
          .timeout(30.seconds)
        afterNewRegion <- consumer.assignment
        apPartitions <- consumer.partitionsFor(ApTopic)
        _ <- consumer.unsubscribe
        afterUnsubscribe <- consumer.assignment.attempt
      } yield AggregationOutcome(subscribed, afterNewRegion, afterUnsubscribe, apPartitions)
    }

  /**
   * Stands in for the brokers and group coordinator of a cluster with two region topics and
   * look-alike topics. Once the first four orders are aggregated, an operator creates the
   * `orders.ap` topic and the coordinator adds its partition to the group's assignment.
   */
  final class OrdersCluster(board: RegionBoard) {

    private val current: AtomicReference[MockConsumer[Array[Byte], Array[Byte]]] = new AtomicReference()

    private val broker: Node = new Node(0, "localhost", 9092)

    private def addTopic(consumer: MockConsumer[Array[Byte], Array[Byte]], topic: String, partitions: Int): Unit = {
      consumer.updatePartitions(
        topic,
        (0 until partitions).map(partition => new PartitionInfo(topic, partition, broker, Array(broker), Array(broker))).asJava
      )
      consumer.updateBeginningOffsets((0 until partitions).map(new TopicPartition(topic, _) -> Long.box(0L)).toMap.asJava)
    }

    private final case class Phase(ready: () => Boolean, run: MockConsumer[Array[Byte], Array[Byte]] => Unit)

    private val phases: List[Phase] = List(
      Phase(
        () => true,
        consumer => {
          val assigned: Set[TopicPartition] = consumer.assignment.asScala.toSet
          // Every topic carries orders, so a partition outside the pattern would surface here.
          List(
            order(EuTopic, 0, 0, "EU-2", 1500),
            order(EuTopic, 1, 0, "EU-1", 2000),
            order(EuTopic, 1, 1, "EU-3", 700),
            order(UsTopic, 0, 0, "US-1", 1999),
            order("orders-archive", 0, 0, "ARCHIVED-1", 100000),
            order("orders.eu.retry", 0, 0, "RETRY-1", 100000)
          ).filter(record => assigned.contains(new TopicPartition(record.topic, record.partition)))
            .foreach(consumer.addRecord)
        }
      ),
      Phase(
        () => board.orders.size == 4,
        consumer => {
          addTopic(consumer, ApTopic, 1)
          consumer.rebalance((consumer.assignment.asScala.toSet + new TopicPartition(ApTopic, 0)).asJava)
          consumer.addRecord(order(ApTopic, 0, 0, "AP-1", 2500))
          consumer.addRecord(order(ApTopic, 0, 1, "AP-2", 1000))
        }
      )
    )

    /** Schedules the next phase for the coming poll; a phase that is not ready waits a poll. */
    private def schedule(consumer: MockConsumer[Array[Byte], Array[Byte]], remaining: List[Phase]): Unit =
      remaining match {
        case Nil => ()
        case phase :: rest =>
          consumer.schedulePollTask { () =>
            if (phase.ready() && !consumer.assignment.isEmpty) {
              phase.run(consumer)
              schedule(consumer, rest)
            } else schedule(consumer, remaining)
          }
      }

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](settings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(OffsetResetStrategy.EARLIEST)
          InitialTopics.foreach((topic, partitions) => addTopic(consumer, topic, partitions))
          schedule(consumer, phases)
          current.set(consumer)
          consumer
        }
    }

    private def order(topic: String, partition: Int, offset: Long, id: String, cents: Long): JavaConsumerRecord[Array[Byte], Array[Byte]] = {
      val key: Array[Byte] = id.getBytes(StandardCharsets.UTF_8)
      val value: Array[Byte] = cents.toString.getBytes(StandardCharsets.UTF_8)
      new JavaConsumerRecord(
        topic,
        partition,
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

    def isClosed: Boolean = current.get.closed
  }
}
