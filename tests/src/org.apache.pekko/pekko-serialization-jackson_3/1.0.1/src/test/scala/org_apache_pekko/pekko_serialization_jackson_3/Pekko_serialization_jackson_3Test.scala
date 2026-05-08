/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_serialization_jackson_3

import org.assertj.core.api.Assertions.assertThat

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Optional
import java.util.UUID

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Address
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.serialization.SerializerWithStringManifest
import org.apache.pekko.serialization.jackson.JacksonMigration
import org.apache.pekko.serialization.jackson.JacksonObjectMapperProvider
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

final case class JsonOrder(
    id: String,
    createdAt: Instant,
    processingTimeout: FiniteDuration,
    serviceAddress: Address,
    tags: Vector[String],
    attributes: Map[String, String],
    assignedTo: Option[String]
)

final case class CborTelemetry(
    node: String,
    samples: Vector[Int],
    lastObservedAt: Instant,
    comment: Option[String]
)

final case class LongJsonPayload(id: String, payload: String)

final case class LongCborPayload(id: String, payload: String)

final case class ManifestlessEvent(id: String, value: Int)

final case class NumberBox(count: Int)

final case class VersionedProfile(id: String, displayName: String, active: Boolean)

class VersionedProfileMigration extends JacksonMigration {
  override def currentVersion: Int = 2

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    if (fromVersion <= 1) {
      val profile: ObjectNode = json.asInstanceOf[ObjectNode]
      val name: JsonNode = profile.remove("name")
      profile.put("displayName", name.asText())
      profile.put("active", true)
    }
    json
  }
}

class Pekko_serialization_jackson_3Test {
  private val BaseConfig: String = """
    pekko.loglevel = "WARNING"
    pekko.stdout-loglevel = "WARNING"
    pekko.actor.allow-java-serialization = off
    pekko.actor.warn-about-java-serializer-usage = on
    pekko.actor.serialization-bindings {
      "org_apache_pekko.pekko_serialization_jackson_3.JsonOrder" = jackson-json
      "org_apache_pekko.pekko_serialization_jackson_3.CborTelemetry" = jackson-cbor
      "org_apache_pekko.pekko_serialization_jackson_3.LongJsonPayload" = jackson-json
      "org_apache_pekko.pekko_serialization_jackson_3.LongCborPayload" = jackson-cbor
      "org_apache_pekko.pekko_serialization_jackson_3.ManifestlessEvent" = jackson-json
      "org_apache_pekko.pekko_serialization_jackson_3.NumberBox" = jackson-json
      "org_apache_pekko.pekko_serialization_jackson_3.VersionedProfile" = jackson-json
    }
    """

  @Test
  def jsonSerializerRoundTripsScalaAndPekkoTypes(): Unit = withActorSystem("json", BaseConfig) { system =>
    val order: JsonOrder = JsonOrder(
      id = "order-1",
      createdAt = Instant.parse("2024-01-02T03:04:05Z"),
      processingTimeout = 250.millis,
      serviceAddress = Address("pekko", "orders", "127.0.0.1", 2552),
      tags = Vector("primary", "express"),
      attributes = Map("region" -> "emea", "priority" -> "high"),
      assignedTo = Some("worker-a")
    )

    val serializer: SerializerWithStringManifest = jacksonSerializerFor(system, order)
    val bytes: Array[Byte] = serializer.toBinary(order)
    val json: String = new String(bytes, StandardCharsets.UTF_8)
    val restored: JsonOrder = serializer.fromBinary(bytes, serializer.manifest(order)).asInstanceOf[JsonOrder]

    assertThat(serializer.identifier).isEqualTo(31)
    assertThat(json).contains("order-1", "primary", "worker-a")
    assertThat(restored).isEqualTo(order)
  }

  @Test
  def cborSerializerRoundTripsStructuredMessagesThroughSerializationExtension(): Unit = withActorSystem("cbor", BaseConfig) {
    system =>
      val telemetry: CborTelemetry = CborTelemetry(
        node = "node-a",
        samples = Vector(3, 5, 8, 13),
        lastObservedAt = Instant.parse("2024-02-03T04:05:06Z"),
        comment = None
      )
      val serialization = SerializationExtension(system)
      val serializer: SerializerWithStringManifest = jacksonSerializerFor(system, telemetry)
      val manifest: String = serializer.manifest(telemetry)
      val bytes: Array[Byte] = serialization.serialize(telemetry).get
      val restored: CborTelemetry = serialization.deserialize(bytes, serializer.identifier, manifest).get.asInstanceOf[CborTelemetry]

      assertThat(serializer.identifier).isEqualTo(33)
      assertThat(restored).isEqualTo(telemetry)
  }

  @Test
  def jsonSerializerCompressesLargePayloadsWithGzip(): Unit = withActorSystem(
    "gzip",
    BaseConfig + """
      pekko.serialization.jackson.jackson-json.compression.algorithm = gzip
      pekko.serialization.jackson.jackson-json.compression.compress-larger-than = 1 bytes
      """
  ) { system =>
    val value: LongJsonPayload = LongJsonPayload("large-json", "abcdefghij" * 200)
    val serializer: SerializerWithStringManifest = jacksonSerializerFor(system, value)
    val bytes: Array[Byte] = serializer.toBinary(value)
    val restored: LongJsonPayload = serializer.fromBinary(bytes, serializer.manifest(value)).asInstanceOf[LongJsonPayload]

    assertThat(isGZipped(bytes)).isTrue
    assertThat(restored).isEqualTo(value)
  }

