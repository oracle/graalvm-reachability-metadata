/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Decoder, DecodingFailure}
import io.circe.parser.decode
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue, fail}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises missing-versus-null JSON field decoding for partial customer profile updates. */
@Timeout(60)
class CustomerProfilePatchTest {
  private sealed trait Change[+A]
  private case object Keep extends Change[Nothing]
  private case object Clear extends Change[Nothing]
  private final case class Replace[A](value: A) extends Change[A]

  private final case class Profile(
      displayName: Option[String],
      phone: Option[String],
      interests: Option[Vector[String]]
  )

  private final case class ProfilePatch(
      displayName: Change[String],
      phone: Change[String],
      interests: Change[Vector[String]]
  ) {
    def applyTo(profile: Profile): Profile = Profile(
      update(profile.displayName, displayName),
      update(profile.phone, phone),
      update(profile.interests, interests)
    )
  }

  private def update[A](current: Option[A], change: Change[A]): Option[A] = change match {
    case Keep => current
    case Clear => None
    case Replace(value) => Some(value)
  }

  private given [A: Decoder]: Decoder[Change[A]] = Decoder.withReattempt { cursor =>
    if (!cursor.succeeded) Right(Keep)
    else cursor.as[Option[A]].map {
      case None => Clear
      case Some(value) => Replace(value)
    }
  }

  private given Decoder[ProfilePatch] =
    Decoder.forProduct3("displayName", "phone", "interests")(ProfilePatch.apply)

  @Test
  def keepsOmittedFieldsClearsNullFieldsAndReplacesSuppliedValues(): Unit = {
    val original: Profile = Profile(Some("Ada"), Some("555-0100"), Some(Vector("books")))
    val request: String = """{"phone":null,"interests":["gardening","cycling"]}"""
    val patch: ProfilePatch = decode[ProfilePatch](request).fold(error => throw error, identity)

    assertEquals(ProfilePatch(Keep, Clear, Replace(Vector("gardening", "cycling"))), patch)
    val updated: Profile = patch.applyTo(original)
    assertEquals(Profile(Some("Ada"), None, Some(Vector("gardening", "cycling"))), updated)

    val emptyPatch: ProfilePatch = decode[ProfilePatch]("{}").fold(error => throw error, identity)
    assertEquals(updated, emptyPatch.applyTo(updated))

    // Empty collections and strings are supplied values, not instructions to clear a field.
    val emptyValues: ProfilePatch = decode[ProfilePatch](
      """{"displayName":"","phone":"555-0199","interests":[]}"""
    ).fold(error => throw error, identity)
    assertEquals(
      Profile(Some(""), Some("555-0199"), Some(Vector.empty)),
      emptyValues.applyTo(updated)
    )

    val clearInterests: ProfilePatch = decode[ProfilePatch](
      """{"interests":null}"""
    ).fold(error => throw error, identity)
    assertEquals(Profile(Some("Ada"), None, None), clearInterests.applyTo(updated))
  }

  @Test
  def rejectsAnInvalidReplacementWithoutApplyingOtherFields(): Unit = {
    val original: Profile = Profile(Some("Ada"), Some("555-0100"), Some(Vector("books")))
    val request: String = """{"displayName":"Grace","phone":false,"interests":null}"""
    val result: Either[io.circe.Error, Profile] =
      decode[ProfilePatch](request).map(_.applyTo(original))

    result match {
      case Left(error: DecodingFailure) => assertTrue(error.history.nonEmpty)
      case Left(error) => fail("Expected a field decoding failure, got: " + error)
      case Right(_) => fail("A wrongly typed replacement must reject the entire update")
    }
    assertEquals(original, result.getOrElse(original))
  }
}
