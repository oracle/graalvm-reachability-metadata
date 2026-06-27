/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_json_2_13

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax._
import play.api.libs.json.Format
import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsLookupResult
import play.api.libs.json.JsNull
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonConfiguration
import play.api.libs.json.JsonNaming
import play.api.libs.json.JsonValidationError
import play.api.libs.json.OFormat
import play.api.libs.json.OptionHandlers
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.__

final case class PostalAddress(streetName: String, city: String, zipCode: String)

object PostalAddress {
  implicit val format: OFormat[PostalAddress] = Json.format[PostalAddress]
}

final case class CustomerProfile(
    customerId: UUID,
    displayName: String,
    birthDate: LocalDate,
    address: PostalAddress,
    aliases: Seq[String],
    marketingOptIn: Option[Boolean]
)

object CustomerProfile {
  implicit val format: OFormat[CustomerProfile] = Json.format[CustomerProfile]
}

final case class ApiToken(tokenId: String, expiresAt: Instant, refreshToken: Option[String])

object ApiToken {
  implicit val configuration: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)
  implicit val format: OFormat[ApiToken] = Json.format[ApiToken]
}

final case class NotificationSettings(emailAddress: String, smsNumber: Option[String], pushChannel: Option[String])

object NotificationSettings {
  implicit val configuration: JsonConfiguration = JsonConfiguration(
    optionHandlers = OptionHandlers.WritesNull
  )
  implicit val format: OFormat[NotificationSettings] = Json.format[NotificationSettings]
}

final case class InventoryItem(sku: String, quantity: Int, tags: Seq[String])

object InventoryItem {
  private val nonBlankString: Reads[String] =
    Reads.StringReads.filter(JsonValidationError("error.expected.nonblank"))(_.trim.nonEmpty)
  private val positiveInt: Reads[Int] =
    Reads.IntReads.filter(JsonValidationError("error.expected.positive"))(_ > 0)

  implicit val reads: Reads[InventoryItem] = (
    (__ \ "sku").read[String](nonBlankString) and
      (__ \ "quantity").read[Int](positiveInt) and
      (__ \ "tags").readWithDefault[Seq[String]](Seq.empty[String])
  )(InventoryItem.apply _)
}

final case class PaymentMethod(cardLast4: String, billingCountry: String, preferred: Boolean)

object PaymentMethod {
  implicit val format: OFormat[PaymentMethod] = (
    (__ \ "card" \ "last4").format[String] and
      (__ \ "billing" \ "country").format[String] and
      (__ \ "preferred").format[Boolean]
  )(
    (cardLast4: String, billingCountry: String, preferred: Boolean) =>
      PaymentMethod(cardLast4, billingCountry, preferred),
    (method: PaymentMethod) => (method.cardLast4, method.billingCountry, method.preferred)
  )
}

object TicketStatus extends Enumeration {
  type TicketStatus = Value
  val Open, PendingReview, Resolved = Value

  implicit val format: Format[TicketStatus] = Json.formatEnum(this)
}

final case class Booking(reference: String, seats: Int)

object Booking {
  val reads: Reads[Booking] = Reads[Booking] { (json: JsValue) =>
    for {
      reference <- (json \ "reference").validate[String]
      seats <- (json \ "seats").validate[Int]
    } yield Booking(reference.trim.toUpperCase, seats)
  }

  val writes: Writes[Booking] = Writes[Booking] { (booking: Booking) =>
    Json.obj(
      "reference" -> booking.reference,
      "seats" -> booking.seats,
      "summary" -> s"${booking.reference}:${booking.seats}"
    )
  }

  implicit val format: Format[Booking] = Format(reads, writes)
}

