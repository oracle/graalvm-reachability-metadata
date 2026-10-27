/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Collections
import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import fs2.kafka.*
import fs2.kafka.consumer.MkConsumer
import fs2.kafka.producer.MkProducer
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.syntax.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord as JavaConsumerRecord
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.producer.RoundRobinPartitioner
import org.apache.kafka.clients.producer.ProducerRecord as JavaProducerRecord
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Serde composition for a compacted changelog: resource-scoped value serializers and
 * deserializers acquired and released with the client, topic-selected formats, optional
 * values written and read as tombstones, UUID keys and a custom typed header.
 *
 * A customer directory publishes profile changes from two sources, the current service
 * (wire-format JSON) and a legacy importer (pipe-separated text), and a materializer rebuilds
 * the directory from both topics. The producer is kafka-clients' `MockProducer` and the
 * consumer its `MockConsumer`, fed with the exact bytes the producer was handed, so the
 * scenario round-trips every record through the serializers and back.
 */
class CustomerDirectoryChangelogTest {

  import CustomerDirectoryChangelogTest.*
  import CustomerDirectoryChangelogTest.given

  @Test
  def materializerRebuildsTheDirectoryFromBothSourcesWithTombstones(): Unit = {
    val registry: SchemaRegistry = new SchemaRegistry(CurrentSchemaId)
    val broker: ChangelogBroker = new ChangelogBroker()
    given MkProducer[IO] = broker.mkProducer

    val changes: List[ProducerRecord[UUID, Option[Profile]]] = List(
      change(ProfilesTopic, Alice, Some(Profile("Alice", "alice@example.com", "gold")), 1_000L),
      change(LegacyTopic, Bob, Some(Profile("Bob", "bob@legacy.example.com", "silver")), 1_100L),
      change(ProfilesTopic, Carol, Some(Profile("Carol", "carol@example.com", "bronze")), 1_200L),
      change(ProfilesTopic, Bob, Some(Profile("Bob", "bob@example.com", "gold")), 1_300L),
      change(ProfilesTopic, Alice, None, 1_400L)
    )

    val acknowledged: List[(String, Long)] = KafkaProducer
      .resource(producerSettings(registry))
      .use(producer => producer.produce(ProducerRecords(changes)).flatten)
      .map(_.toList.map((_, metadata) => metadata.topic -> metadata.offset))
      .unsafeRunSync()

    assertEquals(
      List(ProfilesTopic -> 0L, LegacyTopic -> 0L, ProfilesTopic -> 1L, ProfilesTopic -> 2L, ProfilesTopic -> 3L),
      acknowledged
    )
    assertTrue(broker.isClosed, "releasing the producer closes the Kafka producer")
    assertEquals(0, registry.openConnections, "the value serializer resource is released with the producer")

    val sent: List[JavaProducerRecord[Array[Byte], Array[Byte]]] = broker.history
    assertEquals(changes.map(_.key.toString), sent.map(record => utf8(record.key)),
      "UUID keys are written as their canonical text")
    val aliceJoined: Array[Byte] = sent.head.value
    assertEquals(0, aliceJoined(0).toInt, "current-format values start with the wire-format magic byte")
    assertEquals(CurrentSchemaId, ByteBuffer.wrap(aliceJoined, 1, 4).getInt)
    assertEquals(Right(Profile("Alice", "alice@example.com", "gold")),
      decode[Profile](new String(aliceJoined, 5, aliceJoined.length - 5, StandardCharsets.UTF_8)))
    assertEquals("Bob|bob@legacy.example.com|silver", utf8(sent(1).value),
      "the legacy topic keeps the importer's text format")
    assertNull(sent(4).value, "a removed profile is written as a tombstone")
    assertEquals(1_400L, ByteBuffer.wrap(sent(4).headers.lastHeader(ChangedAtHeader).value).getLong,
      "the change time travels as an epoch-millisecond header")

    val cluster: ChangelogCluster = new ChangelogCluster(sent)
    given MkConsumer[IO] = cluster.mkConsumer

    val directory: Map[UUID, Entry] = materialize(registry, sent.size).unsafeRunSync()

    assertEquals(Set(Bob, Carol), live(directory).keySet, "alice's tombstone removes her profile")
    assertEquals(Profile("Bob", "bob@example.com", "gold"), live(directory)(Bob),
      "the latest change wins regardless of which source topic delivered it first")
    assertEquals(Profile("Carol", "carol@example.com", "bronze"), live(directory)(Carol))
    assertEquals(Entry(None, Instant.ofEpochMilli(1_400L)), directory(Alice),
      "the tombstone is kept with its change time so older changes cannot resurrect the profile")
    assertEquals(Instant.ofEpochMilli(1_300L), directory(Bob).changedAt)
    assertTrue(cluster.isClosed, "releasing the consumer closes the Kafka consumer")
    assertEquals(0, registry.openConnections, "the value deserializer resource is released with the consumer")
    assertEquals(2, registry.totalConnections, "producer and consumer each acquire their own registry client")
  }

