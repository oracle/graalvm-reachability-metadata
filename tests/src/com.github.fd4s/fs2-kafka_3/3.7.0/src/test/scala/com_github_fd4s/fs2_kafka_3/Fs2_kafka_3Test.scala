/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_fd4s.fs2_kafka_3

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.eq.*
import cats.syntax.show.*
import cats.syntax.traverse.*
import fs2.kafka.*
import fs2.kafka.security.KafkaCredentialStore
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{Deserializer as KafkaDeserializer, Serializer as KafkaSerializer}
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Fs2_kafka_3Test {

  @Test
  def headersAreImmutableAndSupportTypedValues(): Unit = {
    val uuid: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val first: Header = Header("text", "alpha")
    val second: Header = Header("number", 42)
    val duplicate: Header = Header("text", "omega")

    val headers: Headers = Headers.empty
      .append(first)
      .append(second)
      .append(duplicate)
      .append("uuid", uuid)

    assertFalse(headers.isEmpty)
    assertTrue(headers.nonEmpty)
    assertTrue(headers.exists("text"))
    assertEquals(Some(first), headers("text"))
    assertEquals("alpha", headers("text").get.as[String])
    assertEquals(Right(42), headers("number").get.attemptAs[Int])
    assertEquals(Right(uuid), headers("uuid").get.attemptAs[UUID])
    assertEquals(4, headers.toChain.size)

    val javaHeaders = headers.asJava
    assertEquals("omega", new String(javaHeaders.lastHeader("text").value, StandardCharsets.UTF_8))
    assertEquals(2, javaHeaders.toArray.count(_.key == "text"))
    assertEquals(Seq("text", "number", "text", "uuid"), javaHeaders.toArray.map(_.key).toSeq)
    assertThrows(classOf[IllegalStateException], () => javaHeaders.add("illegal", bytes("value")))
    assertThrows(classOf[IllegalStateException], () => Headers.empty.asJava.remove("missing"))
  }

  @Test
  def headerSerializersAndDeserializersComposeWithoutKafkaClients(): Unit = {
    val upperUtf8: HeaderSerializer[String] = HeaderSerializer.string(StandardCharsets.UTF_8)
      .contramap[String](_.trim)
      .mapBytes(_.map(byte => Character.toUpperCase(byte.toChar).toByte))

    assertArrayEquals(bytes("KAFKA"), upperUtf8.serialize(" kafka "))
    assertNull(HeaderSerializer.string.option.serialize(None))
    assertArrayEquals(bytes("present"), HeaderSerializer.string.option.serialize(Some("present")))

    val parsed: HeaderDeserializer[(String, Int)] = HeaderDeserializer.string
      .product(HeaderDeserializer.string.map(_.toInt))
    assertEquals(("123", 123), parsed.deserialize(bytes("123")))

    val delayed = HeaderDeserializer.string.map(_.reverse).delay.deserialize(bytes("abc"))
    assertEquals("cba", delayed.value)
    assertTrue(HeaderDeserializer.uuid.deserialize(bytes("not-a-uuid")).isLeft)
    assertEquals(Some("value"), HeaderDeserializer.string.option.deserialize(bytes("value")))
    assertEquals(None, HeaderDeserializer.string.option.deserialize(null))
  }

  @Test
  def serializersSupportTopicHeaderAndOptionComposition(): Unit = {
    val routedByTopic: Serializer[IO, String] = Serializer.topic[KeyOrValue, IO, String] {
      case "upper" => Serializer.string[IO].contramap[String](_.toUpperCase)
      case "lower" => Serializer.string[IO].contramap[String](_.toLowerCase)
    }

    assertArrayEquals(bytes("VALUE"), routedByTopic.serialize("upper", Headers.empty, "Value").unsafeRunSync())
    assertArrayEquals(bytes("value"), routedByTopic.serialize("lower", Headers.empty, "Value").unsafeRunSync())

    val headerSensitive: Serializer[IO, String] = Serializer.headers[IO, String] { headers =>
      if (headers.exists("suffix")) Serializer.string[IO].mapBytes(_ ++ bytes("!"))
      else Serializer.string[IO]
    }

    assertArrayEquals(
      bytes("payload!"),
      headerSensitive.serialize("topic", Headers(Header("suffix", "yes")), "payload").unsafeRunSync()
    )
    assertArrayEquals(bytes("payload"), headerSensitive.serialize("topic", Headers.empty, "payload").unsafeRunSync())

    val numeric: Serializer[IO, Int] = Serializer.int[IO]
    val roundTrip: Deserializer[IO, Int] = Deserializer.int[IO]
    val intBytes: Array[Byte] = numeric.serialize("topic", Headers.empty, 12345).unsafeRunSync()
    assertEquals(12345, roundTrip.deserialize("topic", Headers.empty, intBytes).unsafeRunSync())

    assertNull(Serializer.string[IO].option.serialize("topic", Headers.empty, None).unsafeRunSync())
    assertArrayEquals(
      bytes("some"),
      Serializer.string[IO].option.serialize("topic", Headers.empty, Some("some")).unsafeRunSync()
    )
  }

  @Test
  def deserializersSupportTopicHeaderAttemptAndProductComposition(): Unit = {
    val routedByTopic: Deserializer[IO, String] = Deserializer.topic[KeyOrValue, IO, String] {
      case "trim" => Deserializer.string[IO].map(_.trim)
      case "raw"  => Deserializer.string[IO]
    }

    assertEquals("value", routedByTopic.deserialize("trim", Headers.empty, bytes(" value ")).unsafeRunSync())
    assertEquals(" value ", routedByTopic.deserialize("raw", Headers.empty, bytes(" value ")).unsafeRunSync())

    val headerSensitive: Deserializer[IO, String] = Deserializer.headers[IO, String] { headers =>
      if (headers.exists("reverse")) Deserializer.string[IO].map(_.reverse)
      else Deserializer.string[IO]
    }
    assertEquals(
      "cba",
      headerSensitive.deserialize("topic", Headers(Header("reverse", "true")), bytes("abc")).unsafeRunSync()
    )

    val product: Deserializer[IO, (String, Int)] = Deserializer.string[IO].product(
      Deserializer.string[IO].map(_.toInt)
    )
    assertEquals(("17", 17), product.deserialize("topic", Headers.empty, bytes("17")).unsafeRunSync())

    val failedUuid = Deserializer.uuid[IO].deserialize("topic", Headers.empty, bytes("bad-uuid")).attempt.unsafeRunSync()
    assertTrue(failedUuid.isLeft)
    assertEquals(None, Deserializer.string[IO].option.deserialize("topic", Headers.empty, null).unsafeRunSync())
    assertEquals(Some("ok"), Deserializer.string[IO].option.deserialize("topic", Headers.empty, bytes("ok")).unsafeRunSync())
  }

  @Test
  def recordsExposeImmutableRecordStateAndCatsInstances(): Unit = {
    val headers: Headers = Headers(Header("source", "unit-test"))
    val producerRecord: ProducerRecord[String, String] = ProducerRecord("out", "key", "value")
      .withPartition(2)
      .withTimestamp(1234L)
      .withHeaders(headers)

    assertEquals("out", producerRecord.topic)
    assertEquals(Some(2), producerRecord.partition)
    assertEquals(Some(1234L), producerRecord.timestamp)
    assertEquals(headers, producerRecord.headers)
    assertTrue(producerRecord.show.contains("topic = out"))
    assertTrue(producerRecord === producerRecord)

    val traversedProducer: Option[ProducerRecord[String, Int]] = producerRecord.traverse(value => Option(value.length))
    assertEquals(Some(ProducerRecord("out", "key", 5).withPartition(2).withTimestamp(1234L).withHeaders(headers)), traversedProducer)

    val consumerRecord: ConsumerRecord[String, String] = ConsumerRecord("in", 1, 99L, "key", "value")
      .withHeaders(headers)
      .withTimestamp(Timestamp.createTime(4321L))
      .withSerializedKeySize(3)
      .withSerializedValueSize(5)
      .withLeaderEpoch(7)

    assertEquals("in", consumerRecord.topic)
    assertEquals(1, consumerRecord.partition)
    assertEquals(99L, consumerRecord.offset)
    assertEquals(Some(4321L), consumerRecord.timestamp.createTime)
    assertEquals(Some(3), consumerRecord.serializedKeySize)
    assertEquals(Some(5), consumerRecord.serializedValueSize)
    assertEquals(Some(7), consumerRecord.leaderEpoch)
    assertTrue(consumerRecord.show.contains("offset = 99"))
    assertTrue(consumerRecord === consumerRecord)

    val traversedConsumer: Option[ConsumerRecord[String, Int]] = consumerRecord.traverse(value => Option(value.length))
    assertTrue(traversedConsumer.isDefined)
    assertEquals("in", traversedConsumer.get.topic)
    assertEquals(1, traversedConsumer.get.partition)
    assertEquals(99L, traversedConsumer.get.offset)
    assertEquals("key", traversedConsumer.get.key)
    assertEquals(5, traversedConsumer.get.value)
    assertEquals(headers, traversedConsumer.get.headers)
    assertEquals(Some(4321L), traversedConsumer.get.timestamp.createTime)
    assertEquals(Some(3), traversedConsumer.get.serializedKeySize)
    assertEquals(Some(5), traversedConsumer.get.serializedValueSize)
    assertEquals(Some(7), traversedConsumer.get.leaderEpoch)
  }

  @Test
  def serializersAndDeserializersDelegateToKafkaImplementationsWithHeaders(): Unit = {
    final class HeaderAwareKafkaSerializer extends KafkaSerializer[String] {
      override def serialize(topic: String, data: String): Array[Byte] =
        bytes(s"$topic:$data")

      override def serialize(
          topic: String,
          headers: org.apache.kafka.common.header.Headers,
          data: String
      ): Array[Byte] = {
        val suffix = new String(headers.lastHeader("suffix").value, StandardCharsets.UTF_8)
        bytes(s"$topic:$data:$suffix")
      }
    }

    final class HeaderAwareKafkaDeserializer extends KafkaDeserializer[String] {
      override def deserialize(topic: String, data: Array[Byte]): String =
        s"$topic:${new String(data, StandardCharsets.UTF_8)}"

      override def deserialize(
          topic: String,
          headers: org.apache.kafka.common.header.Headers,
          data: Array[Byte]
      ): String = {
        val prefix = new String(headers.lastHeader("prefix").value, StandardCharsets.UTF_8)
        s"$prefix:$topic:${new String(data, StandardCharsets.UTF_8)}"
      }
    }

    val delegatedSerializer: Serializer[IO, String] = Serializer.delegate(new HeaderAwareKafkaSerializer)
    val serialized: Array[Byte] = delegatedSerializer
      .serialize("topic-a", Headers(Header("suffix", "tail")), "payload")
      .unsafeRunSync()

    assertArrayEquals(bytes("topic-a:payload:tail"), serialized)

    val delegatedDeserializer: Deserializer[IO, String] = Deserializer.delegate(new HeaderAwareKafkaDeserializer)
    val deserialized: String = delegatedDeserializer
      .deserialize("topic-b", Headers(Header("prefix", "head")), bytes("payload"))
      .unsafeRunSync()

    assertEquals("head:topic-b:payload", deserialized)
  }

  @Test
  def settingsBuildersPopulateKafkaPropertiesImmutably(): Unit = {
    val producerSettings: ProducerSettings[IO, String, String] = ProducerSettings[IO, String, String](
      Serializer.string[IO],
      Serializer.string[IO]
    ).withBootstrapServers("localhost:9092")
      .withAcks(Acks.All)
      .withBatchSize(4096)
      .withClientId("producer-client")
      .withRetries(3)
      .withMaxInFlightRequestsPerConnection(1)
      .withEnableIdempotence(true)
      .withLinger(25.millis)
      .withRequestTimeout(5.seconds)
      .withDeliveryTimeout(10.seconds)
      .withFailFastProduce(true)
      .withCloseTimeout(2.seconds)

    assertEquals("localhost:9092", producerSettings.properties(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
    assertEquals("all", producerSettings.properties(ProducerConfig.ACKS_CONFIG))
    assertEquals("4096", producerSettings.properties(ProducerConfig.BATCH_SIZE_CONFIG))
    assertEquals("producer-client", producerSettings.properties(ProducerConfig.CLIENT_ID_CONFIG))
    assertEquals("3", producerSettings.properties(ProducerConfig.RETRIES_CONFIG))
    assertEquals("1", producerSettings.properties(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION))
    assertEquals("true", producerSettings.properties(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG))
    assertEquals("25", producerSettings.properties(ProducerConfig.LINGER_MS_CONFIG))
    assertEquals("5000", producerSettings.properties(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG))
    assertEquals("10000", producerSettings.properties(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG))
    assertTrue(producerSettings.failFastProduce)
    assertEquals(2.seconds, producerSettings.closeTimeout)

    val consumerSettings: ConsumerSettings[IO, String, String] = ConsumerSettings[IO, String, String](
      Deserializer.string[IO],
      Deserializer.string[IO]
    ).withBootstrapServers("localhost:9092")
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withClientId("consumer-client")
      .withGroupId("group")
      .withGroupInstanceId("instance")
      .withMaxPollRecords(10)
      .withMaxPollInterval(30.seconds)
      .withSessionTimeout(9.seconds)
      .withHeartbeatInterval(3.seconds)
      .withEnableAutoCommit(false)
      .withAutoCommitInterval(1.second)
      .withRequestTimeout(4.seconds)
      .withDefaultApiTimeout(6.seconds)
      .withIsolationLevel(IsolationLevel.ReadCommitted)
      .withAllowAutoCreateTopics(false)
      .withClientRack("rack-a")
      .withMaxPrefetchBatches(1)
      .withPollInterval(20.millis)
      .withPollTimeout(30.millis)
      .withCommitTimeout(2.seconds)
      .withCloseTimeout(3.seconds)
      .withRecordMetadata(record => s"${record.topic}:${record.offset}")

    assertEquals("earliest", consumerSettings.properties(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))
    assertEquals("false", consumerSettings.properties(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))
    assertEquals("read_committed", consumerSettings.properties(ConsumerConfig.ISOLATION_LEVEL_CONFIG))
    assertEquals("rack-a", consumerSettings.properties(ConsumerConfig.CLIENT_RACK_CONFIG))
    assertEquals(2, consumerSettings.maxPrefetchBatches)
    assertEquals("topic:12", consumerSettings.recordMetadata(ConsumerRecord("topic", 0, 12L, "k", "v")))

    val adminSettings: AdminClientSettings = AdminClientSettings("localhost:9092")
      .withClientId("admin-client")
      .withReconnectBackoff(10.millis)
      .withReconnectBackoffMax(20.millis)
      .withRetryBackoff(30.millis)
      .withConnectionsMaxIdle(40.seconds)
      .withRequestTimeout(5.seconds)
      .withMetadataMaxAge(6.seconds)
      .withRetries(2)
      .withCloseTimeout(1.second)

    assertEquals("localhost:9092", adminSettings.properties(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG))
    assertEquals("admin-client", adminSettings.properties(AdminClientConfig.CLIENT_ID_CONFIG))
    assertEquals("10", adminSettings.properties(AdminClientConfig.RECONNECT_BACKOFF_MS_CONFIG))
    assertEquals("20", adminSettings.properties(AdminClientConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG))
    assertEquals("30", adminSettings.properties(AdminClientConfig.RETRY_BACKOFF_MS_CONFIG))
    assertEquals("40000", adminSettings.properties(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG))
    assertEquals("5000", adminSettings.properties(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG))
    assertEquals("6000", adminSettings.properties(AdminClientConfig.METADATA_MAX_AGE_CONFIG))
    assertEquals("2", adminSettings.properties(AdminClientConfig.RETRIES_CONFIG))
    assertEquals(1.second, adminSettings.closeTimeout)

    val credentials = KafkaCredentialStore.fromPemStrings("ca\ncert", "private\nkey", "client\ncert")
    assertEquals("ca cert", producerSettings.withCredentials(credentials).properties("ssl.truststore.certificates"))
    assertEquals("private key", consumerSettings.withCredentials(credentials).properties("ssl.keystore.key"))
    assertEquals("client cert", adminSettings.withCredentials(credentials).properties("ssl.keystore.certificate.chain"))
  }

  @Test
  def committableOffsetsBatchAndTransactionalRecordsUsePublicCommitApi(): Unit = {
    val committed = new AtomicReference[List[Map[TopicPartition, OffsetAndMetadata]]](Nil)
    val commit: Map[TopicPartition, OffsetAndMetadata] => IO[Unit] = offsets =>
      IO {
        committed.set(offsets :: committed.get())
        ()
      }

    val topic0: TopicPartition = new TopicPartition("topic", 0)
    val topic1: TopicPartition = new TopicPartition("topic", 1)
    val offset0: CommittableOffset[IO] = CommittableOffset(topic0, new OffsetAndMetadata(10L, "first"), Some("group"), commit)
    val offset0Later: CommittableOffset[IO] = CommittableOffset(topic0, new OffsetAndMetadata(11L, "later"), Some("group"), commit)
    val offset1: CommittableOffset[IO] = CommittableOffset(topic1, new OffsetAndMetadata(20L, "other"), Some("group"), commit)

    val batch: CommittableOffsetBatch[IO] = CommittableOffsetBatch.empty[IO]
      .updated(offset0)
      .updated(offset0Later)
      .updated(offset1)

    assertEquals(Set("group"), batch.consumerGroupIds)
    assertFalse(batch.consumerGroupIdsMissing)
    assertEquals(2, batch.offsets.size)
    assertEquals(11L, batch.offsets(topic0).offset())

    batch.commit.unsafeRunSync()
    assertEquals(List(batch.offsets), committed.get())

    val missingGroup: CommittableOffset[IO] = CommittableOffset(
      new TopicPartition("topic", 2),
      new OffsetAndMetadata(1L),
      None,
      commit
    )
    val failedCommit = missingGroup.batch.commit.attempt.unsafeRunSync()
    failedCommit match {
      case Left(throwable) => assertTrue(throwable.isInstanceOf[ConsumerGroupException])
      case Right(_)        => fail("Expected committing without a consumer group to fail")
    }

    val record: ProducerRecord[String, String] = ProducerRecord("out", "key", "value")
    val producerRecords: ProducerRecords[String, String] = ProducerRecords.one(record)
    val committableRecords: CommittableProducerRecords[IO, String, String] = CommittableProducerRecords.one(record, offset1)
    val transactionalRecords: TransactionalProducerRecords[IO, String, String] = TransactionalProducerRecords.one(committableRecords)

    assertEquals(1, producerRecords.size)
    assertEquals(record, producerRecords(0))
    assertEquals(1, committableRecords.records.size)
    assertEquals(offset1, committableRecords.offset)
    assertEquals(1, transactionalRecords.size)
    assertEquals(committableRecords, transactionalRecords(0))
  }

  @Test
  def timestampsRepresentAllKafkaTimestampKinds(): Unit = {
    val created: Timestamp = Timestamp.createTime(100L)
    val appended: Timestamp = Timestamp.logAppendTime(200L)
    val unknown: Timestamp = Timestamp.unknownTime(300L)
    val empty: Timestamp = Timestamp.none

    assertEquals(Some(100L), created.createTime)
    assertEquals(None, created.logAppendTime)
    assertFalse(created.isEmpty)
    assertTrue(created.nonEmpty)

    assertEquals(Some(200L), appended.logAppendTime)
    assertEquals(None, appended.unknownTime)
    assertEquals(Some(300L), unknown.unknownTime)
    assertTrue(empty.isEmpty)
    assertFalse(empty.nonEmpty)
    assertTrue(created =!= appended)
    assertEquals("Timestamp()", empty.show)
  }

  private def bytes(value: String): Array[Byte] =
    value.getBytes(StandardCharsets.UTF_8)
}