class Play_json_2_13Test {
  @Test
  def parsesNavigatesAndPrintsJsonValues(): Unit = {
    val rawJson: String =
      """
        |{
        |  "name": "Ada Lovelace",
        |  "active": true,
        |  "score": 99.50,
        |  "languages": ["scala", "java", "json"],
        |  "metadata": {"unicode": "café", "empty": null}
        |}
        |""".stripMargin

    val value: JsValue = Json.parse(rawJson)
    val obj: JsObject = value.as[JsObject]

    assertEquals("Ada Lovelace", (value \ "name").as[String])
    assertEquals(BigDecimal("99.50"), (value \ "score").as[BigDecimal])
    assertTrue((value \ "active").as[Boolean])
    assertEquals(Seq("scala", "java", "json"), (value \ "languages").as[Seq[String]])
    assertEquals(JsNull, (value \ "metadata" \ "empty").get)
    assertEquals(Set("name", "active", "score", "languages", "metadata"), obj.keys.toSet)

    val compact: String = Json.stringify(value)
    val pretty: String = Json.prettyPrint(value)
    val ascii: String = Json.asciiStringify(value)

    assertEquals(value, Json.parse(compact))
    assertEquals(value, Json.parse(pretty))
    assertEquals(value, Json.parse(ascii))
    assertTrue(ascii.contains("\\u00E9") || ascii.contains("\\u00e9"))
  }

  @Test
  def buildsMergesAndUpdatesJsonObjectsAndArrays(): Unit = {
    val base: JsObject = Json.obj(
      "customer" -> Json.obj(
        "id" -> "c-123",
        "preferences" -> Json.obj("theme" -> "light", "email" -> true)
      ),
      "tags" -> Json.arr("new")
    )
    val patch: JsObject = Json.obj(
      "customer" -> Json.obj(
        "preferences" -> Json.obj("theme" -> "dark"),
        "tier" -> "gold"
      ),
      "tags" -> Json.arr("returning")
    )

    val merged: JsObject = base.deepMerge(patch)
    assertEquals("c-123", (merged \ "customer" \ "id").as[String])
    assertEquals("dark", (merged \ "customer" \ "preferences" \ "theme").as[String])
    assertTrue((merged \ "customer" \ "preferences" \ "email").as[Boolean])
    assertEquals("gold", (merged \ "customer" \ "tier").as[String])
    assertEquals(Seq("returning"), (merged \ "tags").as[Seq[String]])

    val appended: JsArray = Json.arr("middle").prepend(JsString("first")).append(JsString("last"))
    assertEquals(Seq("first", "middle", "last"), appended.as[Seq[String]])

    val withoutTags: JsObject = merged - "tags"
    val enriched: JsObject = withoutTags + ("source" -> JsString("test"))
    assertFalse((withoutTags \ "tags").isDefined)
    assertEquals("test", (enriched \ "source").as[String])
  }

  @Test
  def roundTripsBytesAndInputStreams(): Unit = {
    val original: JsObject = Json.obj(
      "message" -> "hello",
      "count" -> 3,
      "nested" -> Json.obj("ok" -> true)
    )

    val bytes: Array[Byte] = Json.toBytes(original)
    assertEquals(original, Json.parse(bytes))
    assertEquals(original, Json.parse(new String(bytes, StandardCharsets.UTF_8)))

    val stream: ByteArrayInputStream = new ByteArrayInputStream(bytes)
    try {
      assertEquals(original, Json.parse(stream))
    } finally {
      stream.close()
    }
  }

  @Test
  def derivesFormatsForNestedDomainModels(): Unit = {
    val profile: CustomerProfile = CustomerProfile(
      customerId = UUID.fromString("00000000-0000-0000-0000-000000000123"),
      displayName = "Grace Hopper",
      birthDate = LocalDate.of(1906, 12, 9),
      address = PostalAddress("Arlington Boulevard", "Arlington", "22201"),
      aliases = Seq("Amazing Grace", "Compiler Pioneer"),
      marketingOptIn = Some(false)
    )

    val json: JsObject = Json.toJsObject(profile)
    assertEquals("00000000-0000-0000-0000-000000000123", (json \ "customerId").as[String])
    assertEquals("1906-12-09", (json \ "birthDate").as[String])
    assertEquals("Arlington", (json \ "address" \ "city").as[String])
    assertEquals(Seq("Amazing Grace", "Compiler Pioneer"), (json \ "aliases").as[Seq[String]])
    assertFalse((json \ "marketingOptIn").as[Boolean])
    assertEquals(profile, json.as[CustomerProfile])
  }

