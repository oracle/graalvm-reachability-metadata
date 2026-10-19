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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Temporal
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.kafka.*
import fs2.kafka.consumer.MkConsumer
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetCommitCallback
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.RetriableCommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord as JavaConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.CoordinatorNotAvailableException
import org.apache.kafka.common.errors.NotCoordinatorException
import org.apache.kafka.common.errors.RebalanceInProgressException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Offset commit recovery: committing a `CommittableOffsetBatch` goes through the consumer's
 * `CommitRecovery` policy (the default retrying policy, no recovery, or an application-defined
 * one) and its `commitTimeout`, surfacing `CommitRecoveryException` and `CommitTimeoutException`.
 *
 * The job accrues loyalty points from purchases and commits everything it accrued as one batch.
 * The Kafka client underneath is kafka-clients' `MockConsumer`, plugged in through `MkConsumer`,
 * standing in for a group coordinator that answers each commit request as scripted: accept it,
 * reject it with a Kafka error, or never answer it.
 */
class LoyaltyPointsCommitRecoveryTest {

  import LoyaltyPointsCommitRecoveryTest.*

  @Test
  def defaultRecoveryRetriesTransientCoordinatorRejections(): Unit = {
    val coordinator: GroupCoordinator = new GroupCoordinator(List(
      Reject(new RetriableCommitFailedException("coordinator moved to broker 2")),
      Reject(new RebalanceInProgressException("group loyalty-accrual is rebalancing"))
    ))

    val outcome: AccrualOutcome = runAccrual(coordinator, accrualSettings(CommitRecovery.Default)).unsafeRunSync()

    assertEquals(Right(()), outcome.commit, "the default policy retries both transient rejections")
    assertEquals(
      List(
        Attempt(ExpectedOffsets, "RetriableCommitFailedException"),
        Attempt(ExpectedOffsets, "RebalanceInProgressException"),
        Attempt(ExpectedOffsets, "accepted")
      ),
      coordinator.attempts,
      "every retry commits the same batch of offsets"
    )
    assertEquals(ExpectedOffsets, outcome.batch)
    assertEquals(ExpectedOffsets, outcome.committed)
    assertEquals(ExpectedPoints, outcome.points)
    assertTrue(coordinator.isClosed)
  }

  @Test
  def defaultRecoverySurfacesNonRetriableCommitFailures(): Unit = {
    val coordinator: GroupCoordinator =
      new GroupCoordinator(List(Reject(new CommitFailedException("member was removed from the group"))))

    val outcome: AccrualOutcome = runAccrual(coordinator, accrualSettings(CommitRecovery.Default)).unsafeRunSync()

    outcome.commit match {
      case Left(error: CommitFailedException) => assertEquals("member was removed from the group", error.getMessage)
      case other => fail(s"expected the commit to fail with the coordinator's error, got $other")
    }
    assertEquals(List(Attempt(ExpectedOffsets, "CommitFailedException")), coordinator.attempts,
      "a non-retriable rejection is not retried")
    assertEquals(Map.empty, outcome.committed, "the accrued purchases stay uncommitted and are redelivered")
    assertEquals(ExpectedPoints, outcome.points)
    assertTrue(coordinator.isClosed)
  }

  @Test
  def withoutRecoveryATransientRejectionFailsTheCommit(): Unit = {
    val coordinator: GroupCoordinator =
      new GroupCoordinator(List(Reject(new RetriableCommitFailedException("coordinator moved to broker 3"))))

    val outcome: AccrualOutcome = runAccrual(coordinator, accrualSettings(CommitRecovery.None)).unsafeRunSync()

    outcome.commit match {
      case Left(error: RetriableCommitFailedException) => assertEquals("coordinator moved to broker 3", error.getMessage)
      case other => fail(s"expected the transient rejection to surface, got $other")
    }
    assertEquals(List(Attempt(ExpectedOffsets, "RetriableCommitFailedException")), coordinator.attempts)
    assertEquals(Map.empty, outcome.committed)
  }

  @Test
  def applicationPolicyRetriesEveryRetriableErrorWithinItsBudget(): Unit = {
    val coordinator: GroupCoordinator = new GroupCoordinator(List(
      Reject(new CoordinatorNotAvailableException("coordinator is loading")),
      Reject(new NotCoordinatorException("broker 1 is not the coordinator"))
    ))

    val outcome: AccrualOutcome =
      runAccrual(coordinator, accrualSettings(new BoundedCommitRetry(MaxRetries, 5.millis))).unsafeRunSync()

    assertEquals(Right(()), outcome.commit,
      "the application policy retries coordinator errors the default policy would not")
    assertEquals(
      List(
        Attempt(ExpectedOffsets, "CoordinatorNotAvailableException"),
        Attempt(ExpectedOffsets, "NotCoordinatorException"),
        Attempt(ExpectedOffsets, "accepted")
      ),
      coordinator.attempts
    )
    assertEquals(ExpectedOffsets, outcome.committed)
  }

