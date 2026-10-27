/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala Option data binding for notification route overrides. */
@Timeout(60)
class NotificationOverrideTest {
  @Test
  def resolvesOptionalRoutesAndPublishesUpdatedPreferences(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // Null inherits the default route; an explicit empty string disables that channel.
    val incoming: String = """{"email":"ops@example.test","sms":null,"webhook":""}"""
    val overrides: Map[String, Option[String]] =
      mapper.readValue[Map[String, Option[String]]](incoming)

    assertEquals(Some("ops@example.test"), overrides("email"))
    assertEquals(None, overrides("sms"))
    assertEquals(Some(""), overrides("webhook"))
    assertEquals("ops@example.test", overrides("email").getOrElse("default@example.test"))
    assertEquals("default-sms-route", overrides("sms").getOrElse("default-sms-route"))
    assertEquals("", overrides("webhook").getOrElse("https://example.test/default"))

    // An administrator enables SMS and resets email to its inherited route.
    val updated: Map[String, Option[String]] = overrides
      .updated("email", None)
      .updated("sms", Some("on-call-sms-route"))
    val published: String = mapper.writeValueAsString(updated)
    val document: JsonNode = mapper.readTree(published)
    assertEquals(mapper.readTree(
      """{"email":null,"sms":"on-call-sms-route","webhook":""}"""), document)

    val restored: Map[String, Option[String]] =
      mapper.readValue[Map[String, Option[String]]](published)
    assertEquals(updated, restored)
    assertEquals("default@example.test", restored("email").getOrElse("default@example.test"))
    assertEquals("on-call-sms-route", restored("sms").getOrElse("default-sms-route"))
    assertEquals(Some(""), restored("webhook"))
  }
}