  @Test
  def honorsConfiguredSnakeCaseMacroFormatsAndMissingOptions(): Unit = {
    val token: ApiToken = ApiToken(
      tokenId = "token-42",
      expiresAt = Instant.parse("2030-01-02T03:04:05Z"),
      refreshToken = None
    )

    val json: JsObject = Json.toJsObject(token)
    assertEquals("token-42", (json \ "token_id").as[String])
    assertEquals("2030-01-02T03:04:05Z", (json \ "expires_at").as[String])
    assertFalse((json \ "refresh_token").isDefined)
    assertEquals(token, json.as[ApiToken])

    val withRefresh: JsObject = json + ("refresh_token" -> JsString("refresh-99"))
    assertEquals(Some("refresh-99"), withRefresh.as[ApiToken].refreshToken)
  }

  @Test
  def writesConfiguredOptionalFieldsAsExplicitNulls(): Unit = {
    val settings: NotificationSettings = NotificationSettings(
      emailAddress = "alerts@example.test",
      smsNumber = None,
      pushChannel = Some("mobile")
    )

    val json: JsObject = Json.toJsObject(settings)
    assertEquals("alerts@example.test", (json \ "emailAddress").as[String])
    assertEquals(JsNull, (json \ "smsNumber").get)
    assertEquals("mobile", (json \ "pushChannel").as[String])
    assertEquals(settings, json.as[NotificationSettings])

    val explicitNulls: JsObject = Json.obj(
      "emailAddress" -> "quiet@example.test",
      "smsNumber" -> JsNull,
      "pushChannel" -> JsNull
    )
    assertEquals(
      NotificationSettings("quiet@example.test", None, None),
      explicitNulls.as[NotificationSettings]
    )
  }

  @Test
  def appliesCustomReadsWithDefaultsAndReportsAllValidationPaths(): Unit = {
    val valid: JsObject = Json.obj("sku" -> "BOOK-001", "quantity" -> 4)
    assertEquals(InventoryItem("BOOK-001", 4, Seq.empty[String]), valid.as[InventoryItem])

    val tagged: JsObject = Json.obj("sku" -> "PEN-002", "quantity" -> 12, "tags" -> Json.arr("office", "blue"))
    assertEquals(InventoryItem("PEN-002", 12, Seq("office", "blue")), tagged.as[InventoryItem])

    val invalid: JsObject = Json.obj("sku" -> "  ", "quantity" -> 0, "tags" -> Json.arr("bad"))
    invalid.validate[InventoryItem] match {
      case JsError(errors) =>
        assertTrue(errors.exists { case (path, _) => path.toString.contains("sku") })
        assertTrue(errors.exists { case (path, _) => path.toString.contains("quantity") })
        val messages: List[String] = errors.flatMap { case (_, validationErrors) =>
          validationErrors.flatMap(_.messages)
        }.toList
        assertTrue(messages.contains("error.expected.nonblank"))
        assertTrue(messages.contains("error.expected.positive"))
      case success => fail(s"Expected validation errors, got $success")
    }
  }

  @Test
  def mapsNestedJsonPathsWithCombinatorFormat(): Unit = {
    val json: JsObject = Json.obj(
      "card" -> Json.obj("last4" -> "4242"),
      "billing" -> Json.obj("country" -> "US"),
      "preferred" -> true
    )

    val method: PaymentMethod = json.as[PaymentMethod]
    assertEquals(PaymentMethod("4242", "US", true), method)

    val written: JsObject = Json.toJsObject(method.copy(preferred = false))
    assertEquals("4242", (written \ "card" \ "last4").as[String])
    assertEquals("US", (written \ "billing" \ "country").as[String])
    assertFalse((written \ "preferred").as[Boolean])
    assertEquals(Set("card", "billing", "preferred"), written.keys.toSet)
  }

  @Test
  def readsAndWritesScalaEnumerationValuesByName(): Unit = {
    import TicketStatus.TicketStatus

    val status: TicketStatus = TicketStatus.PendingReview
    val json: JsValue = Json.toJson(status)

    assertEquals(JsString("PendingReview"), json)
    assertEquals(JsSuccess(TicketStatus.Resolved), JsString("Resolved").validate[TicketStatus])
    assertTrue(JsString("Archived").validate[TicketStatus].isError)
  }

