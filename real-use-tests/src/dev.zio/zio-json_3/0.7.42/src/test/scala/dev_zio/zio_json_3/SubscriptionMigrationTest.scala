/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*

/** Exercises decoder alternative composition for migrating notification subscription schemas. */
class SubscriptionMigrationTest {
  final case class Contact(address: String, topics: List[String])
  final case class Subscription(id: String, contact: Contact)
  final case class LegacySubscription(id: String, email: String)

  given JsonCodec[Contact] = DeriveJsonCodec.gen[Contact]
  private val currentDecoder: JsonDecoder[Subscription] = DeriveJsonDecoder.gen[Subscription]
  private val legacyDecoder: JsonDecoder[Subscription] = DeriveJsonDecoder.gen[LegacySubscription]
    .map(legacy => Subscription(legacy.id, Contact(legacy.email, List("announcements"))))

  given JsonDecoder[Subscription] = currentDecoder.orElse(legacyDecoder)
  given JsonEncoder[Subscription] = DeriveJsonEncoder.gen[Subscription]

  @Test
  @Timeout(30)
  def loadMixedSchemaSubscriptionsAndSaveOnlyTheCurrentFormat(): Unit = {
    val stored: String = """
      [
        {"id":"customer-1","email":"first@example.test"},
        {
          "id":"customer-2",
          "contact":{"address":"second@example.test","topics":["security","announcements"]}
        },
        {"email":"third@example.test","id":"customer-3"}
      ]
      """
    val expected: List[Subscription] = List(
      Subscription("customer-1", Contact("first@example.test", List("announcements"))),
      Subscription("customer-2", Contact("second@example.test", List("security", "announcements"))),
      Subscription("customer-3", Contact("third@example.test", List("announcements")))
    )
    val loaded: List[Subscription] = stored.fromJson[List[Subscription]].fold(
      error => fail[List[Subscription]](error),
      identity
    )
    assertEquals(expected, loaded, "Both schema versions must load into the same application model")

    val securityRecipients: List[String] = loaded
      .filter(_.contact.topics.contains("security"))
      .map(_.contact.address)
    assertEquals(List("second@example.test"), securityRecipients)
    val announcementRecipients: List[String] = loaded
      .filter(_.contact.topics.contains("announcements"))
      .map(_.contact.address)
    assertEquals(
      List("first@example.test", "second@example.test", "third@example.test"),
      announcementRecipients,
      "Legacy subscribers retain their original announcement subscription"
    )

    val migrated: String = loaded.toJson
    assertFalse(migrated.contains("\"email\":"), migrated)
    assertEquals(
      Right(expected),
      JsonDecoder.list[Subscription](using currentDecoder).decodeJson(migrated),
      "Saved records must load without the legacy fallback"
    )
    assertEquals(Right(expected), migrated.fromJson[List[Subscription]])

    val transitional: String = """
      {
        "id":"customer-4",
        "email":"obsolete@example.test",
        "contact":{"address":"current@example.test","topics":["security"]}
      }
      """
    assertEquals(
      Right(Subscription("customer-4", Contact("current@example.test", List("security")))),
      transitional.fromJson[Subscription],
      "The current schema takes precedence when a transitional record has both representations"
    )

    val invalidRecords: List[String] = List(
      """{"id":"missing-contact"}""",
      """{"id":"bad-legacy","email":false}""",
      """{"id":"bad-current","contact":{"address":42,"topics":["security"]}}"""
    )
    invalidRecords.foreach { record =>
      assertTrue(record.fromJson[Subscription].isLeft, s"Invalid subscription must be rejected: $record")
    }
  }
}
