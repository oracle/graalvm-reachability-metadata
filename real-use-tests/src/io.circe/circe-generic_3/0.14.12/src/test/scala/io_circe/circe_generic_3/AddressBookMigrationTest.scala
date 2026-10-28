/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises derived decoder alternatives for backward-compatible address-book schema migration. */
@Timeout(60)
class AddressBookMigrationTest {
  final case class LegacyContact(id: String, fullName: String, email: String)

  final case class Contact(id: String, displayName: String, emails: List[String])

  object Contact {
    given Decoder[Contact] = deriveDecoder[Contact].or(
      deriveDecoder[LegacyContact].map { legacy =>
        Contact(legacy.id, legacy.fullName, List(legacy.email))
      }
    )
    given Encoder.AsObject[Contact] = deriveEncoder[Contact]
  }

  @Test
  def importsMixedBackupsAndExportsOnlyTheCurrentSchema(): Unit = {
    val backup: String = """
      [
        {"id": "contact-17", "fullName": "Ada Stone", "email": "ada@example.test"},
        {
          "id": "contact-28",
          "displayName": "Sam Reed",
          "emails": ["sam@example.test", "sam.work@example.test"]
        }
      ]
      """
    val contacts: List[Contact] = decode[List[Contact]](backup).fold(
      error => throw new AssertionError(s"Address-book backup was rejected: $error"),
      identity
    )
    assertThat(contacts).isEqualTo(List(
      Contact("contact-17", "Ada Stone", List("ada@example.test")),
      Contact("contact-28", "Sam Reed", List("sam@example.test", "sam.work@example.test"))
    ))
    assertThat(contacts.flatMap(_.emails)).isEqualTo(List(
      "ada@example.test", "sam@example.test", "sam.work@example.test"
    ))

    val exported: Json = contacts.asJson
    val expected: Json = parse("""
      [
        {"id": "contact-17", "displayName": "Ada Stone", "emails": ["ada@example.test"]},
        {
          "id": "contact-28",
          "displayName": "Sam Reed",
          "emails": ["sam@example.test", "sam.work@example.test"]
        }
      ]
      """).fold(error => throw new AssertionError(s"Invalid export fixture: $error"), identity)
    assertThat(exported).isEqualTo(expected)
    assertThat(decode[List[Contact]](exported.noSpaces)).isEqualTo(Right(contacts))

    // Current fields win if an older writer has left obsolete fields in the record.
    val transitional: String = """
      {
        "id": "contact-17",
        "fullName": "Obsolete Name",
        "email": "obsolete@example.test",
        "displayName": "Ada Stone",
        "emails": ["ada@example.test"]
      }
      """
    assertThat(decode[Contact](transitional)).isEqualTo(Right(contacts.head))

    val malformedLegacy: String = """
      {"id": "contact-17", "fullName": "Ada Stone", "email": 42}
      """
    val malformedCurrent: String = """
      {"id": "contact-28", "displayName": "Sam Reed", "emails": "sam@example.test"}
      """
    assertThat(decode[Contact](malformedLegacy).isLeft).isTrue()
    assertThat(decode[Contact](malformedCurrent).isLeft).isTrue()
  }
}
