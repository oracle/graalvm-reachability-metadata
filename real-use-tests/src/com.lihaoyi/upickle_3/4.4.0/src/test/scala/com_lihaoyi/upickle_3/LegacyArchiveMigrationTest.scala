/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.core.AbortException
import upickle.core.TraceVisitor.TraceException
import upickle.implicits.key

/**
 * The legacy wire format of the upickle.legacy API (sealed hierarchies encoded as tag-and-value
 * arrays, tagged case objects, key-annotated and fully qualified tags), as an account service
 * migrates entitlement archives written by an older release into the upickle.default format.
 */
class LegacyArchiveMigrationTest {

  import LegacyArchiveMigrationTest.*

  @Test
  def legacyArchiveIsMigratedToTheCurrentFormat(): Unit = {
    val archive: String =
      """[
        |  {
        |    "id": "acc-100",
        |    "plan": "enterprise",
        |    "entitlements": [
        |      ["Seat", {"user": "ana", "role": "admin"}],
        |      ["com_lihaoyi.upickle_3.LegacyArchiveMigrationTest.Quota",
        |       {"resource": "storage", "limit": 5000000000}],
        |      ["grant-v2", {"team": "ops", "level": 3}]
        |    ],
        |    "createdAtEpochSecond": 1700000000
        |  },
        |  {
        |    "id": "acc-101",
        |    "plan": null,
        |    "entitlements": [["Suspended", {}]],
        |    "createdAtEpochSecond": 1710000000
        |  }
        |]""".stripMargin

    val accounts: Seq[Account] = upickle.legacy.read[Seq[Account]](archive)

    assertEquals(
      Seq(
        Account(
          "acc-100",
          Some("enterprise"),
          Seq(Seat("ana", "admin"), Quota("storage", 5000000000L), Grant("ops", 3)),
          1700000000L
        ),
        Account("acc-101", None, Seq(Suspended), 1710000000L)
      ),
      accounts
    )

    val migrated: String = upickle.default.write(accounts)
    val tree: ujson.Value = ujson.read(migrated)
    val entitlements: ujson.Arr = tree(0)("entitlements").arr

    assertEquals("Seat", entitlements(0)("$type").str)
    assertEquals("admin", entitlements(0)("role").str)
    assertEquals(5000000000L, entitlements(1)("limit").num.toLong)
    assertEquals("grant-v2", entitlements(2)("$type").str)
    assertEquals(ujson.Str("Suspended"), tree(1)("entitlements")(0))
    assertEquals(accounts, upickle.default.read[Seq[Account]](migrated))
  }

  @Test
  def currentAccountsAreExportedForTheOlderRelease(): Unit = {
    val account: Account = Account(
      "acc-200",
      Some("team"),
      Seq(Seat("bo", "viewer"), Grant("support"), Suspended),
      1720000000L
    )

    val exported: String = upickle.legacy.write(account)
    val tree: ujson.Value = ujson.read(exported)
    val entitlements: ujson.Arr = tree("entitlements").arr

    assertEquals(ujson.Arr("Seat", ujson.Obj("user" -> "bo", "role" -> "viewer")), entitlements(0))
    assertEquals("grant-v2", entitlements(1)(0).str)
    assertEquals("support", entitlements(1)(1)("team").str)
    assertEquals(ujson.Arr("Suspended", ujson.Obj()), entitlements(2))
    assertEquals(
      ujson.Arr("Quota", ujson.Obj("resource" -> "seats", "limit" -> 25)),
      upickle.legacy.writeJs[Entitlement](Quota("seats", 25))
    )
    assertEquals(account, upickle.legacy.read[Account](exported))

    val spooled: Array[Byte] = upickle.legacy.writeBinary(account)
    assertEquals(account, upickle.legacy.readBinary[Account](spooled))
  }

  @Test
  def recordsInTheWrongEncodingAreRejectedWithTheirPath(): Unit = {
    val currentFormat: String = """{"$type": "Seat", "user": "cy", "role": "admin"}"""
    val wrongEncoding: TraceException =
      assertThrows(classOf[TraceException], () => upickle.legacy.read[Entitlement](currentFormat))
    assertInstanceOf(classOf[AbortException], wrongEncoding.getCause)
    assertTrue(wrongEncoding.getCause.getMessage.contains("expected sequence"), wrongEncoding.getCause.getMessage)

    val unknownTag: String = """[["Seat", {"user": "cy", "role": "admin"}], ["Refund", {"amount": 10}]]"""
    val retired: TraceException =
      assertThrows(classOf[TraceException], () => upickle.legacy.read[Seq[Entitlement]](unknownTag))
    assertEquals("$[1][0]", retired.getMessage)
    assertTrue(retired.getCause.getMessage.contains("invalid tag for tagged object: Refund"), retired.getCause.getMessage)

    val corrupted: String =
      """{"id": "acc-300", "plan": null, "entitlements": [["Quota", {"resource": "cpu", "limit": "lots"}]],
        | "createdAtEpochSecond": 1730000000}""".stripMargin
    val badLimit: TraceException =
      assertThrows(classOf[TraceException], () => upickle.legacy.read[Account](corrupted, trace = true))
    assertEquals("$['entitlements'][0][1]['limit']", badLimit.getMessage)
  }
}

object LegacyArchiveMigrationTest {

  sealed trait Entitlement

  final case class Seat(user: String, role: String) extends Entitlement

  final case class Quota(resource: String, limit: Long) extends Entitlement

  @key("grant-v2")
  final case class Grant(team: String, level: Int = 1) extends Entitlement

  case object Suspended extends Entitlement

  object Seat {
    given legacyRW: upickle.legacy.ReadWriter[Seat] = upickle.legacy.macroRW
    given defaultRW: upickle.default.ReadWriter[Seat] = upickle.default.macroRW
  }

  object Quota {
    given legacyRW: upickle.legacy.ReadWriter[Quota] = upickle.legacy.macroRW
    given defaultRW: upickle.default.ReadWriter[Quota] = upickle.default.macroRW
  }

  object Grant {
    given legacyRW: upickle.legacy.ReadWriter[Grant] = upickle.legacy.macroRW
    given defaultRW: upickle.default.ReadWriter[Grant] = upickle.default.macroRW
  }

  object Entitlement {
    given legacySuspendedRW: upickle.legacy.ReadWriter[Suspended.type] = upickle.legacy.macroRW
    given defaultSuspendedRW: upickle.default.ReadWriter[Suspended.type] = upickle.default.macroRW
    given legacyRW: upickle.legacy.ReadWriter[Entitlement] = upickle.legacy.macroRW
    given defaultRW: upickle.default.ReadWriter[Entitlement] = upickle.default.macroRW
  }

  final case class Account(
      id: String,
      plan: Option[String],
      entitlements: Seq[Entitlement],
      createdAtEpochSecond: Long
  )

  object Account {
    given legacyRW: upickle.legacy.ReadWriter[Account] = upickle.legacy.macroRW
    given defaultRW: upickle.default.ReadWriter[Account] = upickle.default.macroRW
  }
}
