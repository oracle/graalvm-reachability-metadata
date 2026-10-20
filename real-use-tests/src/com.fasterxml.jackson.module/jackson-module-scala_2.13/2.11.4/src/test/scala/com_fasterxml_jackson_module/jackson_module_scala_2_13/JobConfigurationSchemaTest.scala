/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.jsonSchema.{JsonSchema, JsonSchemaGenerator}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, RequiredPropertiesSchemaModule}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala required-property JSON Schema generation for job configuration forms. */
@Timeout(60)
class JobConfigurationSchemaTest {
  case class JobConfiguration(name: String, maximumAttempts: Int,
                              notificationEmail: Option[String])

  @Test
  def publishesRequiredAndOptionalFieldsForAConfigurationForm(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(new DefaultScalaModule with RequiredPropertiesSchemaModule)

    // The administration UI builds its form from the same Scala model used by the service.
    val generator: JsonSchemaGenerator = new JsonSchemaGenerator(mapper)
    val schema: JsonSchema = generator.generateSchema(classOf[JobConfiguration])
    val published: String = mapper.writeValueAsString(schema)
    val document: JsonNode = mapper.readTree(published)
    assertEquals("object", document.path("type").asText())

    val fields: JsonNode = document.path("properties")
    assertEquals(3, fields.size())
    assertEquals("string", fields.path("name").path("type").asText())
    assertEquals("integer", fields.path("maximumAttempts").path("type").asText())
    assertEquals("string", fields.path("notificationEmail").path("type").asText())
    assertTrue(fields.path("name").path("required").asBoolean())
    assertTrue(fields.path("maximumAttempts").path("required").asBoolean())
    assertFalse(fields.path("notificationEmail").path("required").asBoolean())

    // Consumers can label mandatory inputs without treating Option[String] as mandatory.
    val formOrder: List[String] = List("name", "maximumAttempts", "notificationEmail")
    val mandatoryInputs: List[String] = formOrder.filter { field: String =>
      fields.path(field).path("required").asBoolean()
    }
    assertEquals(List("name", "maximumAttempts"), mandatoryInputs)

    val example: JobConfiguration = JobConfiguration("nightly-stock-sync", 3, None)
    val exampleDocument: JsonNode = mapper.readTree(mapper.writeValueAsString(example))
    assertEquals("nightly-stock-sync", exampleDocument.path("name").asText())
    assertEquals(3, exampleDocument.path("maximumAttempts").asInt())
    assertTrue(exampleDocument.path("notificationEmail").isNull)
    assertTrue(mandatoryInputs.forall(field => exampleDocument.hasNonNull(field)))
  }
}
