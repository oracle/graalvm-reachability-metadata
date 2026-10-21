/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.default.*

/**
 * Schema-less JSON document editing through the mutable ujson.Value tree (parse, navigate,
 * mutate, copy, reformat and bind into typed values), as an admin tool patches a feature flag
 * document whose unknown fields must survive the edit.
 */
class FeatureFlagDocumentEditingTest {

  import FeatureFlagDocumentEditingTest.*

  @Test
  def patchingAFlagPreservesFieldsTheToolDoesNotKnow(): Unit = {
    val document: ujson.Value = ujson.read(FlagDocument)

    val checkout: ujson.Value = document("flags").arr.find(_("name").str == "new-checkout").get
    checkout("enabled") = true
    checkout("rollout")("percent") = 50
    checkout("rollout")("segments").arr.append(ujson.Str("beta-testers"))
    checkout.obj.remove("deprecatedNote")
    document("revision") = document("revision").num + 1

    val patched: ujson.Value = ujson.read(ujson.write(document))
    val patchedCheckout: ujson.Value = patched("flags")(0)

    assertEquals(8.0, patched("revision").num)
    assertEquals(ujson.True, patchedCheckout("enabled"))
    assertEquals(50.0, patchedCheckout("rollout")("percent").num)
    assertEquals(Seq("staff", "beta-testers"), patchedCheckout("rollout")("segments").arr.map(_.str).toSeq)
    assertFalse(patchedCheckout.obj.contains("deprecatedNote"))
    assertEquals(ujson.Obj("owner" -> "payments", "ticket" -> "PAY-311"), patchedCheckout("x-annotations"))
    assertEquals(Seq("schemaVersion", "revision", "flags"), patched.obj.keys.toSeq)
  }

  @Test
  def optionalAccessorsDistinguishMissingNullAndWronglyTypedFields(): Unit = {
    val document: ujson.Value = ujson.read(FlagDocument)
    val flags: Seq[ujson.Value] = document("flags").arr.toSeq
    val darkMode: ujson.Value = flags(1)

    assertEquals(None, darkMode.obj.get("rollout"))
    assertTrue(darkMode("expiresAt").isNull)
    assertEquals(None, darkMode("expiresAt").strOpt)
    assertEquals(Some("2026-12-31"), flags(0)("expiresAt").strOpt)
    assertEquals(None, darkMode("enabled").numOpt)
    assertEquals(Some(false), darkMode("enabled").boolOpt)

    val wrongType: ujson.Value.InvalidData =
      assertThrows(classOf[ujson.Value.InvalidData], () => darkMode("enabled").str)
    assertTrue(wrongType.getMessage.contains("Expected ujson.Str"), wrongType.getMessage)
    assertThrows(classOf[NoSuchElementException], () => darkMode("rollout"))
  }

  @Test
  def documentsBuiltFromLiteralsBindToTypedFlags(): Unit = {
    val built: ujson.Value = ujson.Obj(
      "name" -> "search-v2",
      "enabled" -> true,
      "rollout" -> ujson.Obj("percent" -> 10, "segments" -> ujson.Arr("staff")),
      "expiresAt" -> ujson.Null
    )

    val flag: Flag = read[Flag](built)

    assertEquals(Flag("search-v2", enabled = true, Some(Rollout(10, List("staff"))), None), flag)
    assertEquals(built, writeJs(flag))

    val document: ujson.Value = ujson.read(FlagDocument)
    val typedFlags: Seq[Flag] = document("flags").arr.toSeq.map(flagJson => read[Flag](flagJson))
    assertEquals(Seq("new-checkout", "dark-mode"), typedFlags.map(_.name))
    assertEquals(Some(Rollout(25, List("staff"))), typedFlags.head.rollout)
  }

  @Test
  def copiesAreIndependentAndReformattingNormalizesLayout(): Unit = {
    val original: ujson.Value = ujson.read(FlagDocument)
    val draft: ujson.Value = ujson.copy(original)

    draft("flags").arr.remove(1)
    draft("schemaVersion") = "2"

    assertEquals(2, original("flags").arr.size)
    assertEquals(1, draft("flags").arr.size)
    assertEquals(ujson.Num(1), original("schemaVersion"))
    assertTrue(original != draft)

    val compact: String = ujson.reformat("""{ "b" : [ 1 , 2 ],   "a" : { } }""")
    assertEquals("""{"b":[1,2],"a":{}}""", compact)
    val sorted: String = ujson.reformat("""{"b": [1, 2], "a": {"z": null, "y": "x"}}""", indent = 2, sortKeys = true)
    assertEquals("{\n  \"a\": {\n    \"y\": \"x\",\n    \"z\": null\n  },\n  \"b\": [\n    1,\n    2\n  ]\n}", sorted)
  }
}

object FeatureFlagDocumentEditingTest {

  final case class Rollout(percent: Int, segments: List[String]) derives ReadWriter

  final case class Flag(name: String, enabled: Boolean, rollout: Option[Rollout] = None, expiresAt: Option[String])
      derives ReadWriter

  val FlagDocument: String =
    """{
      |  "schemaVersion": 1,
      |  "revision": 7,
      |  "flags": [
      |    {
      |      "name": "new-checkout",
      |      "enabled": false,
      |      "rollout": {"percent": 25, "segments": ["staff"]},
      |      "expiresAt": "2026-12-31",
      |      "deprecatedNote": "remove after Q4",
      |      "x-annotations": {"owner": "payments", "ticket": "PAY-311"}
      |    },
      |    {"name": "dark-mode", "enabled": false, "expiresAt": null}
      |  ]
      |}""".stripMargin
}
