/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.kafka.*
import fs2.kafka.producer.MkProducer
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.producer.RoundRobinPartitioner
import org.apache.kafka.clients.producer.ProducerRecord as JavaProducerRecord
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.errors.NotEnoughReplicasException
import org.apache.kafka.common.errors.RecordTooLargeException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Handles producer delivery failures through the two-phase `produce` of the fs2-kafka producer:
 * every record is sent before any acknowledgement is awaited, broker rejections surface when an
 * acknowledgement is awaited, retriable rejections are resent, and serializer failures fail the
 * send itself so the record never reaches the broker.
 *
 * The Kafka producer underneath is kafka-clients' `MockProducer`, plugged in through the
 * `MkProducer` extension point; it acknowledges or rejects each send by a fixed script keyed by
 * record key and attempt, so the deliveries are deterministic.
 */
class PriceFeedDeliveryTest {

  import PriceFeedDeliveryTest.*

  @Test
  def publisherRetriesTransientRejectionsAndReportsEveryOtherOutcome(): Unit = {
    val broker: ScriptedBroker = new ScriptedBroker(
      Map(
        "sku-2" -> List(Some(new NotEnoughReplicasException("only 1 in-sync replica")), None),
        "sku-3" -> List(Some(new RecordTooLargeException("record exceeds max.message.bytes")))
      )
    )
    given MkProducer[IO] = broker.mkProducer

    val updates: List[PriceUpdate] = List(
      PriceUpdate("sku-1", 1299L, "EUR"),
      PriceUpdate("sku-2", 450L, "EUR"),
      PriceUpdate("sku-3", 99900L, "EUR"),
      PriceUpdate("sku-4", -5L, "EUR"),
      PriceUpdate("sku-5", 7L, "USD")
    )

    val deliveries: List[Delivery] = KafkaProducer
      .resource(producerSettings)
      .use(producer => new PriceFeedPublisher(producer, maxAttempts = 3).publish(updates))
      .unsafeRunSync()

    assertEquals(
      List(
        Delivery.Delivered("sku-1", 0L, 1),
        Delivery.Delivered("sku-2", 4L, 2),
        Delivery.Rejected("sku-3", "RecordTooLargeException", 1),
        Delivery.Invalid("sku-4", "price of sku-4 must be positive, was -5"),
        Delivery.Delivered("sku-5", 3L, 1)
      ),
      deliveries,
      "outcomes are reported in update order, whatever order the acknowledgements resolve in"
    )

    assertEquals(List("sku-1", "sku-2", "sku-3", "sku-5", "sku-2"), broker.sentKeys,
      "every valid update is sent before any acknowledgement is awaited, then sku-2 is resent")
    assertEquals("sku-5|7|USD", broker.sentValue(3),
      "the serializer wrote the value bytes the broker received")
    assertTrue(broker.isClosed, "releasing the resource closes the Kafka producer")

    val config: Map[String, String] = broker.lastSettings
    assertEquals("price-feed", config(ProducerConfig.CLIENT_ID_CONFIG))
    assertEquals("all", config(ProducerConfig.ACKS_CONFIG))
    assertEquals("0", config(ProducerConfig.RETRIES_CONFIG),
      "the application retries itself, so the Kafka client does not")
    assertEquals("false", config(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG))
  }

  @Test
  def publisherGivesUpOnAPersistentlyUnavailablePartition(): Unit = {
    val outage: Option[RuntimeException] = Some(new NotEnoughReplicasException("partition offline"))
    val broker: ScriptedBroker = new ScriptedBroker(Map("sku-9" -> List(outage, outage, outage)))
    given MkProducer[IO] = broker.mkProducer

    val deliveries: List[Delivery] = KafkaProducer
      .resource(producerSettings)
      .use { producer =>
        new PriceFeedPublisher(producer, maxAttempts = 2).publish(List(PriceUpdate("sku-9", 100L, "GBP")))
      }
      .unsafeRunSync()

    assertEquals(List(Delivery.Rejected("sku-9", "NotEnoughReplicasException", 2)), deliveries,
      "a retriable rejection still fails the update once the attempts are used up")
    assertEquals(List("sku-9", "sku-9"), broker.sentKeys)
  }
}

object PriceFeedDeliveryTest {

  val PriceTopic: String = "price-updates"

  final case class PriceUpdate(sku: String, priceCents: Long, currency: String)

  /** What the publisher reports for one update. */
  sealed trait Delivery

  object Delivery {
    final case class Delivered(sku: String, offset: Long, attempts: Int) extends Delivery
    final case class Rejected(sku: String, error: String, attempts: Int) extends Delivery
    final case class Invalid(sku: String, reason: String) extends Delivery
  }

