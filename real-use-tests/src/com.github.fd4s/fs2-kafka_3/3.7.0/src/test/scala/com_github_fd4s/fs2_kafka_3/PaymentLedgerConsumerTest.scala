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
import fs2.kafka.*
import fs2.kafka.consumer.MkConsumer
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord as JavaConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Consumes payment events through the fs2-kafka consumer: subscribing to a topic, decoding
 * values with header-selected deserializers, and committing offsets in batches.
 *
 * The Kafka client underneath is kafka-clients' `MockConsumer`, plugged in through the
 * `MkConsumer` extension point; it assigns the partitions and delivers the records on the
 * first poll, so the consumed log and the committed offsets are deterministic.
 */
class PaymentLedgerConsumerTest {

  import PaymentLedgerConsumerTest.*

  @Test
  def ledgerAppliesEveryPaymentAndCommitsPastTheLastOne(): Unit = {
    val log: List[LogEntry] = List(
      LogEntry(0, "alice", 1, """{"id":"p-1","account":"alice","amount":"12.50"}"""),
      LogEntry(1, "bob", 2, """{"id":"p-2","account":"bob","amountCents":4000}"""),
      LogEntry(0, "alice", 2, """{"id":"p-3","account":"alice","amountCents":-250}"""),
      LogEntry(1, "bob", 1, """{"id":"p-4","account":"bob","amount":"0.99"}"""),
      LogEntry(0, "carol", 2, """{"id":"p-5","account":"carol","amountCents":100}""")
    )
    val cluster: PaymentsCluster = new PaymentsCluster(log, Map.empty)
    given MkConsumer[IO] = cluster.mkConsumer

    val outcome: LedgerOutcome = runLedger(log.size).unsafeRunSync()

    assertEquals(Map("alice" -> 1000L, "bob" -> 4099L, "carol" -> 100L), outcome.ledger.balances,
      "version 1 amounts are decimal units, version 2 amounts are cents")
    assertEquals(List("p-1", "p-3", "p-5"), outcome.ledger.applied(Partition0))
    assertEquals(List("p-2", "p-4"), outcome.ledger.applied(Partition1))
    assertEquals(Nil, outcome.ledger.deadLetters)
    assertEquals(Map(Partition0 -> 3L, Partition1 -> 2L), outcome.committed,
      "the committed offset is the next offset to read on each partition")
    assertTrue(cluster.isClosed, "releasing the consumer resource closes the Kafka consumer")

    val config: Map[String, String] = cluster.lastSettings
    assertEquals("payment-ledger", config(ConsumerConfig.GROUP_ID_CONFIG))
    assertEquals("ledger-1", config(ConsumerConfig.CLIENT_ID_CONFIG))
    assertEquals("earliest", config(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))
    assertEquals("false", config(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG),
      "offsets are committed by the application, not by the Kafka client")
  }

  @Test
  def restartedLedgerResumesFromCommittedOffsetsAndParksPoisonRecords(): Unit = {
    val log: List[LogEntry] = List(
      LogEntry(0, "alice", 2, """{"id":"p-1","account":"alice","amountCents":500}"""),
      LogEntry(0, "alice", 2, """{"id":"p-2","account":"alice","amountCents":700}"""),
      LogEntry(0, "alice", 2, """{"id":"p-3","account":"alice",""""),
      LogEntry(1, "bob", 3, """{"id":"p-4","account":"bob","value":12}"""),
      LogEntry(1, "bob", 1, """{"id":"p-5","account":"bob","amount":"3.00"}"""),
      LogEntry(0, "alice", 1, """{"id":"p-6","account":"alice","amount":"1.10"}""")
    )
    val previouslyCommitted: Map[TopicPartition, Long] = Map(Partition0 -> 1L)
    val cluster: PaymentsCluster = new PaymentsCluster(log, previouslyCommitted)
    given MkConsumer[IO] = cluster.mkConsumer

    val outcome: LedgerOutcome = runLedger(log.size - 1).unsafeRunSync()

    assertEquals(Map("alice" -> 810L, "bob" -> 300L), outcome.ledger.balances,
      "p-1 was applied before the restart and is not read again")
    assertEquals(List("p-2", "p-6"), outcome.ledger.applied(Partition0))
    assertEquals(List("p-5"), outcome.ledger.applied(Partition1))
    assertEquals(List(Partition0 -> 2L, Partition1 -> 0L), outcome.ledger.deadLetters.sortBy(_._1.partition))
    assertEquals(Map(Partition0 -> 4L, Partition1 -> 2L), outcome.committed,
      "poison records are committed past, so they do not block their partition")
    assertTrue(cluster.isClosed)
  }
}

object PaymentLedgerConsumerTest {

  val PaymentsTopic: String = "payments"
  val SchemaVersionHeader: String = "schema-version"
  val Partition0: TopicPartition = new TopicPartition(PaymentsTopic, 0)
  val Partition1: TopicPartition = new TopicPartition(PaymentsTopic, 1)
  val Partitions: Set[TopicPartition] = Set(Partition0, Partition1)

  final case class Payment(id: String, account: String, amountCents: Long)

  object Payment {
    given Decoder[Payment] = deriveDecoder[Payment]
  }

  /** The first payment schema, which carried amounts as decimal strings in whole units. */
  final case class PaymentV1(id: String, account: String, amount: BigDecimal) {
    def toPayment: Payment = Payment(id, account, (amount * 100).toLongExact)
  }

  object PaymentV1 {
    given Decoder[PaymentV1] = deriveDecoder[PaymentV1]
  }

