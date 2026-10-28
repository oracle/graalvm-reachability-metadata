/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax.*
import play.api.libs.json.JsDefined
import play.api.libs.json.JsError
import play.api.libs.json.JsNull
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsUndefined
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonConfiguration
import play.api.libs.json.OWrites
import play.api.libs.json.OptionHandlers
import play.api.libs.json.Reads

/**
 * Null-aware optional-field handling: `OptionHandlers` in macro-derived `Json.reads`/`Json.writes`,
 * `writeOptionWithNull`, and absent-versus-null detection through `JsLookup` for PATCH payloads.
 *
 * A contact directory accepts partial updates where an absent field keeps the stored value, an
 * explicit `null` clears it and a value replaces it. It publishes compact snapshots that omit
 * empty fields to its own UI, full snapshots with explicit `null`s to an audit feed whose schema
 * requires every key, and a hand-written sync payload that always carries the phone key.
 */
class ContactPatchNullHandlingTest {
  import ContactPatchNullHandlingTest.*

  @Test
  def partialUpdatesDistinguishAbsentNullAndPresentFields(): Unit = {
    val stored: Contact = Contact(
      contactId = "c-17",
      fullName = "Ada Lovelace",
      nickname = Some("Countess"),
      phone = Some("+44 20 7946 0000"),
      email = Some("ada@example.org")
    )

    val patchBody: JsValue = Json.parse(
      """{"fullName": "Augusta Ada King", "nickname": null, "email": "ada.king@example.org"}"""
    )
    val patch: ContactPatch = ContactPatch.fromJson(patchBody).get
    assertEquals(
      ContactPatch(
        fullName = Some("Augusta Ada King"),
        nickname = FieldUpdate.Clear,
        phone = FieldUpdate.Keep,
        email = FieldUpdate.Set("ada.king@example.org")
      ),
      patch
    )

    val updated: Contact = patch.applyTo(stored)
    assertEquals(
      Contact("c-17", "Augusta Ada King", None, Some("+44 20 7946 0000"), Some("ada.king@example.org")),
      updated
    )

    val rejected: JsResult[ContactPatch] =
      ContactPatch.fromJson(Json.parse("""{"phone": 442079460000, "email": ["a@b.c"]}"""))
    val rejectedPaths: Set[JsPath] = rejected match {
      case JsError(errors) => errors.map(_._1).toSet
      case JsSuccess(value, _) => throw new AssertionError(s"Invalid patch was accepted as $value")
    }
    assertEquals(Set(JsPath \ "phone", JsPath \ "email"), rejectedPaths)
  }

  @Test
  def snapshotsOmitOrWriteNullsAccordingToTheConfiguredOptionHandler(): Unit = {
    val contact: Contact = Contact("c-42", "Grace Hopper", None, Some("+1 202 555 0100"), None)

    val compact: JsObject = Json.toJsObject(contact)(using Contact.compactWrites)
    assertEquals(
      Json.obj("contactId" -> "c-42", "fullName" -> "Grace Hopper", "phone" -> "+1 202 555 0100"),
      compact
    )

    val audit: JsObject = Json.toJsObject(contact)(using Contact.auditWrites)
    assertEquals(
      Json.obj(
        "contactId" -> "c-42",
        "fullName" -> "Grace Hopper",
        "nickname" -> JsNull,
        "phone" -> "+1 202 555 0100",
        "email" -> JsNull
      ),
      audit
    )
    val auditText: String = Json.stringify(audit)
    assertTrue(auditText.contains("\"nickname\":null"))
    assertTrue(auditText.contains("\"email\":null"))

    // Both representations read back to the same contact: null and absent both mean None.
    assertEquals(JsSuccess(contact), compact.validate[Contact](using Contact.reads))
    assertEquals(JsSuccess(contact), audit.validate[Contact](using Contact.auditReads))
    assertEquals(JsSuccess(contact), Json.parse(Json.stringify(audit)).validate[Contact](using Contact.reads))

    val missingName: JsResult[Contact] = Json.obj("contactId" -> "c-43").validate[Contact](using Contact.reads)
    assertTrue(missingName.isError)
    assertEquals(Some(JsPath \ "fullName"), missingName.asEither.left.toOption.map(_.head._1))
  }

