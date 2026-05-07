/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_serialization_jackson_3

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.GZIPInputStream

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.AddressFromURIString
import akka.serialization.SerializationExtension
import akka.serialization.Serializers
import akka.serialization.jackson.JacksonMigration
import akka.serialization.jackson.JacksonObjectMapperProvider

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Akka_serialization_jackson_3Test {
  import Akka_serialization_jackson_3Test._

  @Test
  def objectMapperProviderCachesMappersAndInstallsAkkaAndScalaModules(): Unit = {
    withActorSystem { system =>
      val provider: JacksonObjectMapperProvider = JacksonObjectMapperProvider(system)
      val mapper = provider.getOrCreate("jackson-json", None)
      val cachedMapper = provider.getOrCreate("jackson-json", None)
      val freshMapper = provider.create("jackson-json", None)

      assertThat(cachedMapper).isSameAs(mapper)
      assertThat(freshMapper).isNotSameAs(mapper)
      assertThat(mapper.readTree("{'feature':'single-quotes'}").get("feature").asText()).isEqualTo("single-quotes")

      val envelope = MapperEnvelope(
        name = "mapper-roundtrip",
        duration = 250.millis,
        address = AddressFromURIString("akka://mapper-system@127.0.0.1:25520"),
        createdAt = Instant.parse("2024-01-02T03:04:05Z"),
        tags = List("akka", "jackson", "scala3"),
        metadata = Some(Map("format" -> "json", "module" -> "scala")))

      val json: String = mapper.writeValueAsString(envelope)
      val restored: MapperEnvelope = mapper.readValue(json, classOf[MapperEnvelope])

      assertThat(restored).isEqualTo(envelope)
    }
  }

  @Test
  def jsonSerializerRoundTripsScalaMessagesAndCompressedPayloads(): Unit = {
    withActorSystem { system =>
      val serialization = SerializationExtension(system)
      val payload = JsonPayload(
        id = "json-1",
        values = Vector.range(1, 16),
        note = Some("payload large enough to trigger gzip compression" * 8))

      val serializer = serialization.findSerializerFor(payload)
      val manifest: String = Serializers.manifestFor(serializer, payload)
      val bytes: Array[Byte] = serializer.toBinary(payload)
      val restored = serialization.deserialize(bytes, serializer.identifier, manifest).get

      assertThat(manifest).isEqualTo(JsonPayloadClassName)
      assertThat(bytes).isNotEmpty
      assertThat(isGzip(bytes)).isTrue
      assertThat(restored).isEqualTo(payload)
    }
  }

  @Test
  def cborSerializerCanDeserializeWithConfiguredTypeInsteadOfManifestClassName(): Unit = {
    withActorSystem { system =>
      val serialization = SerializationExtension(system)
      val payload = CborPayload(id = "cbor-1", count = 42, enabled = true)

      val serializer = serialization.findSerializerFor(payload)
      val manifest: String = Serializers.manifestFor(serializer, payload)
      val bytes: Array[Byte] = serializer.toBinary(payload)
      val restored = serialization.deserialize(bytes, serializer.identifier, manifest).get

      assertThat(manifest).isEmpty
      assertThat(bytes).isNotEmpty
      assertThat(restored).isEqualTo(payload)
    }
  }

  @Test
  def jsonSerializerRoundTripsClassicActorRefsWithAkkaModule(): Unit = {
    withActorSystem { system =>
      val serialization = SerializationExtension(system)
      val payload = ActorRefPayload(name = "dead-letters-recipient", recipient = system.deadLetters)

      val serializer = serialization.findSerializerFor(payload)
      val manifest: String = Serializers.manifestFor(serializer, payload)
      val bytes: Array[Byte] = serialization.serialize(payload).get
      val restored = serialization.deserialize(bytes, serializer.identifier, manifest).get.asInstanceOf[ActorRefPayload]

      assertThat(manifest).isEqualTo(ActorRefPayloadClassName)
      assertThat(bytes).isNotEmpty
      assertThat(restored.name).isEqualTo(payload.name)
      assertThat(restored.recipient).isNotNull
      assertThat(restored.recipient.path.toSerializationFormat)
        .isEqualTo(system.deadLetters.path.toSerializationFormat)
    }
  }

  @Test
  def jacksonMigrationTransformsOlderJsonSchemaBeforeDeserialization(): Unit = {
    withActorSystem { system =>
      val serialization = SerializationExtension(system)
      val current = MigratingMessage(newName = "Grace Hopper")
      val serializer = serialization.findSerializerFor(current)
      val currentManifest: String = Serializers.manifestFor(serializer, current)
      val oldJsonBytes: Array[Byte] = """{"oldName":"Ada Lovelace"}""".getBytes(StandardCharsets.UTF_8)
      val oldManifest: String = s"$MigratingMessageClassName#1"
      val restored = serialization.deserialize(oldJsonBytes, serializer.identifier, oldManifest).get

      assertThat(currentManifest).isEqualTo(s"$MigratingMessageClassName#2")
      assertThat(restored).isEqualTo(MigratingMessage(newName = "Ada Lovelace"))
    }
  }

  @Test
  def jsonSerializerRestoresScalaCaseObjectSingletonsFromManifest(): Unit = {
    withActorSystem { system =>
      val serialization = SerializationExtension(system)
      val payload = SingletonNotification

      val serializer = serialization.findSerializerFor(payload)
      val manifest: String = Serializers.manifestFor(serializer, payload)
      val bytes: Array[Byte] = serialization.serialize(payload).get
      val restored = serialization.deserialize(bytes, serializer.identifier, manifest).get

      assertThat(manifest).isEqualTo(SingletonNotificationClassName)
      assertThat(bytes).isNotEmpty
      assertThat(restored).isSameAs(payload)
    }
  }

  private def withActorSystem(test: ActorSystem => Unit): Unit = {
    val systemName: String = s"akka-jackson-test-${System.nanoTime()}"
    val system: ActorSystem = ActorSystem(systemName, TestConfig)
    try test(system)
    finally Await.result(system.terminate(), 10.seconds)
  }

  private def isGzip(bytes: Array[Byte]): Boolean = {
    val stream = new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))
    try stream.read() != -1
    finally stream.close()
  }
}

