/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_serialization_jackson_2_13

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.serialization.Serialization
import akka.serialization.SerializationExtension
import akka.serialization.Serializer
import akka.serialization.Serializers
import akka.serialization.jackson.JacksonObjectMapperProvider
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.beans.BeanProperty
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class Akka_serialization_jackson_2_13Test {
  import Akka_serialization_jackson_2_13Test._

  @Test
  def jsonBindingSerializesAndDeserializesConfiguredMessage(): Unit = {
    withActorSystem("AkkaJacksonJsonBindingTest") { system =>
      val serialization: Serialization = SerializationExtension(system)
      val message: JsonMessage = new JsonMessage("json-message", 42)
      val serializer: Serializer = serialization.findSerializerFor(message)

      assertEquals(JacksonJsonSerializerClassName, serializer.getClass.getName)

      val bytes: Array[Byte] = serialization.serialize(message).get
      val jsonPayload: String = new String(bytes, StandardCharsets.UTF_8)
      assertTrue(jsonPayload.contains("json-message"))
      assertTrue(jsonPayload.contains("42"))

      val manifest: String = Serializers.manifestFor(serializer, message)
      val decoded: JsonMessage =
        serialization.deserialize(bytes, serializer.identifier, manifest).get.asInstanceOf[JsonMessage]
      assertEquals(message.id, decoded.id)
      assertEquals(message.count, decoded.count)
    }
  }

  @Test
  def cborBindingSerializesAndDeserializesConfiguredMessage(): Unit = {
    withActorSystem("AkkaJacksonCborBindingTest") { system =>
      val serialization: Serialization = SerializationExtension(system)
      val message: CborMessage = new CborMessage("cbor-message", true)
      val serializer: Serializer = serialization.findSerializerFor(message)

      assertEquals(JacksonCborSerializerClassName, serializer.getClass.getName)

      val bytes: Array[Byte] = serialization.serialize(message).get
      val utf8Payload: String = new String(bytes, StandardCharsets.UTF_8)
      assertFalse(utf8Payload.startsWith("{"))

      val manifest: String = Serializers.manifestFor(serializer, message)
      val decoded: CborMessage =
        serialization.deserialize(bytes, serializer.identifier, manifest).get.asInstanceOf[CborMessage]
      assertEquals(message.name, decoded.name)
      assertEquals(message.enabled, decoded.enabled)
    }
  }

  @Test
  def markerInterfaceBindingRoutesConcreteMessagesThroughJacksonJson(): Unit = {
    withActorSystem("AkkaJacksonMarkerBindingTest") { system =>
      val serialization: Serialization = SerializationExtension(system)
      val command: Command = new StartCommand("indexing", 3)
      val serializer: Serializer = serialization.findSerializerFor(command)

      assertEquals(JacksonJsonSerializerClassName, serializer.getClass.getName)

      val bytes: Array[Byte] = serialization.serialize(command).get
      val manifest: String = Serializers.manifestFor(serializer, command)
      val decoded: StartCommand =
        serialization.deserialize(bytes, serializer.identifier, manifest).get.asInstanceOf[StartCommand]
      assertEquals("indexing", decoded.name)
      assertEquals(3, decoded.priority)
    }
  }

  @Test
  def objectMapperProviderCreatesCachedMappersForConfiguredBindings(): Unit = {
    withActorSystem("AkkaJacksonObjectMapperProviderTest") { system =>
      val provider: JacksonObjectMapperProvider = JacksonObjectMapperProvider(system)
      val firstMapper: ObjectMapper = provider.getOrCreate("jackson-json", None)
      val secondMapper: ObjectMapper = provider.getOrCreate("jackson-json", None)
      val message: JsonMessage = new JsonMessage("mapper-message", 7)

      assertSame(firstMapper, secondMapper)

      val json: String = firstMapper.writeValueAsString(message)
      assertTrue(json.contains("mapper-message"))

      val decoded: JsonMessage = firstMapper.readValue(json, classOf[JsonMessage])
      assertEquals(message.id, decoded.id)
      assertEquals(message.count, decoded.count)
    }
  }

  private def withActorSystem(name: String)(testBody: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name, serializationConfig)
    try {
      testBody(system)
    } finally {
      Await.result(system.terminate(), TestTimeout)
    }
  }
}

object Akka_serialization_jackson_2_13Test {
  private val TestTimeout = 10.seconds

  private val JacksonJsonSerializerClassName: String =
    "akka.serialization.jackson.JacksonJsonSerializer"
  private val JacksonCborSerializerClassName: String =
    "akka.serialization.jackson.JacksonCborSerializer"

  private val serializationConfig: Config = ConfigFactory.parseString(
    s"""
       |akka {
       |  loglevel = "WARNING"
       |  stdout-loglevel = "OFF"
       |  actor {
       |    default-dispatcher.shutdown-timeout = 10s
       |    serializers {
       |      jackson-json = "$JacksonJsonSerializerClassName"
       |      jackson-cbor = "$JacksonCborSerializerClassName"
       |    }
       |    serialization-bindings {
       |      "${classOf[JsonMessage].getName}" = jackson-json
       |      "${classOf[CborMessage].getName}" = jackson-cbor
       |      "${classOf[Command].getName}" = jackson-json
       |    }
       |  }
       |}
       |""".stripMargin
  )
}

final class JsonMessage() {
  @BeanProperty var id: String = _
  @BeanProperty var count: Int = 0

  def this(id: String, count: Int) = {
    this()
    this.id = id
    this.count = count
  }
}

final class CborMessage() {
  @BeanProperty var name: String = _
  @BeanProperty var enabled: Boolean = false

  def this(name: String, enabled: Boolean) = {
    this()
    this.name = name
    this.enabled = enabled
  }
}

trait Command

final class StartCommand() extends Command {
  @BeanProperty var name: String = _
  @BeanProperty var priority: Int = 0

  @JsonCreator
  def this(
    @JsonProperty("name") name: String,
    @JsonProperty("priority") priority: Int
  ) = {
    this()
    this.name = name
    this.priority = priority
  }
}
