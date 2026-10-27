/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.data.Form
import play.api.data.Forms._

/** Exercises Play form binding and validation for a newsletter registration workflow. */
@Timeout(60)
class RegistrationFormTest {
  case class Registration(
      displayName: String,
      emailAddress: String,
      age: Int,
      company: Option[String],
      topics: List[String],
      acceptedTerms: Boolean
  )

  @Test
  def rejectsInvalidSubmissionAndBindsCorrectedRegistration(): Unit = {
    val registrationForm: Form[Registration] = Form(
      mapping(
        "displayName" -> nonEmptyText(maxLength = 80),
        "emailAddress" -> email,
        "age" -> number(min = 18, max = 120),
        "company" -> optional(text),
        "topics" -> list(nonEmptyText),
        "acceptedTerms" -> boolean.verifying("terms.required", (accepted: Boolean) => accepted)
      )(Registration.apply)(Registration.unapply)
    )

    val submitted: Map[String, String] = Map(
      "displayName" -> "",
      "emailAddress" -> "not-an-email",
      "age" -> "17",
      "company" -> "",
      "topics[0]" -> "",
      "acceptedTerms" -> "false"
    )
    val rejected: Form[Registration] = registrationForm.bind(submitted)

    assertTrue(rejected.hasErrors)
    assertTrue(rejected.value.isEmpty)
    assertEquals(
      Set("displayName", "emailAddress", "age", "topics[0]", "acceptedTerms"),
      rejected.errors.map(_.key).toSet
    )
    assertEquals(Some("not-an-email"), rejected("emailAddress").value)
    assertEquals(Some("17"), rejected("age").value)
    assertEquals(Seq("terms.required"), rejected("acceptedTerms").errors.map(_.message))

    val corrected: Map[String, String] = submitted ++ Map(
      "displayName" -> "Ada Lovelace",
      "emailAddress" -> "ada@example.org",
      "age" -> "36",
      "topics[0]" -> "engineering",
      "topics[1]" -> "events",
      "acceptedTerms" -> "true"
    )
    val accepted: Form[Registration] = registrationForm.bind(corrected)
    val expected: Registration = Registration(
      "Ada Lovelace", "ada@example.org", 36, None, List("engineering", "events"), true
    )

    assertFalse(accepted.hasErrors, accepted.errors.toString)
    assertEquals(Some(expected), accepted.value)

    val editForm: Form[Registration] = registrationForm.fill(
      expected.copy(company = Some("Analytical Engines"))
    )
    assertEquals(Some("Analytical Engines"), editForm("company").value)
    assertEquals(Some("events"), editForm("topics[1]").value)
    assertEquals(Some(expected.copy(company = Some("Analytical Engines"))),
      registrationForm.bind(editForm.data).value)
  }
}
