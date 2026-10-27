/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import upickle.default.*
import upickle.implicits.allowUnknownKeys
import upickle.implicits.serializeDefaults

/**
 * Schema evolution of persisted documents through per-class codec annotations
 * (allowUnknownKeys and serializeDefaults on classes and fields) together with defaults filling
 * missing keys and the encoding of optional fields, as a profile service reads documents an
 * older release stored in a relational table and writes documents for current consumers.
 */
class ProfileStoreSchemaEvolutionTest {

  import ProfileStoreSchemaEvolutionTest.*

  private var connection: Connection = null

  @BeforeEach
  def openStore(): Unit = {
    connection = DriverManager.getConnection("jdbc:h2:mem:profiles;DB_CLOSE_DELAY=-1", "sa", "")
    val statement = connection.createStatement()
    try statement.execute("CREATE TABLE documents (id VARCHAR(64) PRIMARY KEY, kind VARCHAR(32), body CLOB)")
    finally statement.close()
  }

  @AfterEach
  def closeStore(): Unit = {
    val statement = connection.createStatement()
    try statement.execute("DROP ALL OBJECTS")
    finally statement.close()
    connection.close()
  }

  @Test
  def documentsOfThePreviousReleaseAreUpgradedThroughDefaults(): Unit = {
    // Written by the previous release: no locale, consent or tags yet, and a since-removed fax field.
    store("p-1", "profile", """{"id":"p-1","displayName":"Ada Lovelace","fax":"+44 20 7946 0000"}""")
    store("p-2", "profile", """{"id":"p-2","displayName":"Alan Turing","locale":"en-GB","tags":["vip"]}""")

    val upgraded: List[Profile] = loadAll("profile").map(read[Profile](_))

    assertEquals(
      List(
        Profile("p-1", "Ada Lovelace"),
        Profile("p-2", "Alan Turing", locale = "en-GB", tags = List("vip"))
      ),
      upgraded
    )
    assertEquals("en-US", upgraded.head.locale)
    assertFalse(upgraded.head.marketingOptIn)
    assertEquals(None, upgraded.head.nickname)
  }

  @Test
  def rewrittenDocumentsOmitDefaultsAndDropRemovedFields(): Unit = {
    store("p-3", "profile", """{"id":"p-3","displayName":"Grace Hopper","fax":"n/a","marketingOptIn":false}""")

    val profile: Profile = read[Profile](load("p-3"))
    update("p-3", write(profile.copy(nickname = Some("Amazing Grace"))))

    val stored: ujson.Value = ujson.read(load("p-3"))
    assertEquals(Set("id", "displayName", "nickname"), stored.obj.keySet.toSet)
    assertEquals("Amazing Grace", stored("nickname").str)
    assertEquals(profile.copy(nickname = Some("Amazing Grace")), read[Profile](stored))
  }

  @Test
  def strictPayoutInstructionsRejectUnknownKeys(): Unit = {
    store("pay-1", "payout", """{"iban":"DE89370400440532013000","amountCents":125000}""")
    store("pay-2", "payout", """{"iban":"DE89370400440532013000","amountcents":125000,"amountCents":1}""")

    assertEquals(PayoutInstruction("DE89370400440532013000", 125000L), read[PayoutInstruction](load("pay-1")))

    val error: Exception = assertThrows(classOf[Exception], () => read[PayoutInstruction](load("pay-2")))
    assertTrue(messages(error).exists(_.contains("amountcents")), messages(error).mkString(" | "))
  }

  @Test
  def lenientProfilesAndStrictPayoutsDifferOnlyByAnnotation(): Unit = {
    val withTypo: String = """{"id":"p-9","displayName":"Edsger","displayname":"typo"}"""

    assertEquals(Profile("p-9", "Edsger"), read[Profile](withTypo))
    assertThrows(
      classOf[Exception],
      () => read[PayoutInstruction]("""{"iban":"NL91ABNA0417164300","amountCents":5,"currency":"EUR"}""")
    )
  }

