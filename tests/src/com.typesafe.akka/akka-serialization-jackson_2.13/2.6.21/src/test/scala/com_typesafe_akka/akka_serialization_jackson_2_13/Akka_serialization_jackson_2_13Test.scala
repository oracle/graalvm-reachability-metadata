/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_serialization_jackson_2_13

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.ExtendedActorSystem
import akka.serialization.Serialization
import akka.serialization.SerializationExtension
import akka.serialization.SerializerWithStringManifest
import akka.serialization.jackson.JacksonMigration
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class Akka_serialization_jackson_2_13Test {
  @Test
  def cborSerializerRoundTripsAkkaModuleTypes(): Unit = {
    val config: Config = ConfigFactory.parseString(
      s"""
        akka.loglevel = "WARNING"
        akka.actor.allow-java-serialization = off
        akka.actor.serialization-bindings {
          "${classOf[AkkaModulePayload].getName}" = jackson-cbor
        }
        """
    ).withFallback(ConfigFactory.load())

    withActorSystem("JacksonCborAkkaModules", config) { system =>
      val serialization = SerializationExtension(system)
      val serializer = serialization.serializerFor(classOf[AkkaModulePayload]).asInstanceOf[SerializerWithStringManifest]
      val payload: AkkaModulePayload = AkkaModulePayload(
        destination = system.deadLetters,
        address = Address("akka", system.name, "127.0.0.1", 2552),
        timeout = 250.millis,
        labels = Vector("cbor", "actor-ref", "address", "duration")
      )

      val (manifest: String, bytes: Array[Byte], copy: AkkaModulePayload) =
        Serialization.withTransportInformation(system.asInstanceOf[ExtendedActorSystem]) { () =>
          val manifest: String = serializer.manifest(payload)
          val bytes: Array[Byte] = serializer.toBinary(payload)
          val copy = serializer.fromBinary(bytes, manifest).asInstanceOf[AkkaModulePayload]
          (manifest, bytes, copy)
        }

      assertThat(manifest).isNotEmpty()
      assertThat(bytes.length).isGreaterThan(0)
      assertThat(copy.destination.path.toString).isEqualTo(payload.destination.path.toString)
      assertThat(copy.address).isEqualTo(payload.address)
      assertThat(copy.timeout).isEqualTo(payload.timeout)
      assertThat(copy.labels).isEqualTo(payload.labels)
    }
  }

  @Test
  def jsonSerializerAppliesConfiguredMigrationForOlderManifest(): Unit = {
    val currentClassName: String = classOf[MigratedOrder].getName
    val legacyClassName: String = s"$currentClassName.Legacy"
    val config: Config = ConfigFactory.parseString(
      s"""
        akka.loglevel = "WARNING"
        akka.actor.allow-java-serialization = off
        akka.actor.serialization-bindings {
          "$currentClassName" = jackson-json
        }
        akka.serialization.jackson.migrations {
          "$currentClassName" = "${classOf[MigratedOrderMigration].getName}"
          "$legacyClassName" = "${classOf[MigratedOrderMigration].getName}"
        }
        """
    ).withFallback(ConfigFactory.load())

    withActorSystem("JacksonJsonMigration", config) { system =>
      val serialization: Serialization = SerializationExtension(system)
      val serializer: SerializerWithStringManifest =
        serialization.serializerFor(classOf[MigratedOrder]).asInstanceOf[SerializerWithStringManifest]
      val legacyJson: Array[Byte] = """{"orderId":"order-17","quantity":2}""".getBytes(StandardCharsets.UTF_8)

      val manifest: String = serializer.manifest(MigratedOrder(id = "order-17", quantity = 2, priority = "standard"))
      val migrated: MigratedOrder = serializer.fromBinary(legacyJson, s"$legacyClassName#1").asInstanceOf[MigratedOrder]

      assertThat(manifest).isEqualTo(s"$currentClassName#2")
      assertThat(migrated).isEqualTo(MigratedOrder(id = "order-17", quantity = 2, priority = "standard"))
    }
  }

  private def withActorSystem(name: String, config: Config)(test: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name, config)
    try {
      test(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}

final case class AkkaModulePayload(
    destination: ActorRef,
    address: Address,
    timeout: FiniteDuration,
    labels: Vector[String]
)

final case class MigratedOrder(id: String, quantity: Int, priority: String)

class MigratedOrderMigration extends JacksonMigration {
  override def currentVersion(): Int = 2

  override def transformClassName(fromVersion: Int, className: String): String = classOf[MigratedOrder].getName

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    val root: ObjectNode = json.deepCopy[ObjectNode]()
    val orderId: JsonNode = root.remove("orderId")
    root.set[JsonNode]("id", orderId)
    root.put("priority", "standard")
    root
  }
}
