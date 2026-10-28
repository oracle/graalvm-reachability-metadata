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

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import fs2.kafka.*
import fs2.kafka.consumer.MkConsumer
import fs2.kafka.producer.MkProducer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.syntax.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord as JavaConsumerRecord
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord as JavaProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Runs an exactly-once consume-transform-produce pipeline through the fs2-kafka
 * transactional producer, which publishes each order's shipments together with the order's
 * consumed offset in one Kafka transaction, and aborts the transaction when a shipment fails.
 *
 * The Kafka clients underneath are kafka-clients' `MockConsumer` and `MockProducer`, plugged
 * in through `MkConsumer` and `MkProducer`; the offsets a committed transaction carries are
 * fed back to the next consumer of the group, the way the brokers do it.
 */
class ShipmentFulfilmentTransactionTest {

  import ShipmentFulfilmentTransactionTest.*

  @Test
  def eachOrderCommitsItsShipmentsAndOffsetInOneTransaction(): Unit = {
    val orders: List[Order] = List(
      Order("o-1", "alice", List(Line("SKU-MUG", 2, "AMS"), Line("SKU-POT", 1, "BER"), Line("SKU-CUP", 4, "AMS"))),
      Order("o-2", "bob", List(Line("SKU-SPOON", 6, "AMS"))),
      Order("o-3", "carol", List(Line("SKU-KETTLE", 1, "BER")))
    )
    val cluster: FulfilmentCluster = new FulfilmentCluster(orders)
    given MkConsumer[IO] = cluster.mkConsumer
    given MkProducer[IO] = cluster.mkProducer

    val outcome: Either[Throwable, Unit] = runFulfilment(AllDocks, orders.size).unsafeRunSync()

    assertEquals(Right(()), outcome)
    assertEquals(
      List(
        LabelledShipment("o-1", "AMS", 4, List(Line("SKU-MUG", 2, "AMS"), Line("SKU-CUP", 4, "AMS"))),
        LabelledShipment("o-1", "BER", 7, List(Line("SKU-POT", 1, "BER"))),
        LabelledShipment("o-2", "AMS", 4, List(Line("SKU-SPOON", 6, "AMS"))),
        LabelledShipment("o-3", "BER", 7, List(Line("SKU-KETTLE", 1, "BER")))
      ),
      cluster.publishedShipments
    )
    assertEquals(List("o-1", "o-1", "o-2", "o-3"), cluster.publishedKeys)

    val producer: MockProducer[Array[Byte], Array[Byte]] = cluster.producers.head
    assertEquals(1, cluster.producers.size)
    assertEquals(3L, producer.commitCount, "one transaction per consumed order")
    assertTrue(producer.transactionCommitted)
    assertFalse(producer.transactionAborted)
    assertEquals(List(1L, 2L, 3L), cluster.transactionOffsets(producer),
      "every transaction carries the next offset to read after its order")
    assertTrue(producer.closed, "releasing the transactional producer closes it")
    assertTrue(cluster.consumers.head.closed())

    val producerConfig: Map[String, String] = cluster.producerProperties.head
    assertEquals("fulfilment-1", producerConfig(ProducerConfig.TRANSACTIONAL_ID_CONFIG))
    assertEquals("true", producerConfig(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG))
    assertEquals("all", producerConfig(ProducerConfig.ACKS_CONFIG))
    assertEquals("30000", producerConfig(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG))
    val consumerConfig: Map[String, String] = cluster.consumerProperties.head
    assertEquals("read_committed", consumerConfig(ConsumerConfig.ISOLATION_LEVEL_CONFIG),
      "the pipeline only reads orders from committed transactions")
    assertEquals("false", consumerConfig(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG),
      "offsets are committed by the producer's transactions, not by the consumer")
  }

