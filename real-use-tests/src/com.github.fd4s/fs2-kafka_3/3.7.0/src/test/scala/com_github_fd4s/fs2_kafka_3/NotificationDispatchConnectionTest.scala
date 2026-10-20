/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.kafka.*
import fs2.kafka.producer.MkProducer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.syntax.*
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord as JavaProducerRecord
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.Metric
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Shares one fs2-kafka `KafkaProducerConnection` between several typed producers, so a
 * notification service publishes e-mails, text messages and raw dead letters over a single
 * Kafka producer, and reads its partition layout and metrics through the same connection.
 *
 * The Kafka client underneath is kafka-clients' `MockProducer`, plugged in through the
 * `MkProducer` extension point, so the test sees how many producers fs2-kafka opened and
 * exactly which bytes each typed producer handed to Kafka.
 */
class NotificationDispatchConnectionTest {

  import NotificationDispatchConnectionTest.*

  @Test
  def typedProducersShareOneUnderlyingKafkaProducer(): Unit = {
    val broker: SharedBroker = new SharedBroker()
    given MkProducer[IO] = broker.mkProducer

    val emails: List[Email] = List(
      Email("alice@example.com", "Your order shipped", "Parcel 42 is on its way."),
      Email("bob@example.com", "Password changed", "If this was not you, contact support.")
    )
    val texts: List[Sms] = List(Sms(15550100L, "Code 4711"), Sms(15550199L, "Courier arrives at 10:00"))

    val (emailResults, smsResults, deadLetterResult) = KafkaProducerConnection
      .resource(connectionSettings)
      .use { connection =>
        val smsProducer: KafkaProducer.PartitionsFor[IO, Long, Sms] =
          connection.withSerializers(phoneNumberSerializer, smsSerializer)
        connection.withSerializersFrom(emailSettings).use { emailProducer =>
          for {
            emailResults <- emailProducer.produce(
              ProducerRecords(emails.map(email => ProducerRecord(EmailTopic, email.to, email)))
            ).flatten
            smsResults <- texts.traverse(sms =>
              smsProducer.produceOne(SmsTopic, sms.phoneNumber, sms).flatten
            )
            deadLetterResult <- connection.produce(
              ProducerRecords.one(
                ProducerRecord(DeadLetterTopic, "email-bounce", "550 mailbox unavailable")
                  .withHeaders(Headers(Header("source-topic", EmailTopic)))
              )
            ).flatten
          } yield (emailResults, smsResults, deadLetterResult)
        }
      }
      .unsafeRunSync()

    assertEquals(1, broker.producersOpened.get, "all typed producers share one Kafka producer")
    assertTrue(broker.isClosed, "releasing the connection closes the shared producer")

    assertEquals(emails, emailResults.toList.map(_._1.value))
    val emailOffsets: Map[Int, List[Long]] = emailResults.toList.groupMap(_._2.partition)(_._2.offset)
    assertTrue(emailOffsets.keySet.subsetOf(Set(0, 1, 2)))
    emailOffsets.values.foreach(offsets => assertEquals(offsets.indices.toList, offsets.map(_.toInt)))
    assertEquals(texts, smsResults.flatMap(_.toList.map(_._1.value)))
    assertEquals(List(SmsTopic, SmsTopic), smsResults.flatMap(_.toList.map(_._2.topic)))
    assertEquals(DeadLetterTopic, deadLetterResult.toList.head._2.topic)

    val sent: List[JavaProducerRecord[Array[Byte], Array[Byte]]] = broker.history
    assertEquals(List(EmailTopic, EmailTopic, SmsTopic, SmsTopic, DeadLetterTopic), sent.map(_.topic))

    val sentEmails: List[JavaProducerRecord[Array[Byte], Array[Byte]]] = sent.filter(_.topic == EmailTopic)
    assertEquals(emails.map(_.to), sentEmails.map(record => utf8(record.key)))
    assertEquals(emails, sentEmails.map(record => decode[Email](utf8(record.value)).toOption.get))

    val sentTexts: List[JavaProducerRecord[Array[Byte], Array[Byte]]] = sent.filter(_.topic == SmsTopic)
    assertEquals(texts.map(_.phoneNumber), sentTexts.map(record => ByteBuffer.wrap(record.key).getLong))
    assertEquals(List("15550100|Code 4711", "15550199|Courier arrives at 10:00"),
      sentTexts.map(record => utf8(record.value)))

    val deadLetter: JavaProducerRecord[Array[Byte], Array[Byte]] = sent.last
    assertEquals("email-bounce", utf8(deadLetter.key))
    assertEquals("550 mailbox unavailable", utf8(deadLetter.value))
    assertEquals(EmailTopic, utf8(deadLetter.headers.lastHeader("source-topic").value))

    val config: Map[String, String] = broker.lastSettings
    assertEquals("localhost:9092", config(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
    assertEquals("notification-dispatcher", config(ProducerConfig.CLIENT_ID_CONFIG))
    assertEquals("lz4", config(ProducerConfig.COMPRESSION_TYPE_CONFIG))
    assertEquals("5", config(ProducerConfig.LINGER_MS_CONFIG))
  }

  @Test
  def connectionStreamExposesPartitionLayoutAndProducerMetrics(): Unit = {
    val broker: SharedBroker = new SharedBroker()
    given MkProducer[IO] = broker.mkProducer

    val (emailPartitions, smsPartitions, connectionMetrics, producerMetrics) = KafkaProducerConnection
      .stream(connectionSettings)
      .evalMap { connection =>
        val smsProducer: KafkaProducer.PartitionsFor[IO, Long, Sms] =
          connection.withSerializers(phoneNumberSerializer, smsSerializer)
        for {
          emailPartitions <- connection.partitionsFor(EmailTopic)
          smsPartitions <- smsProducer.partitionsFor(SmsTopic)
          connectionMetrics <- connection.metrics
          producerMetrics <- smsProducer.metrics
        } yield (emailPartitions, smsPartitions, connectionMetrics, producerMetrics)
      }
      .compile
      .lastOrError
      .unsafeRunSync()

    assertEquals(List(0, 1, 2), emailPartitions.map(_.partition).sorted)
    assertTrue(emailPartitions.forall(_.topic == EmailTopic))
    assertEquals(List(0, 1), smsPartitions.map(_.partition).sorted)
    assertTrue(broker.isClosed, "the connection stream closes the producer when it completes")

    val sentRecords: MetricName = new MetricName("record-send-total", "producer-metrics", "", Map.empty.asJava)
    assertEquals(Set(sentRecords), connectionMetrics.keySet)
    assertEquals(128.0, connectionMetrics(sentRecords).metricValue)
    assertEquals(connectionMetrics.keySet, producerMetrics.keySet)
    assertEquals(1, broker.producersOpened.get)
  }
}

object NotificationDispatchConnectionTest {

  val EmailTopic: String = "notifications-email"
  val SmsTopic: String = "notifications-sms"
  val DeadLetterTopic: String = "notifications-dead-letter"

  final case class Email(to: String, subject: String, body: String)

  object Email {
    given Codec[Email] = deriveCodec[Email]
  }

  final case class Sms(phoneNumber: Long, text: String)

  /** Connection-level settings: the serializers here are ignored by typed producers. */
  val connectionSettings: ProducerSettings[IO, Unit, Unit] =
    ProducerSettings(Serializer.asNull[IO, Unit], Serializer.asNull[IO, Unit])
      .withBootstrapServers("localhost:9092")
      .withClientId("notification-dispatcher")
      .withProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
      .withLinger(5.millis)

  val emailSettings: ProducerSettings[IO, String, Email] =
    ProducerSettings(Serializer[IO, String], Serializer[IO, String].contramap[Email](_.asJson.noSpaces))

  val phoneNumberSerializer: KeySerializer[IO, Long] = Serializer.long[IO]

  /** The SMS gateway reads `number|text` lines. */
  val smsSerializer: ValueSerializer[IO, Sms] =
    Serializer.string[IO].contramap[Sms](sms => s"${sms.phoneNumber}|${sms.text}")

  def utf8(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

  /** A fixed producer metric, as the Kafka client reports it. */
  final class CounterMetric(name: MetricName, value: Double) extends Metric {

    override def metricName(): MetricName = name

    override def metricValue(): AnyRef = java.lang.Double.valueOf(value)
  }

  /**
   * Stands in for the Kafka cluster: every producer fs2-kafka creates is a `MockProducer`
   * that acknowledges sends immediately and counts how often fs2-kafka opened one.
   */
  final class SharedBroker {

    private val node: Node = new Node(0, "localhost", 9092)

    private val cluster: Cluster = new Cluster(
      "notification-cluster",
      List(node).asJava,
      (for {
        (topic, partitions) <- List(EmailTopic -> 3, SmsTopic -> 2, DeadLetterTopic -> 1)
        partition <- 0 until partitions
      } yield new PartitionInfo(topic, partition, node, Array(node), Array(node))).asJava,
      Collections.emptySet[String](),
      Collections.emptySet[String]()
    )

    private val current: AtomicReference[MockProducer[Array[Byte], Array[Byte]]] =
      new AtomicReference()

    private val settings: AtomicReference[Map[String, String]] = new AtomicReference(Map.empty)

    val producersOpened: AtomicInteger = new AtomicInteger()

    val mkProducer: MkProducer[IO] = new MkProducer[IO] {
      def apply[G[_]](producerSettings: ProducerSettings[G, ?, ?]): IO[Producer[Array[Byte], Array[Byte]]] =
        IO {
          settings.set(producerSettings.properties)
          producersOpened.incrementAndGet()
          val producer: MockProducer[Array[Byte], Array[Byte]] =
            new MockProducer(cluster, true, new ByteArraySerializer(), new ByteArraySerializer())
          val sentRecords: MetricName =
            new MetricName("record-send-total", "producer-metrics", "", Map.empty.asJava)
          producer.setMockMetrics(sentRecords, new CounterMetric(sentRecords, 128.0))
          current.set(producer)
          producer
        }
    }

    def history: List[JavaProducerRecord[Array[Byte], Array[Byte]]] = current.get.history.asScala.toList

    def isClosed: Boolean = current.get.closed

    def lastSettings: Map[String, String] = settings.get
  }
}