  @Test
  def applicationPolicyGivesUpWithACommitRecoveryException(): Unit = {
    val coordinator: GroupCoordinator =
      new GroupCoordinator(List.fill(MaxRetries + 1)(Reject(new CoordinatorNotAvailableException("coordinator is loading"))))

    val outcome: AccrualOutcome =
      runAccrual(coordinator, accrualSettings(new BoundedCommitRetry(MaxRetries, 5.millis))).unsafeRunSync()

    outcome.commit match {
      case Left(error: CommitRecoveryException) =>
        assertEquals(
          "offset commit is still failing after 3 attempts for offsets: purchases-0 -> (4, accrual-run-7), " +
            "purchases-1 -> (2, accrual-run-7); last exception was: " +
            "org.apache.kafka.common.errors.CoordinatorNotAvailableException: coordinator is loading",
          error.getMessage
        )
      case other => fail(s"expected the retry budget to run out, got $other")
    }
    assertEquals(List.fill(MaxRetries + 1)(Attempt(ExpectedOffsets, "CoordinatorNotAvailableException")),
      coordinator.attempts, "the first attempt plus one per retry")
    assertEquals(Map.empty, outcome.committed)
    assertTrue(coordinator.isClosed)
  }

  @Test
  def unansweredCommitFailsAfterTheCommitTimeout(): Unit = {
    val coordinator: GroupCoordinator = new GroupCoordinator(List(Unanswered))

    val outcome: AccrualOutcome =
      runAccrual(coordinator, accrualSettings(CommitRecovery.Default).withCommitTimeout(200.millis)).unsafeRunSync()

    outcome.commit match {
      case Left(error: CommitTimeoutException) =>
        assertEquals(
          "offset commit timeout after 200 milliseconds for offsets: purchases-0 -> (4, accrual-run-7), " +
            "purchases-1 -> (2, accrual-run-7)",
          error.getMessage
        )
      case other => fail(s"expected the commit to time out, got $other")
    }
    assertEquals(List(Attempt(ExpectedOffsets, "unanswered")), coordinator.attempts,
      "the default policy does not retry a timed-out commit")
    assertEquals(Map.empty, outcome.committed)
    assertTrue(coordinator.isClosed)
  }
}

object LoyaltyPointsCommitRecoveryTest {

  val PurchasesTopic: String = "purchases"
  val Partition0: TopicPartition = new TopicPartition(PurchasesTopic, 0)
  val Partition1: TopicPartition = new TopicPartition(PurchasesTopic, 1)
  val Partitions: Set[TopicPartition] = Set(Partition0, Partition1)
  val RunMetadata: String = "accrual-run-7"
  val MaxRetries: Int = 3

  /** A purchase keyed by customer, with its amount in cents as the record value. */
  final case class Purchase(partition: Int, customer: String, cents: Long)

  val Purchases: List[Purchase] = List(
    Purchase(0, "ada", 1_250), Purchase(0, "grace", 4_000), Purchase(1, "linus", 999),
    Purchase(0, "ada", 310), Purchase(1, "grace", 15_500), Purchase(0, "linus", 2_001)
  )

  /** One loyalty point per started euro. */
  val ExpectedPoints: Map[String, Long] = Map("ada" -> 17L, "grace" -> 195L, "linus" -> 31L)

  val ExpectedOffsets: Map[TopicPartition, OffsetAndMetadata] = Map(
    Partition0 -> new OffsetAndMetadata(4L, RunMetadata),
    Partition1 -> new OffsetAndMetadata(2L, RunMetadata)
  )

  def accrualSettings(recovery: CommitRecovery): ConsumerSettings[IO, String, Long] =
    ConsumerSettings(Deserializer[IO, String], Deserializer[IO, String].map(_.toLong))
      .withBootstrapServers("localhost:9092")
      .withGroupId("loyalty-accrual")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withPollInterval(10.millis)
      .withRecordMetadata(_ => RunMetadata)
      .withCommitRecovery(recovery)

  final case class AccrualOutcome(
    batch: Map[TopicPartition, OffsetAndMetadata],
    commit: Either[Throwable, Unit],
    committed: Map[TopicPartition, OffsetAndMetadata],
    points: Map[String, Long]
  )