  @Test
  def failedShipmentAbortsItsTransactionAndRestartResumesFromTheLastCommit(): Unit = {
    val orders: List[Order] = List(
      Order("o-1", "alice", List(Line("SKU-MUG", 1, "AMS"))),
      Order("o-2", "bob", List(Line("SKU-POT", 3, "AMS"), Line("SKU-LAMP", 1, "LIS"))),
      Order("o-3", "carol", List(Line("SKU-CUP", 2, "BER")))
    )
    val cluster: FulfilmentCluster = new FulfilmentCluster(orders)
    given MkConsumer[IO] = cluster.mkConsumer
    given MkProducer[IO] = cluster.mkProducer

    val firstRun: Either[Throwable, Unit] = runFulfilment(AllDocks - "LIS", orders.size).unsafeRunSync()

    assertEquals(Left(UnknownWarehouse("LIS")), firstRun)
    val firstProducer: MockProducer[Array[Byte], Array[Byte]] = cluster.producers.head
    assertEquals(1L, firstProducer.commitCount)
    assertTrue(firstProducer.transactionAborted,
      "the o-2 shipment from AMS was already sent when the LIS shipment failed")
    assertEquals(List("o-1"), cluster.publishedShipments.map(_.orderId),
      "records of an aborted transaction never become visible")
    assertEquals(List(1L), cluster.transactionOffsets(firstProducer))
    assertTrue(firstProducer.closed)

    val secondRun: Either[Throwable, Unit] = runFulfilment(AllDocks, orders.size - 1).unsafeRunSync()

    assertEquals(Right(()), secondRun)
    assertEquals(2, cluster.producers.size)
    assertEquals(
      List(
        LabelledShipment("o-1", "AMS", 4, List(Line("SKU-MUG", 1, "AMS"))),
        LabelledShipment("o-2", "AMS", 4, List(Line("SKU-POT", 3, "AMS"))),
        LabelledShipment("o-2", "LIS", 2, List(Line("SKU-LAMP", 1, "LIS"))),
        LabelledShipment("o-3", "BER", 7, List(Line("SKU-CUP", 2, "BER")))
      ),
      cluster.publishedShipments,
      "the restarted pipeline resumes at o-2, so every shipment is published exactly once"
    )
    assertEquals(List(2L, 3L), cluster.transactionOffsets(cluster.producers(1)))
    assertTrue(cluster.producers(1).transactionCommitted)
  }
}

object ShipmentFulfilmentTransactionTest {

  val OrdersTopic: String = "orders"
  val ShipmentsTopic: String = "shipments"
  val GroupId: String = "fulfilment"
  val OrdersPartition: TopicPartition = new TopicPartition(OrdersTopic, 0)

  /** Loading docks of the warehouses that can currently ship. */
  val AllDocks: Map[String, Int] = Map("AMS" -> 4, "BER" -> 7, "LIS" -> 2)

  final case class Line(sku: String, quantity: Int, warehouse: String)

  object Line {
    given Codec[Line] = deriveCodec[Line]
  }

  final case class Order(id: String, customer: String, lines: List[Line])

  object Order {
    given Codec[Order] = deriveCodec[Order]
  }

  final case class Shipment(orderId: String, warehouse: String, lines: List[Line])

  /** The shipment as the warehouse reads it: labelled with the dock it leaves from. */
  final case class LabelledShipment(orderId: String, warehouse: String, dock: Int, lines: List[Line])

  object LabelledShipment {
    given Codec[LabelledShipment] = deriveCodec[LabelledShipment]
  }

  final case class UnknownWarehouse(warehouse: String) extends RuntimeException(s"no dock for $warehouse")

  /** Splits an order into one shipment per warehouse, in the order the warehouses appear. */
  def shipmentsFor(order: Order): List[Shipment] =
    order.lines.map(_.warehouse).distinct.map { warehouse =>
      Shipment(order.id, warehouse, order.lines.filter(_.warehouse == warehouse))
    }

  def shipmentSerializer(docks: Map[String, Int]): Serializer[IO, Shipment] =
    Serializer.lift[IO, Shipment] { shipment =>
      IO.fromOption(docks.get(shipment.warehouse))(UnknownWarehouse(shipment.warehouse)).map { dock =>
        LabelledShipment(shipment.orderId, shipment.warehouse, dock, shipment.lines)
          .asJson
          .noSpaces
          .getBytes(StandardCharsets.UTF_8)
      }
    }

  val orderDeserializer: Deserializer[IO, Order] =
    Deserializer.string[IO].flatMap(text => Deserializer.lift[IO, Order](_ => IO.fromEither(decode[Order](text))))

