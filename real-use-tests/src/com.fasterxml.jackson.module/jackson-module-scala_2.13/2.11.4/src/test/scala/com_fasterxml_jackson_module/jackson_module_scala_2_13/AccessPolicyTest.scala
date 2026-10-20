/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala unsorted-set data binding for access-control group membership policies. */
@Timeout(60)
class AccessPolicyTest {
  @Test
  def deduplicatesMembershipsAndPublishesGrantsAndRevocations(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // A policy import can repeat group names; membership is a set, not a priority list.
    val imported: Map[String, Set[String]] = mapper.readValue[Map[String, Set[String]]](
      """{
        |  "alice": ["readers", "editors", "readers"],
        |  "bob": ["readers"],
        |  "guest": []
        |}""".stripMargin)
    assertEquals(Set("readers", "editors"), imported("alice"))
    assertEquals(2, imported("alice").size)
    assertEquals(Set.empty[String], imported("guest"))

    // Revoke Alice's edit access and grant it to Bob without changing the imported snapshot.
    val revised: Map[String, Set[String]] = imported
      .updated("alice", imported("alice") - "editors")
      .updated("bob", imported("bob") + "editors" + "editors")
    assertFalse(revised("alice").contains("editors"))
    assertTrue(revised("bob").contains("editors"))
    assertEquals(Set("readers", "editors"), imported("alice"))
    assertEquals(Set("readers"), imported("bob"))

    val published: String = mapper.writeValueAsString(revised)
    val document: JsonNode = mapper.readTree(published)
    assertEquals(3, document.size())
    revised.foreach { case (user, groups) =>
      val encodedGroups: JsonNode = document.path(user)
      assertTrue(encodedGroups.isArray)
      assertEquals(groups.size, encodedGroups.size())
      // Array order is not part of a set's wire contract.
      val names: Set[String] = (0 until encodedGroups.size()).map { index: Int =>
        encodedGroups.get(index).asText()
      }.toSet
      assertEquals(groups, names)
    }

    // A restarted authorization service must enforce the newly published membership policy.
    val restored: Map[String, Set[String]] =
      mapper.readValue[Map[String, Set[String]]](published)
    assertEquals(revised, restored)
    val editors: Set[String] = restored.collect {
      case (user, groups) if groups.contains("editors") => user
    }.toSet
    assertEquals(Set("bob"), editors)
    assertFalse(restored("guest").contains("readers"))
    assertTrue(restored("alice").contains("readers"))
  }
}
