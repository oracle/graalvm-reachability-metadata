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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import scala.collection.immutable.SortedSet
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.kafka.*
import fs2.kafka.consumer.MkConsumer
import fs2.kafka.instances.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord as JavaConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Consumer group rebalancing and graceful shutdown: a ticket triage service processes each
 * assigned partition in its own lane from `partitionedStream`, follows its assignment through
 * `assignmentStream`, loses and regains a partition in rebalances, and stops with
 * `stopConsuming` before terminating the consumer.
 *
 * The Kafka client underneath is kafka-clients' `MockConsumer`, plugged in through the
 * `MkConsumer` extension point. Each rebalance is a poll task that waits until the service has
 * finished the previous phase, so the outcome does not depend on poll timing.
 */
class TicketTriageRebalanceTest {

  import TicketTriageRebalanceTest.*

  @Test
  def lanesFollowRebalancesAndStopGracefully(): Unit = {
    val desk: TriageDesk = new TriageDesk
    val cluster: SupportCluster = new SupportCluster(desk)
    given MkConsumer[IO] = cluster.mkConsumer

    val outcome: TriageOutcome = runTriage(desk).unsafeRunSync()

    assertEquals(
      List(
        SortedSet.empty[TopicPartition],
        SortedSet(Partition0, Partition1),
        SortedSet(Partition1),
        SortedSet(Partition0, Partition1)
      ),
      outcome.assignments,
      "the assignment stream reports the initial assignment and every rebalance"
    )
    assertEquals(
      List(
        Triaged(0, 0, "T-100", "billing"),
        Triaged(0, 1, "T-101", "outage"),
        Triaged(0, 2, "T-104", "billing")
      ),
      desk.triaged.asScala.toList.filter(_.partition == 0),
      "the regained partition resumes from its committed offset without replaying tickets"
    )
    assertEquals(
      List(Triaged(1, 0, "T-102", "account"), Triaged(1, 1, "T-103", "outage")),
      desk.triaged.asScala.toList.filter(_.partition == 1)
    )

    val lanes: List[ClosedLane] = desk.closedLanes.asScala.toList
    assertEquals(ClosedLane(List(0 -> 0L, 0 -> 1L)), lanes.head, "revoking a partition ends its lane")
    assertEquals(
      Set(ClosedLane(List(1 -> 0L, 1 -> 1L)), ClosedLane(List(0 -> 2L))),
      lanes.tail.toSet,
      "stopConsuming ends the kept lane and the lane of the regained partition"
    )
    assertEquals(Map(Partition0 -> 3L, Partition1 -> 2L), outcome.committed)
    assertEquals(Nil, outcome.afterStop, "a stopped consumer hands out no further records")
    assertTrue(cluster.isClosed, "releasing the consumer resource closes the Kafka consumer")
  }
}

object TicketTriageRebalanceTest {

  val TicketsTopic: String = "support-tickets"
  val Partition0: TopicPartition = new TopicPartition(TicketsTopic, 0)
  val Partition1: TopicPartition = new TopicPartition(TicketsTopic, 1)
  val Partitions: Set[TopicPartition] = Set(Partition0, Partition1)

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings(Deserializer[IO, String], Deserializer[IO, String])
      .withBootstrapServers("localhost:9092")
      .withGroupId("ticket-triage")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withPollInterval(10.millis)

  final case class Triaged(partition: Int, offset: Long, ticket: String, queue: String)

  /** The partition and offset of every ticket a lane processed before it ended. */
  final case class ClosedLane(records: List[(Int, Long)])

  final case class TriageOutcome(
      assignments: List[SortedSet[TopicPartition]],
      committed: Map[TopicPartition, Long],
      afterStop: List[String]
  )

  /** The service's observable state, which the cluster also reads to pace its rebalances. */
  final class TriageDesk {

    val triaged: ConcurrentLinkedQueue[Triaged] = new ConcurrentLinkedQueue()

    val closedLanes: ConcurrentLinkedQueue[ClosedLane] = new ConcurrentLinkedQueue()

    val assignments: ConcurrentLinkedQueue[SortedSet[TopicPartition]] = new ConcurrentLinkedQueue()

    /** Tickets that were triaged and whose offsets were committed. */
    val completed: AtomicInteger = new AtomicInteger()

    def triage(record: ConsumerRecord[String, String]): IO[Unit] =
      IO(triaged.add(Triaged(record.partition, record.offset, record.key, record.value))).void
  }

  /** Processes one partition's tickets, committing each offset once its ticket is triaged. */
  def lane(desk: TriageDesk)(records: Stream[IO, CommittableConsumerRecord[IO, String, String]]): Stream[IO, Unit] =
    Stream.eval(Ref.of[IO, List[(Int, Long)]](Nil)).flatMap { seen =>
      records
        .evalMap { committable =>
          val record: ConsumerRecord[String, String] = committable.record
          desk.triage(record) >>
            committable.offset.commit >>
            seen.update(_ :+ (record.partition -> record.offset)) >>
            IO(desk.completed.incrementAndGet()).void
        }
        .onFinalize(seen.get.flatMap(processed => IO(desk.closedLanes.add(ClosedLane(processed))).void))
    }

