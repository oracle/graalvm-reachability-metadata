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
import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonConfiguration
import play.api.libs.json.JsonNaming
import play.api.libs.json.OFormat

/**
 * Macro-derived formats for a sealed trait family, tagged by a configured discriminator field.
 *
 * A notification dispatcher queues outbound messages for several channels as one JSON array.
 * Each message is a case of the sealed `Notification` trait; `Json.format` derives one format for
 * the whole family, writing a `channel` field whose value comes from a custom type-naming
 * strategy. The dispatcher serialises a mixed batch, reads it back into the right subtypes,
 * routes the decoded messages, and rejects entries with an unknown or missing channel tag.
 */
class NotificationDispatchSealedFamilyTest {
  import NotificationDispatchSealedFamilyTest.*

  @Test
  def mixedBatchRoundTripsThroughDiscriminatedFamilyFormat(): Unit = {
    val batch: List[Notification] = List(
      EmailNotification("ada@example.org", "Invoice 42", List("invoice-42.pdf")),
      SmsNotification("+441632960001", "Your code is 918273", 2),
      PushNotification("device-7f3a", "Parcel shipped", Map("orderId" -> "A-1001", "carrier" -> "DHL"))
    )

    val written: JsValue = Json.toJson(batch)
    val expected: JsValue = Json.parse(
      """[
        |  {"channel": "email", "recipient": "ada@example.org", "subject": "Invoice 42",
        |   "attachments": ["invoice-42.pdf"]},
        |  {"channel": "sms", "phoneNumber": "+441632960001", "text": "Your code is 918273",
        |   "segments": 2},
        |  {"channel": "push", "deviceToken": "device-7f3a", "title": "Parcel shipped",
        |   "payload": {"orderId": "A-1001", "carrier": "DHL"}}
        |]""".stripMargin
    )
    assertEquals(expected, written)

    val wireText: String = Json.stringify(written)
    val decoded: JsResult[List[Notification]] = Json.parse(wireText).validate[List[Notification]]
    assertEquals(JsSuccess(batch), decoded)

    val routes: List[String] = decoded.get.map(route)
    assertEquals(
      List("smtp:ada@example.org:1", "sms-gateway:+441632960001:2", "apns:device-7f3a:2"),
      routes
    )
  }

  @Test
  def singleSubtypeIsTaggedWhenWrittenThroughTheFamilyFormat(): Unit = {
    val notification: Notification = SmsNotification("+15550100", "Ready for pickup", 1)

    val written: JsObject = Json.toJson(notification).as[JsObject]

    assertEquals(Some(JsString("sms")), written.value.get("channel"))
    assertEquals(
      Set("channel", "phoneNumber", "text", "segments"),
      written.keys.toSet
    )
  }

  @Test
  def entriesWithUnknownOrMissingChannelAreRejected(): Unit = {
    val unknownChannel: JsValue = Json.parse(
      """{"channel": "fax", "number": "+15550199", "pages": 3}"""
    )
    val unknownResult: JsResult[Notification] = unknownChannel.validate[Notification]
    assertTrue(unknownResult.isError, s"expected an error but got $unknownResult")

    val missingChannel: JsValue = Json.parse(
      """{"recipient": "bob@example.org", "subject": "Hello", "attachments": []}"""
    )
    val missingResult: JsResult[Notification] = missingChannel.validate[Notification]
    missingResult match {
      case JsError(errors) =>
        assertEquals(List(JsPath \ "channel"), errors.map(_._1).toList)
      case JsSuccess(value, _) =>
        throw new AssertionError(s"expected an error but decoded $value")
    }

    val malformedEmail: JsValue = Json.parse(
      """{"channel": "email", "recipient": "carol@example.org", "subject": 7, "attachments": []}"""
    )
    val malformedResult: JsResult[Notification] = malformedEmail.validate[Notification]
    malformedResult match {
      case JsError(errors) =>
        assertEquals(List(JsPath \ "subject"), errors.map(_._1).toList)
      case JsSuccess(value, _) =>
        throw new AssertionError(s"expected an error but decoded $value")
    }
  }

  @Test
  def queueDocumentKeepsValidEntriesAndReportsInvalidOnes(): Unit = {
    val queue: JsValue = Json.parse(
      """{"queue": [
        |  {"channel": "push", "deviceToken": "d-1", "title": "Hi", "payload": {}},
        |  {"channel": "carrier-pigeon", "loft": "north"},
        |  {"channel": "email", "recipient": "dan@example.org", "subject": "Ok", "attachments": []}
        |]}""".stripMargin
    )

    val entries: Seq[JsValue] = (queue \ "queue").as[JsArray].value.toSeq
    val outcomes: Seq[JsResult[Notification]] = entries.map(_.validate[Notification])

    val accepted: Seq[Notification] = outcomes.collect { case JsSuccess(value, _) => value }
    assertEquals(
      Seq(
        PushNotification("d-1", "Hi", Map.empty),
        EmailNotification("dan@example.org", "Ok", Nil)
      ),
      accepted
    )
    assertEquals(Seq(false, true, false), outcomes.map(_.isError))
  }
}

object NotificationDispatchSealedFamilyTest {

  sealed trait Notification

  final case class EmailNotification(recipient: String, subject: String, attachments: List[String])
      extends Notification

  final case class SmsNotification(phoneNumber: String, text: String, segments: Int)
      extends Notification

  final case class PushNotification(deviceToken: String, title: String, payload: Map[String, String])
      extends Notification

  /** Maps a fully qualified subtype name such as `...EmailNotification` to the tag `email`. */
  private val channelNaming: JsonNaming = JsonNaming { (fullName: String) =>
    val simpleName: String = fullName.split("[.$]").filter(_.nonEmpty).last
    simpleName.stripSuffix("Notification").toLowerCase
  }

  given JsonConfiguration = JsonConfiguration(discriminator = "channel", typeNaming = channelNaming)

  given OFormat[EmailNotification] = Json.format[EmailNotification]

  given OFormat[SmsNotification] = Json.format[SmsNotification]

  given OFormat[PushNotification] = Json.format[PushNotification]

  given OFormat[Notification] = Json.format[Notification]

  /** Picks the delivery backend and reports how many units of work it carries. */
  def route(notification: Notification): String = notification match {
    case EmailNotification(recipient, _, attachments) => s"smtp:$recipient:${attachments.size}"
    case SmsNotification(phoneNumber, _, segments) => s"sms-gateway:$phoneNumber:$segments"
    case PushNotification(deviceToken, _, payload) => s"apns:$deviceToken:${payload.size}"
  }
}