  @Test
  def syncPayloadAlwaysCarriesThePhoneKey(): Unit = {
    val withoutPhone: Contact = Contact("c-7", "Alan Turing", Some("Prof"), None, Some("alan@example.org"))
    val withPhone: Contact = withoutPhone.copy(phone = Some("+44 161 496 0000"))

    val syncWithout: JsObject = Contact.syncWrites.writes(withoutPhone)
    val syncWith: JsObject = Contact.syncWrites.writes(withPhone)

    assertEquals(Json.obj("id" -> "c-7", "phone" -> JsNull, "email" -> "alan@example.org"), syncWithout)
    assertEquals(
      Json.obj("id" -> "c-7", "phone" -> "+44 161 496 0000", "email" -> "alan@example.org"),
      syncWith
    )
    assertEquals(Set("id", "phone", "email"), syncWithout.keys.toSet)

    val noEmail: JsObject = Contact.syncWrites.writes(withoutPhone.copy(email = None))
    assertEquals(Json.obj("id" -> "c-7", "phone" -> JsNull), noEmail)
  }
}

object ContactPatchNullHandlingTest {

  final case class Contact(
      contactId: String,
      fullName: String,
      nickname: Option[String],
      phone: Option[String],
      email: Option[String]
  )

  object Contact {

    /** Audit feeds require every key, so empty options are written as explicit nulls. */
    private val auditConfiguration: JsonConfiguration.Aux[Json.MacroOptions] =
      JsonConfiguration(optionHandlers = OptionHandlers.WritesNull)

    val reads: Reads[Contact] = Json.reads[Contact]

    val compactWrites: OWrites[Contact] = Json.writes[Contact]

    val auditReads: Reads[Contact] = Json.configured(using auditConfiguration).reads[Contact]

    val auditWrites: OWrites[Contact] = Json.configured(using auditConfiguration).writes[Contact]

    /** The downstream sync service expects `phone` always, `email` only when known. */
    val syncWrites: OWrites[Contact] = (
      (JsPath \ "id").write[String] and
        (JsPath \ "phone").writeOptionWithNull[String] and
        (JsPath \ "email").writeNullable[String]
    )((contact: Contact) => (contact.contactId, contact.phone, contact.email))
  }

  /** What a PATCH request asks for a single optional field. */
  enum FieldUpdate {
    case Keep
    case Clear
    case Set(value: String)

    def applyTo(current: Option[String]): Option[String] = this match {
      case Keep => current
      case Clear => None
      case Set(value) => Some(value)
    }
  }

  final case class ContactPatch(
      fullName: Option[String],
      nickname: FieldUpdate,
      phone: FieldUpdate,
      email: FieldUpdate
  ) {
    def applyTo(contact: Contact): Contact = contact.copy(
      fullName = fullName.getOrElse(contact.fullName),
      nickname = nickname.applyTo(contact.nickname),
      phone = phone.applyTo(contact.phone),
      email = email.applyTo(contact.email)
    )
  }

  object ContactPatch {

    /** Reads a clearable field: absent keeps, `null` clears, a string replaces. */
    private def fieldUpdate(name: String): Reads[FieldUpdate] = Reads { (body: JsValue) =>
      (body \ name) match {
        case JsDefined(JsNull) => JsSuccess(FieldUpdate.Clear)
        case JsDefined(value) =>
          value.validate[String].map(FieldUpdate.Set(_)).repath(JsPath \ name)
        case _: JsUndefined => JsSuccess(FieldUpdate.Keep)
      }
    }

    private val reads: Reads[ContactPatch] = (
      (JsPath \ "fullName").readNullable[String] and
        fieldUpdate("nickname") and
        fieldUpdate("phone") and
        fieldUpdate("email")
    )(ContactPatch.apply)

    def fromJson(body: JsValue): JsResult[ContactPatch] = body.validate[ContactPatch](using reads)
  }
}