  def json[A: Decoder]: Deserializer[IO, A] =
    Deserializer.string[IO].flatMap(text => Deserializer.lift[IO, A](_ => IO.fromEither(decode[A](text))))

  /** Picks the payload schema from the record's `schema-version` header. */
  val paymentDeserializer: Deserializer[IO, Payment] =
    Deserializer.headers[IO, Payment] { headers =>
      headers(SchemaVersionHeader).map(_.as[String]) match {
        case Some("1") => json[PaymentV1].map(_.toPayment)
        case Some("2") => json[Payment]
        case other => Deserializer.failWith[IO, Payment](s"unsupported schema version $other")
      }
    }

  val consumerSettings: ConsumerSettings[IO, String, Either[Throwable, Payment]] =
    ConsumerSettings(Deserializer[IO, String], paymentDeserializer.attempt)
      .withBootstrapServers("localhost:9092")
      .withGroupId("payment-ledger")
      .withClientId("ledger-1")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withPollInterval(10.millis)

  final case class Ledger(
      balances: Map[String, Long],
      appliedIds: Map[TopicPartition, List[String]],
      deadLetters: List[(TopicPartition, Long)]
  ) {
    def applied(partition: TopicPartition): List[String] = appliedIds.getOrElse(partition, Nil)

    def record(partition: TopicPartition, offset: Long, value: Either[Throwable, Payment]): Ledger =
      value match {
        case Right(payment) =>
          Ledger(
            balances.updated(payment.account, balances.getOrElse(payment.account, 0L) + payment.amountCents),
            appliedIds.updated(partition, applied(partition) :+ payment.id),
            deadLetters
          )
        case Left(_) => copy(deadLetters = deadLetters :+ (partition -> offset))
      }
  }

  final case class LedgerOutcome(ledger: Ledger, committed: Map[TopicPartition, Long])

  /**
   * Runs the ledger service until it has handled `expected` records: each record is applied
   * to the ledger, then its offset is committed in batches, and the committed offsets are read
   * back from the consumer group before shutdown.
   */
  def runLedger(expected: Int)(using MkConsumer[IO]): IO[LedgerOutcome] =
    Ref.of[IO, Ledger](Ledger(Map.empty, Map.empty, Nil)).flatMap { ledger =>
      KafkaConsumer.resource(consumerSettings).use { consumer =>
        for {
          _ <- consumer.subscribeTo(PaymentsTopic)
          _ <- consumer.records
            .evalMap { committable =>
              val record: ConsumerRecord[String, Either[Throwable, Payment]] = committable.record
              val partition: TopicPartition = new TopicPartition(record.topic, record.partition)
              ledger.update(_.record(partition, record.offset, record.value)).as(committable.offset)
            }
            .take(expected.toLong)
            .through(commitBatchWithin[IO](expected, 10.seconds))
            .compile
            .drain
            .timeout(30.seconds)
          committed <- consumer.committed(Partitions)
          state <- ledger.get
        } yield LedgerOutcome(state, committed.view.mapValues(_.offset).toMap)
      }
    }

  final case class LogEntry(partition: Int, key: String, schemaVersion: Int, payload: String)

  /**
   * Stands in for the brokers of a two-partition payments topic: the consumer group is
   * assigned both partitions on its first poll and receives the whole log, starting from the
   * group's previously committed offsets where there are any.
   */
  final class PaymentsCluster(log: List[LogEntry], committedBefore: Map[TopicPartition, Long]) {

    private val current: AtomicReference[MockConsumer[Array[Byte], Array[Byte]]] = new AtomicReference()

    private val settings: AtomicReference[Map[String, String]] = new AtomicReference(Map.empty)

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](consumerSettings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          settings.set(consumerSettings.properties)
          val resetStrategy: OffsetResetStrategy = OffsetResetStrategy.valueOf(
            consumerSettings.properties(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG).toUpperCase
          )
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(resetStrategy)
          consumer.updateBeginningOffsets(Partitions.map(_ -> Long.box(0L)).toMap.asJava)
          consumer.schedulePollTask { () =>
            consumer.rebalance(Partitions.asJava)
            // Subscribing resets MockConsumer's offsets, so the group's offsets come with the assignment.
            consumer.commitSync(
              committedBefore.map((partition, offset) => partition -> new OffsetAndMetadata(offset)).asJava
            )
            log.groupBy(_.partition).foreach { (partition, entries) =>
              entries.zipWithIndex.foreach { (entry, offset) =>
                consumer.addRecord(toJava(partition, offset.toLong, entry))
              }
            }
          }
          current.set(consumer)
          consumer
        }
    }

    private def toJava(partition: Int, offset: Long, entry: LogEntry): JavaConsumerRecord[Array[Byte], Array[Byte]] = {
      val headers: RecordHeaders = new RecordHeaders()
      headers.add(new RecordHeader(SchemaVersionHeader, entry.schemaVersion.toString.getBytes(StandardCharsets.UTF_8)))
      val key: Array[Byte] = entry.key.getBytes(StandardCharsets.UTF_8)
      val value: Array[Byte] = entry.payload.getBytes(StandardCharsets.UTF_8)
      new JavaConsumerRecord(
        PaymentsTopic,
        partition,
        offset,
        1_700_000_000_000L + offset,
        TimestampType.CREATE_TIME,
        key.length,
        value.length,
        key,
        value,
        headers,
        Optional.empty[Integer]()
      )
    }

    def isClosed: Boolean = current.get.closed

    def lastSettings: Map[String, String] = settings.get
  }
}