  @Test
  def retentionPolicyAlwaysCarriesItsDefaultsForDownstreamAuditors(): Unit = {
    val policy: RetentionPolicy = RetentionPolicy()
    store("ret-default", "retention", write(policy))
    store("ret-custom", "retention", write(RetentionPolicy(days = 365)))

    assertEquals(ujson.Obj("days" -> 30, "archive" -> true), ujson.read(load("ret-default")))
    assertEquals(ujson.Obj("days" -> 365, "archive" -> true), ujson.read(load("ret-custom")))
    assertEquals(policy, read[RetentionPolicy]("{}"))
  }

  @Test
  def consentRecordSerializesOnlyTheAnnotatedDefaultField(): Unit = {
    val consent: Consent = Consent(subject = "p-1")
    store("consent-1", "consent", write(consent))

    val stored: ujson.Value = ujson.read(load("consent-1"))
    assertEquals(ujson.Obj("subject" -> "p-1", "policyVersion" -> 1), stored)
    assertEquals(consent, read[Consent](stored))
    assertEquals(Consent("p-2", policyVersion = 2, channel = "sms"), read[Consent](
      """{"subject":"p-2","policyVersion":2,"channel":"sms"}"""
    ))
  }

  @Test
  def optionalFieldsWithoutDefaultsAreWrittenAsNulls(): Unit = {
    val home: Address = Address(street = "1 Main St", unit = None)
    val office: Address = Address(street = "2 Side St", unit = Some("4B"))
    store("addr-home", "address", write(home))
    store("addr-office", "address", write(office))

    assertEquals("""{"street":"1 Main St","unit":null}""", load("addr-home"))
    assertEquals("""{"street":"2 Side St","unit":"4B"}""", load("addr-office"))
    assertEquals(List(home, office), loadAll("address").map(read[Address](_)))
  }

  private def store(id: String, kind: String, body: String): Unit = {
    val statement: PreparedStatement = connection.prepareStatement("INSERT INTO documents VALUES (?, ?, ?)")
    try {
      statement.setString(1, id)
      statement.setString(2, kind)
      statement.setString(3, body)
      assertEquals(1, statement.executeUpdate())
    } finally statement.close()
  }

  private def update(id: String, body: String): Unit = {
    val statement: PreparedStatement = connection.prepareStatement("UPDATE documents SET body = ? WHERE id = ?")
    try {
      statement.setString(1, body)
      statement.setString(2, id)
      assertEquals(1, statement.executeUpdate())
    } finally statement.close()
  }

  private def load(id: String): String = {
    val statement: PreparedStatement = connection.prepareStatement("SELECT body FROM documents WHERE id = ?")
    try {
      statement.setString(1, id)
      val rows: ResultSet = statement.executeQuery()
      assertTrue(rows.next(), s"document $id is stored")
      rows.getString(1)
    } finally statement.close()
  }

  private def loadAll(kind: String): List[String] = {
    val statement: PreparedStatement =
      connection.prepareStatement("SELECT body FROM documents WHERE kind = ? ORDER BY id")
    try {
      statement.setString(1, kind)
      val rows: ResultSet = statement.executeQuery()
      Iterator.continually(rows).takeWhile(_.next()).map(_.getString(1)).toList
    } finally statement.close()
  }

  private def messages(error: Throwable): List[String] =
    Iterator.iterate(error)(_.getCause).takeWhile(_ != null).map(e => String.valueOf(e.getMessage)).toList
}

object ProfileStoreSchemaEvolutionTest {

  /** Lenient by default: keys written by older or newer releases are ignored. */
  final case class Profile(
      id: String,
      displayName: String,
      locale: String = "en-US",
      marketingOptIn: Boolean = false,
      tags: List[String] = Nil,
      nickname: Option[String] = None
  ) derives ReadWriter

  /** Money movement must not silently drop a misspelt key. */
  @allowUnknownKeys(false)
  final case class PayoutInstruction(iban: String, amountCents: Long) derives ReadWriter

  /** Auditors read retention policies without knowing this service's defaults. */
  @serializeDefaults(true)
  final case class RetentionPolicy(days: Int = 30, archive: Boolean = true) derives ReadWriter

  /** Only the policy version is pinned in every record; the channel keeps the compact default. */
  final case class Consent(
      subject: String,
      @serializeDefaults(true) policyVersion: Int = 1,
      channel: String = "email"
  ) derives ReadWriter

  final case class Address(street: String, unit: Option[String]) derives ReadWriter
}
