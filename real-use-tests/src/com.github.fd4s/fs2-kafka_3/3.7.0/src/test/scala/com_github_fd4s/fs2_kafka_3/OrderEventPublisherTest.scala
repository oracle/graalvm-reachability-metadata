/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Map as JavaMap
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.kafka.*
import fs2.kafka.producer.MkProducer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.syntax.*
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.producer.ProducerRecord as JavaProducerRecord
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Publishes order events through the fs2-kafka producer, both as a stream pipe and as a
 * scoped producer resource, with JSON values, typed headers and key-based partitioning.
 *
 * The Kafka client underneath is kafka-clients' `MockProducer`, plugged in through the
 * `MkProducer` extension point, so the test reads back exactly what fs2-kafka handed to Kafka.
 */
class OrderEventPublisherTest {

  import OrderEventPublisherTest.*

  @Test
  def streamOfOrdersIsPublishedThroughThePipeInOrder(): Unit = {
    val broker: RecordingBroker = new RecordingBroker()
    given MkProducer[IO] = broker.mkProducer

    val orders: List[Order] = List(
      Order("o-1", "alice", "SKU-RED-MUG", 2, BigDecimal("9.90")),
      Order("o-2", "bob", "SKU-TEA-POT", 1, BigDecimal("34.50")),
      Order("o-3", "alice", "SKU-SPOON", 6, BigDecimal("1.25"))
    )

    val published: List[(ProducerRecord[String, Order], RecordMetadata)] = Stream
      .emits(orders)
      .map(order => ProducerRecords.one(ProducerRecord(OrdersTopic, order.customer, order)))
      .through(KafkaProducer.pipe(producerSettings))
      .flatMap(result => Stream.chunk(result))
      .compile
      .toList
      .unsafeRunSync()

    assertEquals(orders, published.map(_._1.value))
    assertTrue(published.forall(_._2.topic == OrdersTopic))
    val offsetsByPartition: Map[Int, List[Long]] =
      published.groupMap(_._2.partition)(_._2.offset)
    offsetsByPartition.values.foreach(offsets => assertEquals(offsets.indices.toList, offsets.map(_.toInt)))

    val sent: List[JavaProducerRecord[Array[Byte], Array[Byte]]] = broker.history
    assertEquals(List("alice", "bob", "alice"), sent.map(record => utf8(record.key)))
    assertEquals(orders, sent.map(record => decode[Order](utf8(record.value)).toOption.get))
    val alicePartitions: Set[Int] =
      published.filter(_._1.key == "alice").map(_._2.partition).toSet
    assertEquals(1, alicePartitions.size, "one customer's orders stay on one partition")
    assertTrue(broker.isClosed, "the pipe closes its producer when the stream completes")

    val config: Map[String, String] = broker.lastSettings
    assertEquals("localhost:9092", config(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
    assertEquals("all", config(ProducerConfig.ACKS_CONFIG))
    assertEquals("order-publisher", config(ProducerConfig.CLIENT_ID_CONFIG))
    assertEquals("true", config(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG))
  }

  @Test
  def scopedProducerRoutesCancellationsWithHeadersToAnExplicitPartition(): Unit = {
    val broker: RecordingBroker = new RecordingBroker()
    given MkProducer[IO] = broker.mkProducer

    val cancellation: Order = Order("o-7", "carol", "SKU-KETTLE", 1, BigDecimal("49.00"))
    val headers: Headers = Headers(
      Header("event-type", "OrderCancelled"),
      Header("attempt", 3)
    )

    val (single, batch): (RecordMetadata, List[RecordMetadata]) = KafkaProducer
      .resource(producerSettings)
      .use { producer =>
        for {
          single <- producer.produceOne_(
            ProducerRecord(OrdersTopic, cancellation.customer, cancellation)
              .withPartition(2)
              .withHeaders(headers)
          ).flatten
          batch <- producer.produce(
            ProducerRecords(
              List(
                ProducerRecord(AuditTopic, "carol", cancellation.copy(id = "audit-1")),
                ProducerRecord(AuditTopic, "carol", cancellation.copy(id = "audit-2"))
              )
            )
          ).flatten
        } yield (single, batch.toList.map(_._2))
      }
      .unsafeRunSync()

    assertEquals(OrdersTopic, single.topic)
    assertEquals(2, single.partition)
    assertEquals(0L, single.offset)
    assertEquals(List(AuditTopic, AuditTopic), batch.map(_.topic))
    assertEquals(List(0L, 1L), batch.map(_.offset))
    assertTrue(broker.isClosed, "releasing the resource closes the producer")

    val sent: JavaProducerRecord[Array[Byte], Array[Byte]] = broker.history.head
    assertEquals(Integer.valueOf(2), sent.partition)
    assertEquals("carol", utf8(sent.key))
    assertEquals(Right(cancellation), decode[Order](utf8(sent.value)))
    val sentHeaders: Headers = Headers.fromIterable(
      sent.headers.toArray.toList.map(header => Header(header.key, header.value))
    )
    assertEquals("OrderCancelled", sentHeaders("event-type").get.as[String])
    assertEquals(Right(3), sentHeaders("attempt").get.attemptAs[Int])
    assertEquals(List("audit-1", "audit-2"),
      broker.history.tail.map(record => decode[Order](utf8(record.value)).toOption.get.id))
  }
}

object OrderEventPublisherTest {

  val OrdersTopic: String = "orders"
  val AuditTopic: String = "orders-audit"

  final case class Order(id: String, customer: String, sku: String, quantity: Int, unitPrice: BigDecimal)

  object Order {
    given Codec[Order] = deriveCodec[Order]
  }

  /** Encodes orders as compact JSON, the way the downstream services read them. */
  val orderSerializer: Serializer[IO, Order] =
    Serializer[IO, String].contramap[Order](_.asJson.noSpaces)

  val producerSettings: ProducerSettings[IO, String, Order] =
    ProducerSettings(Serializer[IO, String], orderSerializer)
      .withBootstrapServers("localhost:9092")
      .withClientId("order-publisher")
      .withAcks(Acks.All)
      .withEnableIdempotence(true)

  def utf8(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

  /** Keeps all orders of one customer on one partition, so they are consumed in order. */
  final class CustomerPartitioner extends Partitioner {

    override def partition(
        topic: String,
        key: Any,
        keyBytes: Array[Byte],
        value: Any,
        valueBytes: Array[Byte],
        cluster: Cluster
    ): Int =
      Math.floorMod(utf8(keyBytes).hashCode, cluster.partitionCountForTopic(topic))

    override def configure(configs: JavaMap[String, ?]): Unit = ()

    override def close(): Unit = ()
  }

  /**
   * Stands in for the Kafka cluster: every producer fs2-kafka creates is a `MockProducer`
   * that acknowledges sends immediately on a cluster where each topic has three partitions.
   */
  final class RecordingBroker {

    private val node: Node = new Node(0, "localhost", 9092)

    private val cluster: Cluster = new Cluster(
      "test-cluster",
      List(node).asJava,
      (for {
        topic <- List(OrdersTopic, AuditTopic)
        partition <- 0 until 3
      } yield new PartitionInfo(topic, partition, node, Array(node), Array(node))).asJava,
      Collections.emptySet[String](),
      Collections.emptySet[String]()
    )

    private val current: AtomicReference[MockProducer[Array[Byte], Array[Byte]]] =
      new AtomicReference()

    private val settings: AtomicReference[Map[String, String]] = new AtomicReference(Map.empty)

    val mkProducer: MkProducer[IO] = new MkProducer[IO] {
      def apply[G[_]](producerSettings: ProducerSettings[G, ?, ?]): IO[Producer[Array[Byte], Array[Byte]]] =
        IO {
          settings.set(producerSettings.properties)
          val producer: MockProducer[Array[Byte], Array[Byte]] =
            new MockProducer(
              cluster,
              true,
              new CustomerPartitioner(),
              new ByteArraySerializer(),
              new ByteArraySerializer()
            )
          current.set(producer)
          producer
        }
    }

    def history: List[JavaProducerRecord[Array[Byte], Array[Byte]]] = current.get.history.asScala.toList

    def isClosed: Boolean = current.get.closed

    def lastSettings: Map[String, String] = settings.get
  }
}