  /**
   * Accrues points for every purchase, then commits all consumed offsets as one batch and
   * reports the commit result and what the group has committed afterwards.
   */
  def runAccrual(coordinator: GroupCoordinator, settings: ConsumerSettings[IO, String, Long]): IO[AccrualOutcome] = {
    given MkConsumer[IO] = coordinator.mkConsumer
    Ref.of[IO, Map[String, Long]](Map.empty).flatMap { points =>
      KafkaConsumer.resource(settings).use { consumer =>
        for {
          _ <- consumer.subscribeTo(PurchasesTopic)
          offsets <- consumer.records
            .evalMap { committable =>
              val earned: Long = (committable.record.value + 99) / 100
              points.update(all => all.updated(committable.record.key, all.getOrElse(committable.record.key, 0L) + earned))
                .as(committable.offset)
            }
            .take(Purchases.size.toLong)
            .compile
            .toList
            .timeout(30.seconds)
          batch = CommittableOffsetBatch.fromFoldable(offsets)
          commit <- batch.commit.attempt.timeout(30.seconds)
          committed <- consumer.committed(Partitions)
          accrued <- points.get
        } yield AccrualOutcome(batch.offsets, commit, committed.filter((_, offset) => offset != null), accrued)
      }
    }
  }

  /**
   * Retries any retriable Kafka error up to `maxRetries` times after a jittered pause, then gives
   * up with a [[CommitRecoveryException]]; other errors fail the commit at once.
   */
  final class BoundedCommitRetry(maxRetries: Int, pause: FiniteDuration) extends CommitRecovery {

    override def recoverCommitWith[F[_]](
      offsets: Map[TopicPartition, OffsetAndMetadata],
      commit: F[Unit]
    )(implicit F: Temporal[F], jitter: Jitter[F]): Throwable => F[Unit] = {
      def retry(attempt: Int): Throwable => F[Unit] = {
        case retriable: RetriableException if attempt <= maxRetries =>
          jitter.withJitter(pause.toMillis.toDouble).flatMap(millis => F.sleep(millis.millis)) >>
            commit.handleErrorWith(retry(attempt + 1))
        case retriable: RetriableException =>
          F.raiseError(CommitRecoveryException(maxRetries, retriable, offsets))
        case other =>
          F.raiseError(other)
      }
      retry(1)
    }

    override def toString: String = s"BoundedCommitRetry($maxRetries)"
  }

  /** How the coordinator answers one commit request. */
  sealed trait Response
  final case class Reject(error: Exception) extends Response
  case object Unanswered extends Response

  final case class Attempt(offsets: Map[TopicPartition, OffsetAndMetadata], answer: String)

  /**
   * Stands in for the brokers of a two-partition purchases topic and the group's coordinator:
   * the group is assigned both partitions with all purchases on the first poll, and commit
   * requests are answered by the scripted responses in order, then accepted.
   */
  final class GroupCoordinator(responses: List[Response]) {

    private val pending: ConcurrentLinkedQueue[Response] = new ConcurrentLinkedQueue(responses.asJava)

    private val log: CopyOnWriteArrayList[Attempt] = new CopyOnWriteArrayList()

    private val current: AtomicReference[MockConsumer[Array[Byte], Array[Byte]]] = new AtomicReference()

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](settings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new CoordinatedConsumer()
          consumer.updateBeginningOffsets(Partitions.map(_ -> Long.box(0L)).toMap.asJava)
          consumer.schedulePollTask { () =>
            consumer.rebalance(Partitions.asJava)
            Purchases.groupBy(_.partition).foreach { (partition, purchases) =>
              purchases.zipWithIndex.foreach((purchase, offset) => consumer.addRecord(toJava(purchase, offset.toLong)))
            }
          }
          current.set(consumer)
          consumer
        }
    }

    /** Answers asynchronous commits as the coordinator script says. */
    private final class CoordinatedConsumer extends MockConsumer[Array[Byte], Array[Byte]](OffsetResetStrategy.EARLIEST) {
      override def commitAsync(
        offsets: java.util.Map[TopicPartition, OffsetAndMetadata],
        callback: OffsetCommitCallback
      ): Unit = this.synchronized {
        val requested: Map[TopicPartition, OffsetAndMetadata] = offsets.asScala.toMap
        Option(pending.poll()) match {
          case Some(Reject(error)) =>
            log.add(Attempt(requested, error.getClass.getSimpleName))
            callback.onComplete(offsets, error)
          case Some(Unanswered) =>
            log.add(Attempt(requested, "unanswered"))
          case None =>
            log.add(Attempt(requested, "accepted"))
            super.commitAsync(offsets, callback)
        }
      }
    }

    private def toJava(purchase: Purchase, offset: Long): JavaConsumerRecord[Array[Byte], Array[Byte]] = {
      val key: Array[Byte] = purchase.customer.getBytes(StandardCharsets.UTF_8)
      val value: Array[Byte] = purchase.cents.toString.getBytes(StandardCharsets.UTF_8)
      new JavaConsumerRecord(
        PurchasesTopic,
        purchase.partition,
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

    def attempts: List[Attempt] = log.asScala.toList

    def isClosed: Boolean = current.get.closed
  }
}
