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

/** Exercises Play form binding and validation for a newsletter signup and edit form. */
@Timeout(60)
class SignupFormTest {
  case class Contact(name: String, email: String)
  case class Signup(contact: Contact, age: Int, topics: List[String], phone: Option[String], consent: Boolean)

  @Test
  def validatesSignupAndRefillsAnEditForm(): Unit = {
    val signupForm: Form[Signup] = Form(
      mapping(
        "contact" -> mapping(
          "name" -> nonEmptyText,
          "email" -> email
        )(Contact.apply)(Contact.unapply),
        "age" -> number(min = 18, max = 120),
        "topics" -> list(nonEmptyText),
        "phone" -> optional(text),
        "consent" -> boolean.verifying("consent.required", accepted => accepted)
      )(Signup.apply)(Signup.unapply)
    )
    val submitted: Map[String, String] = Map(
      "contact.name" -> "Ada Lovelace",
      "contact.email" -> "ada@example.com",
      "age" -> "36",
      "topics[0]" -> "science",
      "topics[1]" -> "computing",
      "phone" -> "",
      "consent" -> "true"
    )

    val bound: Form[Signup] = signupForm.bind(submitted)
    assertFalse(bound.hasErrors, bound.errors.mkString(", "))
    val expected: Signup = Signup(
      Contact("Ada Lovelace", "ada@example.com"), 36, List("science", "computing"), None, true
    )
    assertEquals(expected, bound.get)

    val edited: Signup = bound.get.copy(phone = Some("+44 1234567890"), topics = List("computing"))
    val editForm: Form[Signup] = signupForm.fill(edited)
    assertEquals(Some("Ada Lovelace"), editForm("contact.name").value)
    assertEquals(Some("+44 1234567890"), editForm("phone").value)
    assertEquals(Some("computing"), editForm("topics[0]").value)
    assertEquals(edited, signupForm.bind(editForm.data).get)

    val invalidSubmission: Map[String, String] = submitted ++ Map(
      "contact.email" -> "not-an-email",
      "age" -> "17",
      "consent" -> "false"
    )
    val rejected: Form[Signup] = signupForm.bind(invalidSubmission)
    assertTrue(rejected.hasErrors)
    assertTrue(rejected.value.isEmpty)
    assertEquals(Set("contact.email", "age", "consent"), rejected.errors.map(_.key).toSet)
    assertEquals(Some("not-an-email"), rejected("contact.email").value)
    assertEquals(Some("Ada Lovelace"), rejected("contact.name").value)
    assertEquals(Seq("consent.required"), rejected("consent").errors.flatMap(_.messages))

    val corrected: Form[Signup] = signupForm.bind(rejected.data ++ Map(
      "contact.email" -> "ada@example.com", "age" -> "36", "consent" -> "true"
    ))
    assertFalse(corrected.hasErrors, corrected.errors.mkString(", "))
    assertEquals(expected, corrected.get)
  }
}
