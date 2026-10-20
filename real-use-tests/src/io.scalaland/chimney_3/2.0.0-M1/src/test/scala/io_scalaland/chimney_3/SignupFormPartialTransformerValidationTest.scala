/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `PartialTransformer` validation with `partial.Result` error accumulation and error paths.
 *
 * A signup endpoint receives a loosely typed form whose fields arrive as optional strings and
 * turns it into a validated account. Chimney unwraps the options, parses numbers and e-mail
 * addresses through `given` partial transformers, and runs per-field checks declared with
 * `withFieldComputedPartial`. The endpoint reports every problem at once, keyed by the path of
 * the offending field inside the nested form, and can switch to fail-fast mode when it only
 * needs to know whether the form is acceptable.
 */
class SignupFormPartialTransformerValidationTest {

  final case class AddressForm(street: Option[String], zip: Option[String])
  final case class SignupForm(
      username: Option[String],
      email: String,
      age: String,
      addresses: List[AddressForm],
      preferences: Map[String, String]
  )

  final case class Email(value: String)
  final case class Address(street: String, zip: Int)
  final case class Account(
      username: String,
      email: Email,
      age: Int,
      addresses: Vector[Address],
      preferences: Map[String, Int]
  )

  /** Numeric form fields are parsed and a malformed number becomes a field error. */
  given PartialTransformer[String, Int] = PartialTransformer(raw => partial.Result.fromCatching(raw.trim.toInt))

  /** An e-mail address must contain exactly one `@` with text on both sides. */
  given PartialTransformer[String, Email] = PartialTransformer { raw =>
    raw.split('@') match {
      case Array(user, domain) if user.nonEmpty && domain.nonEmpty => partial.Result.fromValue(Email(raw))
      case _ => partial.Result.fromErrorString(s"'$raw' is not an e-mail address")
    }
  }

  /** The account mapping: options and nested types are derived, the age gets an extra rule. */
  given PartialTransformer[SignupForm, Account] = PartialTransformer
    .define[SignupForm, Account]
    .withFieldComputedPartial(
      _.age,
      form =>
        partial.Result
          .fromCatching(form.age.trim.toInt)
          .flatMap(age => if (age >= 18) partial.Result.fromValue(age) else partial.Result.fromErrorString("must be adult"))
    )
    .buildTransformer

  private def register(form: SignupForm, failFast: Boolean): partial.Result[Account] =
    form.transformIntoPartial[Account](failFast)

  @Test
  def acceptsValidFormAndBuildsNestedAccount(): Unit = {
    val form: SignupForm = SignupForm(
      username = Some("ada"),
      email = "ada@example.org",
      age = " 36 ",
      addresses = List(AddressForm(Some("Analytical Row 1"), Some("10115"))),
      preferences = Map("newsletter" -> "1", "theme" -> "2")
    )

    val result: partial.Result[Account] = register(form, failFast = false)

    assertEquals(
      Right(
        Account(
          username = "ada",
          email = Email("ada@example.org"),
          age = 36,
          addresses = Vector(Address("Analytical Row 1", 10115)),
          preferences = Map("newsletter" -> 1, "theme" -> 2)
        )
      ),
      result.asEither
    )
    assertEquals(Some("ada"), result.asOption.map(_.username))
  }

  @Test
  def accumulatesEveryFieldErrorWithItsPath(): Unit = {
    val form: SignupForm = SignupForm(
      username = None,
      email = "not-an-email",
      age = "15",
      addresses = List(AddressForm(Some("Main Road 1"), Some("12345")), AddressForm(None, Some("abc"))),
      preferences = Map("newsletter" -> "yes")
    )

    val result: partial.Result[Account] = register(form, failFast = false)

    assertEquals(
      List(
        "username" -> "empty value",
        "email" -> "'not-an-email' is not an e-mail address",
        "<computed for _.age>" -> "must be adult",
        "addresses(1).street" -> "empty value",
        "addresses(1).zip" -> "For input string: \"abc\"",
        "preferences(newsletter)" -> "For input string: \"yes\""
      ),
      result.asErrorPathMessageStrings.toList
    )
    assertEquals(None, result.asOption)
  }

  @Test
  def failFastStopsAtTheFirstInvalidField(): Unit = {
    val form: SignupForm = SignupForm(
      username = None,
      email = "broken",
      age = "abc",
      addresses = Nil,
      preferences = Map.empty
    )

    val errors: List[(String, String)] = register(form, failFast = true).asErrorPathMessageStrings.toList

    assertEquals(List("username" -> "empty value"), errors)
  }

  @Test
  def standalonePartialConversionsReuseGivenParsers(): Unit = {
    val parsed: partial.Result[Address] = AddressForm(Some("Harbour Lane 5"), Some(" 80001 ")).transformIntoPartial[Address]
    assertEquals(Right(Address("Harbour Lane 5", 80001)), parsed.asEither)

    val folded: String = "grace@navy".transformIntoPartial[Email].fold(_.value, _ => "invalid")
    assertEquals("grace@navy", folded)

    val rejected: partial.Result[Email] = "@navy".transformIntoPartial[Email]
    val messages: List[String] = rejected.asErrorPathMessageStrings.map(_._2).toList
    assertEquals(List("'@navy' is not an e-mail address"), messages)
    assertTrue(rejected.asEither.isLeft)
  }
}