object Akka_serialization_jackson_3Test {
  val PackageName = "com_typesafe_akka.akka_serialization_jackson_3"
  val JsonPayloadClassName = s"$PackageName.JsonPayload"
  val CborPayloadClassName = s"$PackageName.CborPayload"
  val ActorRefPayloadClassName = s"$PackageName.ActorRefPayload"
  val MigratingMessageClassName = s"$PackageName.MigratingMessage"
  val SingletonNotificationClassName = SingletonNotification.getClass.getName
  val RenameOldNameMigrationClassName = s"$PackageName.RenameOldNameMigration"

  val NativeFallbackConfig: Config = ConfigFactory.parseString("""
      akka.version = "2.6.21"
      akka.loggers = []
      akka.logging-filter = "akka.event.DefaultLoggingFilter"
      akka.loggers-dispatcher = "akka.actor.default-dispatcher"
      akka.logger-startup-timeout = 5s
      akka.loglevel = "INFO"
      akka.stdout-loglevel = "WARNING"
      akka.log-config-on-start = off
      akka.log-dead-letters = 10
      akka.log-dead-letters-during-shutdown = off
      akka.log-dead-letters-suspend-duration = 5 minutes
      akka.library-extensions = ["akka.serialization.SerializationExtension$"]
      akka.extensions = []
      akka.jvm-exit-on-fatal-error = on
      akka.jvm-shutdown-hooks = on
      akka.fail-mixed-versions = on

      akka.actor {
        provider = "local"
        guardian-supervisor-strategy = "akka.actor.DefaultSupervisorStrategy"
        creation-timeout = 20s
        unstarted-push-timeout = 10s
        serialize-messages = off
        serialize-creators = off
        no-serialization-verification-needed-class-prefix = ["akka."]
        allow-java-serialization = off
        warn-about-java-serializer-usage = on
        warn-on-no-serialization-verification = on

        debug {
          receive = off
          autoreceive = off
          lifecycle = off
          fsm = off
          event-stream = off
          unhandled = off
          router-misconfiguration = off
        }

        deployment {
          default {
            virtual-nodes-factor = 10
          }
        }

        default-dispatcher {
          type = "Dispatcher"
          executor = "default-executor"
          default-executor {
            fallback = "fork-join-executor"
          }
          fork-join-executor {
            parallelism-min = 8
            parallelism-factor = 1.0
            parallelism-max = 64
            task-peeking-mode = "FIFO"
          }
          thread-pool-executor {
            fixed-pool-size = off
            core-pool-size-min = 8
            core-pool-size-factor = 3.0
            core-pool-size-max = 64
            max-pool-size-min = 8
            max-pool-size-factor = 3.0
            max-pool-size-max = 64
            task-queue-size = -1
            task-queue-type = "linked"
            allow-core-timeout = on
          }
          shutdown-timeout = 1s
          throughput = 5
          throughput-deadline-time = 0ms
          attempt-teamwork = on
          mailbox-requirement = ""
        }

        internal-dispatcher {
          type = "Dispatcher"
          executor = "fork-join-executor"
          throughput = 5
          fork-join-executor {
            parallelism-min = 4
            parallelism-factor = 1.0
            parallelism-max = 64
          }
        }

        default-blocking-io-dispatcher {
          type = "Dispatcher"
          executor = "thread-pool-executor"
          throughput = 1
          thread-pool-executor {
            fixed-pool-size = 16
          }
        }

        default-mailbox {
          mailbox-type = "akka.dispatch.UnboundedMailbox"
        }

        serializers {
          java = "akka.serialization.JavaSerializer"
          bytes = "akka.serialization.ByteArraySerializer"
          primitive-long = "akka.serialization.LongSerializer"
          primitive-int = "akka.serialization.IntSerializer"
          primitive-string = "akka.serialization.StringSerializer"
          primitive-bytestring = "akka.serialization.ByteStringSerializer"
          primitive-boolean = "akka.serialization.BooleanSerializer"
          jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
          jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
        }
      }

      akka.serialization.jackson {
        jackson-modules += "akka.serialization.jackson.AkkaJacksonModule"
        jackson-modules += "akka.serialization.jackson.AkkaTypedJacksonModule"
        jackson-modules += "akka.serialization.jackson.AkkaStreamJacksonModule"
        jackson-modules += "com.fasterxml.jackson.module.paramnames.ParameterNamesModule"
        jackson-modules += "com.fasterxml.jackson.datatype.jdk8.Jdk8Module"
        jackson-modules += "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"
        jackson-modules += "com.fasterxml.jackson.module.scala.DefaultScalaModule"
        visibility {
          FIELD = ANY
        }
        type-in-manifest = on
      }
      """)