  /** Writes updates as `sku|cents|currency` and refuses prices that are not positive. */
  val priceSerializer: Serializer[IO, PriceUpdate] =
    Serializer.lift[IO, PriceUpdate] { update =>
      if (update.priceCents <= 0)
        IO.raiseError(
          new IllegalArgumentException(s"price of ${update.sku} must be positive, was ${update.priceCents}")
        )
      else
        IO.pure(s"${update.sku}|${update.priceCents}|${update.currency}".getBytes(StandardCharsets.UTF_8))
    }

  val producerSettings: ProducerSettings[IO, String, PriceUpdate] =
    ProducerSettings(Serializer[IO, String], priceSerializer)
      .withBootstrapServers("localhost:9092")
      .withClientId("price-feed")
      .withAcks(Acks.All)
      .withRetries(0)
      .withEnableIdempotence(false)

  /**
   * Publishes price updates: the first phase sends every update, the second awaits each
   * acknowledgement, resending an update the broker rejected with a retriable error.
   */
  final class PriceFeedPublisher(producer: KafkaProducer[IO, String, PriceUpdate], maxAttempts: Int) {

    def publish(updates: List[PriceUpdate]): IO[List[Delivery]] =
      updates.traverse(update => send(update, 1)).flatMap(_.sequence)

    /** Sends one attempt; the returned effect awaits its acknowledgement. */
    private def send(update: PriceUpdate, attempt: Int): IO[IO[Delivery]] =
      producer.produceOne_(ProducerRecord(PriceTopic, update.sku, update)).attempt.map {
        case Left(error) =>
          IO.pure(Delivery.Invalid(update.sku, error.getMessage))
        case Right(acknowledgement) =>
          acknowledgement.attempt.flatMap {
            case Right(metadata) =>
              IO.pure(Delivery.Delivered(update.sku, metadata.offset, attempt))
            case Left(_: RetriableException) if attempt < maxAttempts =>
              send(update, attempt + 1).flatten
            case Left(error) =>
              IO.pure(Delivery.Rejected(update.sku, error.getClass.getSimpleName, attempt))
          }
      }
  }

  def utf8(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

  /**
   * Stands in for the Kafka cluster: a single-partition topic whose producer answers each send
   * with the scripted outcome for that key and attempt, acknowledging when none is scripted.
   */
  final class ScriptedBroker(script: Map[String, List[Option[RuntimeException]]]) {

    private val node: Node = new Node(0, "localhost", 9092)

    private val cluster: Cluster = new Cluster(
      "price-cluster",
      List(node).asJava,
      List(new PartitionInfo(PriceTopic, 0, node, Array(node), Array(node))).asJava,
      Collections.emptySet[String](),
      Collections.emptySet[String]()
    )

    private val current: AtomicReference[ScriptedProducer] = new AtomicReference()

    private val settings: AtomicReference[Map[String, String]] = new AtomicReference(Map.empty)

    val mkProducer: MkProducer[IO] = new MkProducer[IO] {
      def apply[G[_]](producerSettings: ProducerSettings[G, ?, ?]): IO[Producer[Array[Byte], Array[Byte]]] =
        IO {
          settings.set(producerSettings.properties)
          val producer: ScriptedProducer = new ScriptedProducer(cluster, script)
          current.set(producer)
          producer
        }
    }

    def sentKeys: List[String] = current.get.history.asScala.toList.map(record => utf8(record.key))

    def sentValue(index: Int): String = utf8(current.get.history.get(index).value)

    def isClosed: Boolean = current.get.closed

    def lastSettings: Map[String, String] = settings.get
  }

  /** A producer that does not auto-complete sends but resolves each one as the script says. */
  final class ScriptedProducer(cluster: Cluster, script: Map[String, List[Option[RuntimeException]]])
      extends MockProducer[Array[Byte], Array[Byte]](
        cluster,
        false,
        new RoundRobinPartitioner(),
        new ByteArraySerializer(),
        new ByteArraySerializer()
      ) {

    private val attempts: mutable.Map[String, Int] = mutable.Map.empty

    override def send(
        record: JavaProducerRecord[Array[Byte], Array[Byte]],
        callback: Callback
    ): Future[RecordMetadata] =
      this.synchronized {
        val future: Future[RecordMetadata] = super.send(record, callback)
        val key: String = utf8(record.key)
        val attempt: Int = attempts.getOrElse(key, 0)
        attempts.update(key, attempt + 1)
        script.getOrElse(key, Nil).lift(attempt).flatten match {
          case Some(error) => errorNext(error)
          case None => completeNext()
        }
        future
      }
  }
}