  @Test
  def cborSerializerCompressesLargePayloadsWithLz4(): Unit = withActorSystem(
    "lz4",
    BaseConfig + """
      pekko.serialization.jackson.jackson-cbor.compression.algorithm = lz4
      pekko.serialization.jackson.jackson-cbor.compression.compress-larger-than = 1 bytes
      """
  ) { system =>
    val value: LongCborPayload = LongCborPayload("large-cbor", "klmnopqrst" * 200)
    val serializer: SerializerWithStringManifest = jacksonSerializerFor(system, value)
    val bytes: Array[Byte] = serializer.toBinary(value)
    val restored: LongCborPayload = serializer.fromBinary(bytes, serializer.manifest(value)).asInstanceOf[LongCborPayload]

    assertThat(isLZ4Compressed(bytes)).isTrue
    assertThat(restored).isEqualTo(value)
  }

  @Test
  def serializerCanUseConfiguredDeserializationTypeInsteadOfManifestType(): Unit = withActorSystem(
    "manifestless",
    BaseConfig + """
      pekko.serialization.jackson.jackson-json.type-in-manifest = off
      pekko.serialization.jackson.jackson-json.deserialization-type = "org_apache_pekko.pekko_serialization_jackson_3.ManifestlessEvent"
      """
  ) { system =>
    val event: ManifestlessEvent = ManifestlessEvent("event-1", 42)
    val serializer: SerializerWithStringManifest = jacksonSerializerFor(system, event)
    val manifest: String = serializer.manifest(event)
    val restored: ManifestlessEvent = serializer.fromBinary(serializer.toBinary(event), manifest).asInstanceOf[ManifestlessEvent]

    assertThat(manifest).isEmpty
    assertThat(restored).isEqualTo(event)
  }

  @Test
  def jsonSerializerMigratesOlderPayloadsBeforeDeserialization(): Unit = withActorSystem(
    "migration",
    BaseConfig + """
      pekko.serialization.jackson.migrations {
        "org_apache_pekko.pekko_serialization_jackson_3.VersionedProfile" = "org_apache_pekko.pekko_serialization_jackson_3.VersionedProfileMigration"
      }
      """
  ) { system =>
    val currentProfile: VersionedProfile = VersionedProfile("profile-1", "Ada", active = true)
    val serializer: SerializerWithStringManifest = jacksonSerializerFor(system, currentProfile)
    val currentManifest: String = serializer.manifest(currentProfile)
    val oldPayload: Array[Byte] = """{"id":"profile-1","name":"Ada"}""".getBytes(StandardCharsets.UTF_8)
    val oldManifest: String = currentManifest.replace("#2", "#1")
    val restored: VersionedProfile = serializer.fromBinary(oldPayload, oldManifest).asInstanceOf[VersionedProfile]

    assertThat(currentManifest).endsWith("#2")
    assertThat(restored).isEqualTo(currentProfile)
  }

  @Test
  def objectMapperProviderCreatesCachedMappersWithConfiguredFeatures(): Unit = withActorSystem(
    "object-mapper",
    BaseConfig + """
      pekko.serialization.jackson.jackson-json.json-write-features {
        WRITE_NUMBERS_AS_STRINGS = on
      }
      """
  ) { system =>
    val provider: JacksonObjectMapperProvider = JacksonObjectMapperProvider.get(system)
    val noJsonFactory: Optional[JsonFactory] = Optional.empty()
    val mapper = provider.getOrCreate("jackson-json", noJsonFactory)
    val cachedMapper = provider.getOrCreate("jackson-json", noJsonFactory)
    val json: String = mapper.writeValueAsString(NumberBox(7))
    val restored: NumberBox = mapper.readValue(json, classOf[NumberBox])

    assertThat(cachedMapper).isSameAs(mapper)
    assertThat(json).contains("\"count\":\"7\"")
    assertThat(restored).isEqualTo(NumberBox(7))
  }

  private def jacksonSerializerFor(system: ActorSystem, value: AnyRef): SerializerWithStringManifest = {
    SerializationExtension(system).findSerializerFor(value).asInstanceOf[SerializerWithStringManifest]
  }

  private def isGZipped(bytes: Array[Byte]): Boolean = {
    bytes.length >= 2 && bytes(0) == 0x1f.toByte && bytes(1) == 0x8b.toByte
  }

  private def isLZ4Compressed(bytes: Array[Byte]): Boolean = {
    bytes.length >= 4 && bytes(0) == 0x87.toByte && bytes(1) == 0xd9.toByte && bytes(2) == 0x6d.toByte && bytes(3) == 0xf6.toByte
  }

  private def withActorSystem(testName: String, configText: String)(body: ActorSystem => Unit): Unit = {
    val config: Config = ConfigFactory.parseString(configText).withFallback(ConfigFactory.load())
    val system: ActorSystem = ActorSystem(s"JacksonSerialization-$testName-${UUID.randomUUID()}", config)
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