  val TestConfig: Config = ConfigFactory
    .parseString(s"""
      akka.actor.serialization-bindings {
        "$JsonPayloadClassName" = jackson-json
        "$CborPayloadClassName" = jackson-cbor
        "$ActorRefPayloadClassName" = jackson-json
        "$MigratingMessageClassName" = jackson-json
        "$SingletonNotificationClassName" = jackson-json
      }
      akka.serialization.jackson {
        allowed-class-prefix = ["$PackageName."]
        jackson-json {
          compression {
            algorithm = gzip
            compress-larger-than = 1 byte
          }
          json-read-features {
            ALLOW_SINGLE_QUOTES = on
          }
        }
        jackson-cbor {
          type-in-manifest = off
          deserialization-type = "$CborPayloadClassName"
        }
        migrations {
          "$MigratingMessageClassName" = "$RenameOldNameMigrationClassName"
        }
      }
      """)
    .withFallback(NativeFallbackConfig)
    .withFallback(ConfigFactory.load())
    .resolve()
}

final case class MapperEnvelope(
    name: String,
    duration: FiniteDuration,
    address: Address,
    createdAt: Instant,
    tags: List[String],
    metadata: Option[Map[String, String]])

final case class JsonPayload(id: String, values: Vector[Int], note: Option[String])

final case class CborPayload(id: String, count: Int, enabled: Boolean)

final case class ActorRefPayload(name: String, recipient: ActorRef)

final case class MigratingMessage(newName: String)

case object SingletonNotification

class RenameOldNameMigration extends JacksonMigration {
  override def currentVersion: Int = 2

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    val objectNode: ObjectNode = json.asInstanceOf[ObjectNode]
    if (fromVersion == 1) {
      val oldName = objectNode.remove("oldName")
      objectNode.set[JsonNode]("newName", oldName)
    }
    objectNode
  }
}