  val consumerSettings: ConsumerSettings[IO, String, Order] =
    ConsumerSettings(Deserializer[IO, String], orderDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId(GroupId)
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withIsolationLevel(IsolationLevel.ReadCommitted)
      .withPollInterval(10.millis)

  def producerSettings(docks: Map[String, Int]): TransactionalProducerSettings[IO, String, Shipment] =
    TransactionalProducerSettings(
      "fulfilment-1",
      ProducerSettings(Serializer[IO, String], shipmentSerializer(docks))
        .withBootstrapServers("localhost:9092")
        .withAcks(Acks.All)
        .withEnableIdempotence(true)
    ).withTransactionTimeout(30.seconds)

  /**
   * Runs the fulfilment service until it has handled `expected` orders: each consumed order
   * becomes one transaction holding its shipments and its offset.
   */
  def runFulfilment(docks: Map[String, Int], expected: Int)(using MkConsumer[IO], MkProducer[IO]): IO[Either[Throwable, Unit]] = {
    val clients: Resource[IO, (KafkaConsumer[IO, String, Order], TransactionalKafkaProducer[IO, String, Shipment])] =
      for {
        consumer <- KafkaConsumer.resource(consumerSettings)
        producer <- TransactionalKafkaProducer.resource(producerSettings(docks))
      } yield (consumer, producer)
    clients.use { (consumer, producer) =>
      consumer.subscribeTo(OrdersTopic) *>
        consumer.records
          .map { committable =>
            val shipments: List[ProducerRecord[String, Shipment]] =
              shipmentsFor(committable.record.value).map(shipment => ProducerRecord(ShipmentsTopic, shipment.orderId, shipment))
            CommittableProducerRecords.chunk(Chunk.from(shipments), committable.offset)
          }
          .evalMap(records => producer.produce(TransactionalProducerRecords.one(records)))
          .take(expected.toLong)
          .compile
          .drain
          .timeout(30.seconds)
    }.attempt
  }

  /**
   * Stands in for the brokers: the orders topic has one partition holding the whole order log,
   * and offsets committed by a producer's transactions are the group's committed offsets
   * that the next consumer starts from.
   */
  final class FulfilmentCluster(log: List[Order]) {

    private val consumerClients: CopyOnWriteArrayList[MockConsumer[Array[Byte], Array[Byte]]] =
      new CopyOnWriteArrayList()

    private val producerClients: CopyOnWriteArrayList[MockProducer[Array[Byte], Array[Byte]]] =
      new CopyOnWriteArrayList()

    private val consumerConfigs: CopyOnWriteArrayList[Map[String, String]] = new CopyOnWriteArrayList()

    private val producerConfigs: CopyOnWriteArrayList[Map[String, String]] = new CopyOnWriteArrayList()

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](settings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          consumerConfigs.add(settings.properties)
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(OffsetResetStrategy.EARLIEST)
          consumer.updateBeginningOffsets(Map(OrdersPartition -> Long.box(0L)).asJava)
          val committed: Option[Long] = committedOffset
          consumer.schedulePollTask { () =>
            consumer.rebalance(List(OrdersPartition).asJava)
            // Subscribing resets MockConsumer's offsets, so the group's offsets come with the assignment.
            committed.foreach(offset => consumer.commitSync(Map(OrdersPartition -> new OffsetAndMetadata(offset)).asJava))
            log.zipWithIndex.foreach((order, offset) => consumer.addRecord(toJava(offset.toLong, order)))
          }
          consumerClients.add(consumer)
          consumer
        }
    }

    val mkProducer: MkProducer[IO] = new MkProducer[IO] {
      def apply[G[_]](settings: ProducerSettings[G, ?, ?]): IO[Producer[Array[Byte], Array[Byte]]] =
        IO {
          producerConfigs.add(settings.properties)
          val producer: MockProducer[Array[Byte], Array[Byte]] =
            new MockProducer(true, new ByteArraySerializer(), new ByteArraySerializer())
          producerClients.add(producer)
          producer
        }
    }

    private def toJava(offset: Long, order: Order): JavaConsumerRecord[Array[Byte], Array[Byte]] = {
      val key: Array[Byte] = order.customer.getBytes(StandardCharsets.UTF_8)
      val value: Array[Byte] = order.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      new JavaConsumerRecord(
        OrdersTopic,
        0,
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

    /** The offsets each committed transaction of `producer` stored for the group, in commit order. */
    def transactionOffsets(producer: MockProducer[Array[Byte], Array[Byte]]): List[Long] =
      producer.consumerGroupOffsetsHistory.asScala.toList.map { byGroup =>
        assertEquals(Set(GroupId), byGroup.keySet.asScala.toSet)
        byGroup.get(GroupId).get(OrdersPartition).offset
      }

    private def committedOffset: Option[Long] =
      producers.flatMap(transactionOffsets).lastOption

    private def published: List[JavaProducerRecord[Array[Byte], Array[Byte]]] =
      producers.flatMap(_.history.asScala.toList)

    def publishedShipments: List[LabelledShipment] =
      published.map { record =>
        assertEquals(ShipmentsTopic, record.topic)
        decode[LabelledShipment](new String(record.value, StandardCharsets.UTF_8)).fold(throw _, identity)
      }

    def publishedKeys: List[String] = published.map(record => new String(record.key, StandardCharsets.UTF_8))

    def producers: List[MockProducer[Array[Byte], Array[Byte]]] = producerClients.asScala.toList

    def consumers: List[MockConsumer[Array[Byte], Array[Byte]]] = consumerClients.asScala.toList

    def producerProperties: List[Map[String, String]] = producerConfigs.asScala.toList

    def consumerProperties: List[Map[String, String]] = consumerConfigs.asScala.toList
  }
}