  @Test
  def unknownFormatsAreRejectedOnBothSidesOfTheChangelog(): Unit = {
    val registry: SchemaRegistry = new SchemaRegistry(CurrentSchemaId)
    val broker: ChangelogBroker = new ChangelogBroker()
    given MkProducer[IO] = broker.mkProducer

    val outcome: (RecordMetadata, Either[Throwable, RecordMetadata]) = KafkaProducer
      .resource(producerSettings(registry))
      .use { producer =>
        for {
          accepted <- producer
            .produceOne_(change(ProfilesTopic, Carol, Some(Profile("Carol", "carol@example.com", "bronze")), 2_000L))
            .flatten
          rejected <- producer
            .produceOne_(change(ArchiveTopic, Carol, Some(Profile("Carol", "carol@example.com", "bronze")), 2_100L))
            .flatten
            .attempt
        } yield (accepted, rejected)
      }
      .unsafeRunSync()

    assertEquals(ProfilesTopic, outcome._1.topic)
    val rejection: Throwable = outcome._2.swap.toOption.get
    assertInstanceOf(classOf[UnexpectedTopicException], rejection,
      "a topic without a registered format fails serialization")
    assertTrue(rejection.getMessage.contains(ArchiveTopic), rejection.getMessage)
    assertEquals(List(ProfilesTopic), broker.history.map(_.topic),
      "the rejected record never reaches the Kafka producer")
    assertEquals(0, registry.openConnections)

    val newerFormat: JavaProducerRecord[Array[Byte], Array[Byte]] = new JavaProducerRecord(
      ProfilesTopic,
      null,
      Carol.toString.getBytes(StandardCharsets.UTF_8),
      wireFormat(CurrentSchemaId + 1, """{"name":"Carol","email":"c@example.com","tier":"gold","region":"eu"}"""
        .getBytes(StandardCharsets.UTF_8)),
      Headers(Header(ChangedAtHeader, Instant.ofEpochMilli(2_200L))).asJava
    )
    val cluster: ChangelogCluster = new ChangelogCluster(broker.history :+ newerFormat)
    given MkConsumer[IO] = cluster.mkConsumer

    val failure: Either[Throwable, Map[UUID, Entry]] = materialize(registry, 2).attempt.unsafeRunSync()

    val error: Throwable = failure.swap.toOption.get
    assertInstanceOf(classOf[SerializationException], error,
      "a schema the materializer does not know stops it instead of dropping the change")
    assertTrue(error.getMessage.contains(s"schema ${CurrentSchemaId + 1}"), error.getMessage)
    assertTrue(cluster.isClosed)
    assertEquals(0, registry.openConnections, "the deserializer resource is released when the stream fails")
  }
}

object CustomerDirectoryChangelogTest {

  val ProfilesTopic: String = "customer-profiles"
  val LegacyTopic: String = "customer-profiles-legacy"
  val ArchiveTopic: String = "customer-profiles-archive"
  val ChangedAtHeader: String = "changed-at"
  val CurrentSchemaId: Int = 7
  val MagicByte: Byte = 0

  val Alice: UUID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")
  val Bob: UUID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7")
  val Carol: UUID = UUID.fromString("16fd2706-8baf-433b-82eb-8c7fada847da")

  final case class Profile(name: String, email: String, tier: String)

  object Profile {
    given Codec[Profile] = deriveCodec[Profile]
  }

  /** The directory's view of one customer: the latest profile, or `None` once deleted. */
  final case class Entry(profile: Option[Profile], changedAt: Instant)

  given HeaderSerializer[Instant] = HeaderSerializer[Long].contramap[Instant](_.toEpochMilli)

  given HeaderDeserializer[Either[Throwable, Instant]] =
    HeaderDeserializer[Either[Throwable, Long]].map(_.map(Instant.ofEpochMilli))

