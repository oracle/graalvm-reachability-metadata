/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_serialization_jackson_3

import java.time.Instant

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.actor.Address
import akka.actor.AddressFromURIString
import akka.serialization.jackson.AkkaJacksonModule
import akka.serialization.jackson.JacksonMigration

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Akka_serialization_jackson_3Test {
  @Test
  def jsonMapperRoundTripsAkkaAddressAndFiniteDuration(): Unit = {
    val mapper = jsonMapper()
    val address = AddressFromURIString("akka://mapper-system@127.0.0.1:25520")
    val duration = 250.millis

    val addressJson = mapper.writeValueAsString(address)
    val durationJson = mapper.writeValueAsString(duration)

    val restoredAddress = mapper.readValue(addressJson, classOf[Address])
    val restoredDuration = mapper.readValue(durationJson, classOf[FiniteDuration])

    assertThat(restoredAddress).isEqualTo(address)
    assertThat(restoredDuration).isEqualTo(duration)
    assertThat(mapper.readTree("{'feature':'single-quotes'}").get("feature").asText()).isEqualTo("single-quotes")
  }

  @Test
  def cborMapperRoundTripsAkkaAddressAndInstant(): Unit = {
    val mapper = cborMapper()
    val address = AddressFromURIString("akka://cbor-system@127.0.0.1:25521")
    val instant = Instant.parse("2024-01-02T03:04:05Z")

    val addressBytes = mapper.writeValueAsBytes(address)
    val instantBytes = mapper.writeValueAsBytes(instant)

    val restoredAddress = mapper.readValue(addressBytes, classOf[Address])
    val restoredInstant = mapper.readValue(instantBytes, classOf[Instant])

    assertThat(restoredAddress).isEqualTo(address)
    assertThat(restoredInstant).isEqualTo(instant)
  }

  @Test
  def akkaJacksonModuleExposesVersionMetadata(): Unit = {
    val version = new AkkaJacksonModule().version()

    assertThat(version.getMajorVersion).isGreaterThanOrEqualTo(0)
    assertThat(version.getMinorVersion).isGreaterThanOrEqualTo(0)
    assertThat(version.getArtifactId).contains("jackson")
  }

  @Test
  def jacksonMigrationTransformsOlderJsonSchemaBeforeDeserialization(): Unit = {
    val mapper = jsonMapper()
    val migration = new RenameOldNameMigration
    val original = mapper.readTree("""{"oldName":"Ada Lovelace"}""")
    val transformed = migration.transform(1, original)

    assertThat(transformed.get("newName").asText()).isEqualTo("Ada Lovelace")
    assertThat(transformed.has("oldName")).isFalse
  }

  private def jsonMapper(): ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(new AkkaJacksonModule())
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new ParameterNamesModule())
    mapper.registerModule(new Jdk8Module())
    mapper.registerModule(new JavaTimeModule())
    mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
    mapper
  }

  private def cborMapper(): ObjectMapper = {
    val mapper = new ObjectMapper(new CBORFactory())
    mapper.registerModule(new AkkaJacksonModule())
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new ParameterNamesModule())
    mapper.registerModule(new Jdk8Module())
    mapper.registerModule(new JavaTimeModule())
    mapper
  }
}

class RenameOldNameMigration extends JacksonMigration {
  override def currentVersion: Int = 2

  override def transform(fromVersion: Int, json: JsonNode): JsonNode = {
    val objectNode: ObjectNode = json.deepCopy[JsonNode]().asInstanceOf[ObjectNode]
    if (fromVersion == 1) {
      val oldName = objectNode.remove("oldName")
      objectNode.set[JsonNode]("newName", oldName)
    }
    objectNode
  }
}
