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
  val NativeTestLoggingFilterClassName = classOf[NativeTestLoggingFilter].getName

  private val DefaultAkkaConfigText = """
    akka.version = "2.6.21"

    akka {
      home = ""
      loggers = []
      logging-filter = "__NATIVE_TEST_LOGGING_FILTER__"
      loggers-dispatcher = "akka.actor.default-dispatcher"
      logger-startup-timeout = 5s
      loglevel = "OFF"
      stdout-loglevel = "OFF"
      log-config-on-start = off
      log-dead-letters = 10
      log-dead-letters-during-shutdown = off
      log-dead-letters-suspend-duration = 5 minutes
      library-extensions = ["akka.serialization.SerializationExtension$"]
      extensions = []
      daemonic = off
      jvm-exit-on-fatal-error = on
      jvm-shutdown-hooks = on
      fail-mixed-versions = on

      actor {
        provider = "local"
        guardian-supervisor-strategy = "akka.actor.DefaultSupervisorStrategy"
        creation-timeout = 20s
        serialize-messages = off
        serialize-creators = off
        no-serialization-verification-needed-class-prefix = ["akka."]
        unstarted-push-timeout = 10s
        allow-java-serialization = off
        warn-about-java-serializer-usage = on
        warn-on-no-serialization-verification = on

        deployment.default {
          dispatcher = ""
          mailbox = ""
          router = "from-code"
          nr-of-instances = 1
          within = 5 seconds
          virtual-nodes-factor = 10
          tail-chopping-router.interval = 10 milliseconds
          routees.paths = []
        }

        default-dispatcher {
          type = "Dispatcher"
          executor = "default-executor"
          default-executor.fallback = "fork-join-executor"
          fork-join-executor {
            parallelism-min = 8
            parallelism-factor = 1.0
            parallelism-max = 64
            task-peeking-mode = "FIFO"
          }
          thread-pool-executor {
            keep-alive-time = 60s
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
          thread-pool-executor.fixed-pool-size = 16
        }

        default-mailbox {
          mailbox-type = "akka.dispatch.UnboundedMailbox"
          mailbox-capacity = 1000
          mailbox-push-timeout-time = 10s
          stash-capacity = -1
        }

        mailbox {
          requirements {
            "akka.dispatch.UnboundedMessageQueueSemantics" = akka.actor.mailbox.unbounded-queue-based
            "akka.dispatch.BoundedMessageQueueSemantics" = akka.actor.mailbox.bounded-queue-based
            "akka.dispatch.DequeBasedMessageQueueSemantics" = akka.actor.mailbox.unbounded-deque-based
            "akka.dispatch.UnboundedDequeBasedMessageQueueSemantics" = akka.actor.mailbox.unbounded-deque-based
            "akka.dispatch.BoundedDequeBasedMessageQueueSemantics" = akka.actor.mailbox.bounded-deque-based
            "akka.dispatch.MultipleConsumerSemantics" = akka.actor.mailbox.unbounded-queue-based
            "akka.dispatch.ControlAwareMessageQueueSemantics" = akka.actor.mailbox.unbounded-control-aware-queue-based
            "akka.dispatch.UnboundedControlAwareMessageQueueSemantics" = akka.actor.mailbox.unbounded-control-aware-queue-based
            "akka.dispatch.BoundedControlAwareMessageQueueSemantics" = akka.actor.mailbox.bounded-control-aware-queue-based
            "akka.event.LoggerMessageQueueSemantics" = akka.actor.mailbox.logger-queue
          }
          unbounded-queue-based.mailbox-type = "akka.dispatch.UnboundedMailbox"
          bounded-queue-based.mailbox-type = "akka.dispatch.BoundedMailbox"
          unbounded-deque-based.mailbox-type = "akka.dispatch.UnboundedDequeBasedMailbox"
          bounded-deque-based.mailbox-type = "akka.dispatch.BoundedDequeBasedMailbox"
          unbounded-control-aware-queue-based.mailbox-type = "akka.dispatch.UnboundedControlAwareMailbox"
          bounded-control-aware-queue-based.mailbox-type = "akka.dispatch.BoundedControlAwareMailbox"
          logger-queue.mailbox-type = "akka.event.LoggerMailboxType"
        }

        debug {
          receive = off
          autoreceive = off
          lifecycle = off
          fsm = off
          event-stream = off
          unhandled = off
          router-misconfiguration = off
        }

        serializers {
          jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
          jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
          jackson-cbor-264 = "akka.serialization.jackson.JacksonJsonSerializer"
        }

        serialization-identifiers {
          jackson-json = 31
          jackson-cbor = 33
          jackson-cbor-264 = 32
        }
      }

      serialization.jackson {
        jackson-modules += "akka.serialization.jackson.AkkaJacksonModule"
        jackson-modules += "akka.serialization.jackson.AkkaTypedJacksonModule"
        jackson-modules += "akka.serialization.jackson.AkkaStreamJacksonModule"
        jackson-modules += "com.fasterxml.jackson.module.paramnames.ParameterNamesModule"
        jackson-modules += "com.fasterxml.jackson.datatype.jdk8.Jdk8Module"
        jackson-modules += "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"
        jackson-modules += "com.fasterxml.jackson.module.scala.DefaultScalaModule"

        verbose-debug-logging = off
        migrations {}

        serialization-features {
          WRITE_DATES_AS_TIMESTAMPS = off
          WRITE_DURATIONS_AS_TIMESTAMPS = off
          FAIL_ON_EMPTY_BEANS = off
        }

        deserialization-features {
          FAIL_ON_UNKNOWN_PROPERTIES = off
        }

        mapper-features {}
        json-parser-features {}
        json-generator-features {}
        stream-read-features {}
        stream-write-features {}
        json-read-features {}
        json-write-features {}

        visibility {
          FIELD = ANY
        }

        whitelist-class-prefix = []
        allowed-class-prefix = ${akka.serialization.jackson.whitelist-class-prefix}

        compression {
          algorithm = off
          compress-larger-than = 0 KiB
        }

        type-in-manifest = on
        deserialization-type = ""
        jackson-json {}
        jackson-cbor {}
        jackson-cbor-264 = ${akka.serialization.jackson.jackson-cbor}
      }

      scheduler {
        tick-duration = 10ms
        ticks-per-wheel = 512
        implementation = akka.actor.LightArrayRevolverScheduler
        shutdown-timeout = 5s
      }

      coordinated-shutdown {
        default-phase-timeout = 5 s
        terminate-actor-system = on
        exit-jvm = off
        exit-code = 0
        run-by-jvm-shutdown-hook = on
        run-by-actor-system-terminate = on

        reason-overrides {
          "akka.actor.CoordinatedShutdown$ClusterDowningReason$" {
            exit-code = -1
          }
          "akka.actor.CoordinatedShutdown$ClusterJoinUnsuccessfulReason$" {
            exit-code = -1
          }
        }

        phases {
          before-service-unbind {}
          service-unbind.depends-on = [before-service-unbind]
          service-requests-done.depends-on = [service-unbind]
          service-stop.depends-on = [service-requests-done]
          before-cluster-shutdown.depends-on = [service-stop]
          cluster-sharding-shutdown-region {
            timeout = 10 s
            depends-on = [before-cluster-shutdown]
          }
          cluster-leave.depends-on = [cluster-sharding-shutdown-region]
          cluster-exiting {
            timeout = 10 s
            depends-on = [cluster-leave]
          }
          cluster-exiting-done.depends-on = [cluster-exiting]
          cluster-shutdown.depends-on = [cluster-exiting-done]
          before-actor-system-terminate.depends-on = [cluster-shutdown]
          actor-system-terminate {
            timeout = 10 s
            depends-on = [before-actor-system-terminate]
          }
        }
      }
    }
    """

  private val DefaultAkkaConfig: Config =
    ConfigFactory.parseString(DefaultAkkaConfigText.replace("__NATIVE_TEST_LOGGING_FILTER__", NativeTestLoggingFilterClassName))

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
    .withFallback(DefaultAkkaConfig)
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

final class NativeTestLoggingFilter(
    settings: akka.actor.ActorSystem.Settings,
    eventStream: akka.event.EventStream)
    extends akka.event.DefaultLoggingFilter(settings, eventStream)