  def change(topic: String, customer: UUID, profile: Option[Profile], changedAtMillis: Long)
      : ProducerRecord[UUID, Option[Profile]] =
    ProducerRecord(topic, customer, profile)
      .withHeaders(Headers(Header(ChangedAtHeader, Instant.ofEpochMilli(changedAtMillis))))

  def utf8(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

  /** Prefixes a payload with the magic byte and schema id, the schema-registry wire format. */
  def wireFormat(schemaId: Int, payload: Array[Byte]): Array[Byte] =
    ByteBuffer.allocate(5 + payload.length).put(MagicByte).putInt(schemaId).put(payload).array

  /**
   * Stands in for a schema-registry client: each serializer or deserializer resource opens a
   * connection when the Kafka client is created and closes it when the client is released.
   */
  final class SchemaRegistry(schemaId: Int) {

    private val open: AtomicInteger = new AtomicInteger()

    private val total: AtomicInteger = new AtomicInteger()

    val connection: Resource[IO, Int] =
      Resource.make(IO { open.incrementAndGet(); total.incrementAndGet(); schemaId })(_ =>
        IO(open.decrementAndGet()).void
      )

    def openConnections: Int = open.get

    def totalConnections: Int = total.get
  }

  /** Writes current-format profiles as wire-format JSON and legacy ones as pipe-separated text. */
  def profileSerializer(schemaId: Int): Serializer[IO, Option[Profile]] = {
    val current: Serializer[IO, Profile] = Serializer[IO, String]
      .contramap[Profile](_.asJson.noSpaces)
      .mapBytes(json => wireFormat(schemaId, json))
    val legacy: Serializer[IO, Profile] =
      Serializer[IO, String].contramap[Profile](profile => s"${profile.name}|${profile.email}|${profile.tier}")
    Serializer
      .topic[KeyOrValue, IO, Profile] {
        case ProfilesTopic => current
        case LegacyTopic => legacy
      }
      .option
  }

  def producerSettings(registry: SchemaRegistry): ProducerSettings[IO, UUID, Option[Profile]] = {
    val values: Resource[IO, ValueSerializer[IO, Option[Profile]]] =
      registry.connection.map(profileSerializer)
    ProducerSettings(Serializer.uuid[IO], values)
      .withBootstrapServers("localhost:9092")
      .withClientId("customer-directory-publisher")
      .withAcks(Acks.All)
  }

  /** Reads both formats, mapping tombstones to `None`, and pairs each change with its time. */
  def profileDeserializer(schemaId: Int): Deserializer[IO, (Option[Profile], Instant)] = {
    val current: Deserializer[IO, Profile] = Deserializer.lift[IO, Profile] { bytes =>
      val buffer: ByteBuffer = ByteBuffer.wrap(bytes)
      val magic: Byte = buffer.get()
      val writerSchema: Int = buffer.getInt()
      if (magic != MagicByte || writerSchema != schemaId)
        IO.raiseError(new SerializationException(s"cannot read profiles written with schema $writerSchema"))
      else
        IO.fromEither(decode[Profile](new String(bytes, 5, bytes.length - 5, StandardCharsets.UTF_8)))
    }
    val legacy: Deserializer[IO, Profile] = Deserializer[IO, String].map { text =>
      val Array(name, email, tier) = text.split('|')
      Profile(name, email, tier)
    }
    val changedAt: Deserializer[IO, Instant] = Deserializer.headers[IO, Instant] { headers =>
      headers(ChangedAtHeader).map(_.attemptAs[Instant]) match {
        case Some(Right(changedAt)) => Deserializer.const[IO, Instant](changedAt)
        case Some(Left(error)) => Deserializer.fail[IO, Instant](error)
        case None => Deserializer.failWith[IO, Instant](s"missing $ChangedAtHeader header")
      }
    }
    Deserializer
      .topic[KeyOrValue, IO, Profile] {
        case ProfilesTopic => current
        case LegacyTopic => legacy
      }
      .option
      .product(changedAt)
  }

  def consumerSettings(registry: SchemaRegistry): ConsumerSettings[IO, UUID, (Option[Profile], Instant)] = {
    val values: Resource[IO, ValueDeserializer[IO, (Option[Profile], Instant)]] =
      registry.connection.map(profileDeserializer)
    ConsumerSettings(Deserializer.uuid[IO], values)
      .withBootstrapServers("localhost:9092")
      .withGroupId("customer-directory")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withPollInterval(10.millis)
  }

  /** Applies a change unless the directory already holds a newer one for that customer. */
  def applyChange(directory: Map[UUID, Entry], customer: UUID, entry: Entry): Map[UUID, Entry] =
    directory.get(customer) match {
      case Some(existing) if existing.changedAt.isAfter(entry.changedAt) => directory
      case _ => directory.updated(customer, entry)
    }

  def live(directory: Map[UUID, Entry]): Map[UUID, Profile] =
    directory.collect { case (customer, Entry(Some(profile), _)) => customer -> profile }

  /** Reads `expected` changes from both topics into a last-writer-wins directory. */
  def materialize(registry: SchemaRegistry, expected: Int)(using MkConsumer[IO]): IO[Map[UUID, Entry]] =
    KafkaConsumer.resource(consumerSettings(registry)).use { consumer =>
      consumer.subscribeTo(ProfilesTopic, LegacyTopic) >>
        consumer.records
          .map(_.record)
          .take(expected.toLong)
          .compile
          .fold(Map.empty[UUID, Entry]) { (directory, record) =>
            val (profile, changedAt): (Option[Profile], Instant) = record.value
            applyChange(directory, record.key, Entry(profile, changedAt))
          }
          .timeout(30.seconds)
    }

  /**
   * Stands in for the brokers on the producing side: every producer is a `MockProducer` that
   * acknowledges sends immediately on a cluster where each changelog topic has one partition.
   */
  final class ChangelogBroker {

    private val node: Node = new Node(0, "localhost", 9092)

    private val cluster: Cluster = new Cluster(
      "directory-cluster",
      List(node).asJava,
      List(ProfilesTopic, LegacyTopic, ArchiveTopic)
        .map(topic => new PartitionInfo(topic, 0, node, Array(node), Array(node)))
        .asJava,
      Collections.emptySet[String](),
      Collections.emptySet[String]()
    )

    private val current: AtomicReference[MockProducer[Array[Byte], Array[Byte]]] = new AtomicReference()

    val mkProducer: MkProducer[IO] = new MkProducer[IO] {
      def apply[G[_]](producerSettings: ProducerSettings[G, ?, ?]): IO[Producer[Array[Byte], Array[Byte]]] =
        IO {
          val producer: MockProducer[Array[Byte], Array[Byte]] =
            new MockProducer(
              cluster,
              true,
              new RoundRobinPartitioner(),
              new ByteArraySerializer(),
              new ByteArraySerializer()
            )
          current.set(producer)
          producer
        }
    }

    def history: List[JavaProducerRecord[Array[Byte], Array[Byte]]] = current.get.history.asScala.toList

    def isClosed: Boolean = current.get.closed
  }

  /**
   * Stands in for the brokers on the consuming side: the directory group is assigned both
   * changelog partitions on its first poll and receives the given records, in order per topic.
   */
  final class ChangelogCluster(records: List[JavaProducerRecord[Array[Byte], Array[Byte]]]) {

    private val partitions: Set[TopicPartition] =
      Set(new TopicPartition(ProfilesTopic, 0), new TopicPartition(LegacyTopic, 0))

    private val current: AtomicReference[MockConsumer[Array[Byte], Array[Byte]]] = new AtomicReference()

    val mkConsumer: MkConsumer[IO] = new MkConsumer[IO] {
      def apply[G[_]](consumerSettings: ConsumerSettings[G, ?, ?]): IO[Consumer[Array[Byte], Array[Byte]]] =
        IO {
          val consumer: MockConsumer[Array[Byte], Array[Byte]] = new MockConsumer(OffsetResetStrategy.EARLIEST)
          consumer.updateBeginningOffsets(partitions.map(_ -> Long.box(0L)).toMap.asJava)
          consumer.schedulePollTask { () =>
            consumer.rebalance(partitions.asJava)
            records.groupBy(_.topic).foreach { (topic, sent) =>
              sent.zipWithIndex.foreach { (record, offset) =>
                consumer.addRecord(toConsumerRecord(record, offset.toLong))
              }
            }
          }
          current.set(consumer)
          consumer
        }
    }

    private def toConsumerRecord(
        record: JavaProducerRecord[Array[Byte], Array[Byte]],
        offset: Long
    ): JavaConsumerRecord[Array[Byte], Array[Byte]] =
      new JavaConsumerRecord(
        record.topic,
        0,
        offset,
        1_700_000_000_000L + offset,
        TimestampType.CREATE_TIME,
        record.key.length,
        Option(record.value).fold(-1)(_.length),
        record.key,
        record.value,
        new RecordHeaders(record.headers.toArray),
        Optional.empty[Integer]()
      )

    def isClosed: Boolean = current.get.closed
  }
}