  def awaitCondition(description: String, condition: => Boolean, remaining: Int = 1500): IO[Unit] =
    IO(condition).flatMap { met =>
      if (met) IO.unit
      else if (remaining == 0) IO.raiseError(new AssertionError(s"timed out waiting until $description"))
      else IO.sleep(10.millis) >> awaitCondition(description, condition, remaining - 1)
    }

  /**
   * Runs the triage service until all five tickets are done, then stops consuming, checks that
   * the stopped consumer emits nothing more, and terminates it before the resource is released.
   */
  def runTriage(desk: TriageDesk)(using MkConsumer[IO]): IO[TriageOutcome] =
    KafkaConsumer.resource(consumerSettings).use { consumer =>
      for {
        _ <- consumer.subscribeTo(TicketsTopic)
        watcher <- consumer.assignmentStream.evalMap(assigned => IO(desk.assignments.add(assigned))).compile.drain.start
        consuming <- consumer.partitionedStream.map(lane(desk)).parJoinUnbounded.compile.drain.start
        _ <- awaitCondition("five tickets are triaged", desk.completed.get == 5)
        _ <- awaitCondition("four assignments are reported", desk.assignments.size == 4)
        _ <- consumer.stopConsuming
        _ <- consuming.joinWithNever.timeout(15.seconds)
        afterStop <- consumer.stream.map(_.record.key).compile.toList.timeout(15.seconds)
        committed <- consumer.committed(Partitions)
        _ <- watcher.cancel
        _ <- consumer.terminate
        _ <- consumer.awaitTermination.timeout(15.seconds)
      } yield TriageOutcome(
        desk.assignments.asScala.toList,
        committed.collect { case (partition, offset) if offset != null => partition -> offset.offset },
        afterStop
      )
    }

  /**
   * Stands in for the group coordinator and brokers of a two-partition tickets topic. The group
   * first receives both partitions, then loses partition 0 to another member, then regains it;
   * each step waits until the desk has finished the previous one.
   */
  final class SupportCluster(desk: TriageDesk) {

    private val current: AtomicReference[MockConsumer[Array[Byte], Array[Byte]]] = new AtomicReference()

    private final case class Phase(ready: () => Boolean, run: MockConsumer[Array[Byte], Array[Byte]] => Unit)

    private val phases: List[Phase] = List(
      Phase(
        () => !desk.assignments.isEmpty,
        consumer => {
          consumer.rebalance(Partitions.asJava)
          consumer.addRecord(ticket(0, 0, "T-100", "billing"))
          consumer.addRecord(ticket(0, 1, "T-101", "outage"))
          consumer.addRecord(ticket(1, 0, "T-102", "account"))
        }
      ),
      Phase(
        () => desk.completed.get == 3,
        consumer => {
          consumer.rebalance(List(Partition1).asJava)
          consumer.addRecord(ticket(1, 1, "T-103", "outage"))
        }
      ),
      Phase(
        () => desk.completed.get == 4 && !desk.closedLanes.isEmpty,
        consumer => {
          consumer.rebalance(Partitions.toList.asJava)
          // The partition's log still holds every ticket; the group's committed offset decides.
          consumer.addRecord(ticket(0, 0, "T-100", "billing"))
          consumer.addRecord(ticket(0, 1, "T-101", "outage"))
          consumer.addRecord(ticket(0, 2, "T-104", "billing"))
        }
      )
    )

    /** Schedules the next phase for the coming poll; a phase that is not ready waits a poll. */
    private def schedule(consumer: MockConsumer[Array[Byte], Array[Byte]], remaining: List[Phase]): Unit =
      remaining match {
        case Nil => ()
        case phase :: rest =>
          consumer.schedulePollTask { () =>
            if (phase.ready()) {
              phase.run(consumer)
              schedule(consumer, rest)
            } else schedule(consumer, remaining)
          }
      }

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](settings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(OffsetResetStrategy.EARLIEST)
          consumer.updateBeginningOffsets(Partitions.map(_ -> Long.box(0L)).toMap.asJava)
          schedule(consumer, phases)
          current.set(consumer)
          consumer
        }
    }

    private def ticket(partition: Int, offset: Long, id: String, queue: String): JavaConsumerRecord[Array[Byte], Array[Byte]] = {
      val key: Array[Byte] = id.getBytes(StandardCharsets.UTF_8)
      val value: Array[Byte] = queue.getBytes(StandardCharsets.UTF_8)
      new JavaConsumerRecord(
        TicketsTopic,
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
