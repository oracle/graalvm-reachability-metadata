/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_serialization_jackson_2_13

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.serialization.SerializerWithStringManifest
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class JacksonTypeInManifestOffTest {
  @Test
  def jsonSerializerUsesConfiguredDeserializationTypeWhenManifestIsDisabled(): Unit = {
    val config: Config = ConfigFactory.parseString(
      s"""
        akka.loglevel = "WARNING"
        akka.actor.allow-java-serialization = off
        akka.actor.serializers {
          jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
        }
        akka.actor.serialization-bindings {
          "${classOf[ManifestlessPayload].getName}" = jackson-json
        }
        akka.serialization.jackson.jackson-json {
          type-in-manifest = off
          deserialization-type = "${classOf[ManifestlessPayload].getName}"
          compression {
            algorithm = lz4
            compress-larger-than = 1 byte
          }
        }
        """
    ).withFallback(ConfigFactory.load())

    withActorSystem("JacksonTypeInManifestOff", config) { system =>
      val serialization = SerializationExtension(system)
      val serializer = serialization.serializerFor(classOf[ManifestlessPayload]).asInstanceOf[SerializerWithStringManifest]
      val payload: ManifestlessPayload = ManifestlessPayload(
        name = "manifestless",
        revision = 3,
        tags = Vector("akka", "jackson", "native-image"),
        description = "lz4-compressed-json-payload-" * 80
      )

      val manifest: String = serializer.manifest(payload)
      val bytes: Array[Byte] = serializer.toBinary(payload)
      val copy = serializer.fromBinary(bytes, manifest).asInstanceOf[ManifestlessPayload]

      assertThat(manifest).isEmpty()
      assertThat(bytes.length).isGreaterThan(0)
      assertThat(copy).isEqualTo(payload)
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

final case class ManifestlessPayload(name: String, revision: Int, tags: Vector[String], description: String)