  @Test
  def usesCustomReadsAndWritesForDomainSpecificJsonShape(): Unit = {
    val input: JsValue = Json.obj("reference" -> " ab-123 ", "seats" -> 4)
    val booking: Booking = input.as[Booking]
    val encoded: JsValue = Json.toJson(booking)
    val bookings: Seq[Booking] = Json.arr(input, Json.obj("reference" -> "cd-456", "seats" -> 2)).as[Seq[Booking]]

    assertEquals(Booking("AB-123", 4), booking)
    assertEquals("AB-123", (encoded \ "reference").as[String])
    assertEquals(4, (encoded \ "seats").as[Int])
    assertEquals("AB-123:4", (encoded \ "summary").as[String])
    assertEquals(Seq(Booking("AB-123", 4), Booking("CD-456", 2)), bookings)
  }

  @Test
  def transformsJsonBranchesWithPublicPathApi(): Unit = {
    val source: JsObject = Json.obj(
      "customer" -> Json.obj("id" -> "c-123", "name" -> "Ada"),
      "obsolete" -> Json.obj("remove" -> true),
      "audit" -> Json.obj("createdBy" -> "test")
    )

    val picked: JsObject = source.transform((__ \ "customer" \ "id").json.pickBranch).get
    assertEquals(Json.obj("customer" -> Json.obj("id" -> "c-123")), picked)

    val pruned: JsObject = source.transform((__ \ "obsolete").json.prune).get
    assertFalse((pruned \ "obsolete").isDefined)
    assertEquals("Ada", (pruned \ "customer" \ "name").as[String])

    val inserted: JsObject = source.transform((__ \ "customer" \ "status").json.put(JsString("active"))).get
    assertEquals(Json.obj("customer" -> Json.obj("status" -> "active")), inserted)
  }

  @Test
  def readsAndWritesCollectionsMapsTuplesAndTemporalValues(): Unit = {
    val instant: Instant = Instant.parse("2025-05-06T07:08:09Z")
    val localDate: LocalDate = LocalDate.of(2025, 5, 6)
    val payload: JsObject = Json.obj(
      "numbers" -> Seq(1, 2, 3),
      "flags" -> Set(true, false),
      "labels" -> Map("primary" -> "green", "secondary" -> "blue"),
      "pair" -> Json.obj("left" -> "age", "right" -> 42),
      "instant" -> instant,
      "localDate" -> localDate
    )

    assertEquals(Seq(1, 2, 3), (payload \ "numbers").as[Seq[Int]])
    assertEquals(Set(true, false), (payload \ "flags").as[Set[Boolean]])
    assertEquals(Map("primary" -> "green", "secondary" -> "blue"), (payload \ "labels").as[Map[String, String]])
    assertEquals(("age", 42), (payload \ "pair").as[(String, Int)](Reads.tuple2[String, Int]("left", "right")))
    assertEquals(instant, (payload \ "instant").as[Instant])
    assertEquals(localDate, (payload \ "localDate").as[LocalDate])
  }

  @Test
  def performsRecursiveLookupAndSafeMissingValueHandling(): Unit = {
    val document: JsObject = Json.obj(
      "id" -> "root",
      "items" -> Json.arr(
        Json.obj("id" -> "child-1", "details" -> Json.obj("id" -> "detail-1")),
        Json.obj("id" -> "child-2")
      )
    )

    val ids: List[String] = (document \\ "id").map(_.as[String]).toList
    assertEquals(4, ids.size)
    assertEquals(Set("root", "child-1", "detail-1", "child-2"), ids.toSet)

    val missing: JsLookupResult = document \ "doesNotExist"
    assertFalse(missing.isDefined)
    assertEquals(JsString("fallback"), missing.getOrElse(JsString("fallback")))
    assertEquals(None, missing.toOption)
    assertEquals(Some("root"), (document \ "id").validate[String].asOpt)
    assertEquals(None, (document \ "items" \ "missing").validateOpt[String].get)
  }
}
