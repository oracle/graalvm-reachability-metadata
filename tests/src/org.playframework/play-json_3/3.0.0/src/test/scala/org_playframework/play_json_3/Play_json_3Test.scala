/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax._
import play.api.libs.json.Format
import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.__

final case class PlayJsonAddress(street: String, city: String, postalCode: String)

object PlayJsonAddress {
  implicit val format: OFormat[PlayJsonAddress] = Json.format[PlayJsonAddress]
}

final case class PlayJsonCustomer(
    name: String,
    age: Int,
    address: PlayJsonAddress,
    tags: Seq[String],
    preferences: Map[String, String],
    loyaltyNumber: Option[String]
)

object PlayJsonCustomer {
  implicit val format: OFormat[PlayJsonCustomer] = Json.format[PlayJsonCustomer]
}

final case class PlayJsonBooking(reference: String, seats: Int)

object PlayJsonBooking {
  val reads: Reads[PlayJsonBooking] = Reads[PlayJsonBooking] { (json: JsValue) =>
    for {
      reference <- (json \ "reference").validate[String]
      seats <- (json \ "seats").validate[Int]
    } yield PlayJsonBooking(reference.trim.toUpperCase, seats)
  }

  val writes: Writes[PlayJsonBooking] = Writes[PlayJsonBooking] { (booking: PlayJsonBooking) =>
    Json.obj(
      "reference" -> booking.reference,
      "seats" -> booking.seats,
      "summary" -> s"${booking.reference}:${booking.seats}"
    )
  }

  implicit val format: Format[PlayJsonBooking] = Format(reads, writes)
}

final case class PlayJsonLineItem(sku: String, quantity: Int, warehouse: String)

object PlayJsonLineItem {
  implicit val format: OFormat[PlayJsonLineItem] = (
    (__ \ "line" \ "sku").format[String] and
      (__ \ "line" \ "quantity").format[Int] and
      (__ \ "fulfillment" \ "warehouse").format[String]
  )(
    (sku: String, quantity: Int, warehouse: String) => PlayJsonLineItem(sku, quantity, warehouse),
    (lineItem: PlayJsonLineItem) => (lineItem.sku, lineItem.quantity, lineItem.warehouse)
  )
}

class Play_json_3Test {
  @Test
  def parsesNavigatesAndRendersJsonDocuments(): Unit = {
    val document: JsValue = Json.parse(
      """
        |{
        |  "id": 7,
        |  "title": "Native JSON",
        |  "published": true,
        |  "authors": [
        |    { "name": "Ada", "role": "author" },
        |    { "name": "Grace", "role": "reviewer" }
        |  ],
        |  "metrics": { "rating": 4.75, "downloads": 1250 }
        |}
        |""".stripMargin
    )

    assertEquals(7, (document \ "id").as[Int])
    assertEquals("Native JSON", (document \ "title").as[String])
    assertTrue((document \ "published").as[Boolean])
    assertEquals(BigDecimal("4.75"), (document \ "metrics" \ "rating").as[BigDecimal])
    assertEquals(Seq("Ada", "Grace"), (document \\ "name").map(_.as[String]))
    assertTrue((document \ "missing").asOpt[JsValue].isEmpty)

    val rendered: String = Json.stringify(document)
    val reparsed: JsValue = Json.parse(rendered)
    val prettyPrinted: String = Json.prettyPrint(reparsed)

    assertEquals(document, reparsed)
    assertTrue(prettyPrinted.contains("Native JSON"))
    assertTrue(prettyPrinted.contains(System.lineSeparator()))
  }

  @Test
  def buildsAndUpdatesJsonObjectsAndArrays(): Unit = {
    val base: JsObject = Json.obj(
      "name" -> "catalog",
      "items" -> Json.arr(
        Json.obj("sku" -> "A-1", "price" -> 12.50),
        Json.obj("sku" -> "B-2", "price" -> 18.75)
      ),
      "active" -> true
    )

    val enriched: JsObject = base + ("metadata" -> Json.obj("source" -> "test")) - "active"
    val merged: JsObject = enriched ++ Json.obj("version" -> 3, "name" -> "catalog-v3")
    val items: JsArray = (merged \ "items").as[JsArray]

    assertFalse(merged.keys.contains("active"))
    assertEquals("catalog-v3", (merged \ "name").as[String])
    assertEquals("test", (merged \ "metadata" \ "source").as[String])
    assertEquals(2, items.value.size)
    assertEquals(Seq("A-1", "B-2"), items.value.map(item => (item \ "sku").as[String]))
    assertEquals(JsNumber(3), merged.value("version"))
  }

  @Test
  def derivesFormatsForNestedCaseClassesAndCollections(): Unit = {
    val customer: PlayJsonCustomer = PlayJsonCustomer(
      name = "Dorothy Vaughan",
      age = 50,
      address = PlayJsonAddress("1 Research Way", "Hampton", "23666"),
      tags = Seq("mathematician", "manager"),
      preferences = Map("theme" -> "dark", "language" -> "scala"),
      loyaltyNumber = Some("LV-42")
    )

    val json: JsValue = Json.toJson(customer)
    val decoded: JsResult[PlayJsonCustomer] = Json.fromJson[PlayJsonCustomer](json)
    val withoutOptionalField: JsValue = Json.obj(
      "name" -> "Mary Jackson",
      "age" -> 34,
      "address" -> Json.obj("street" -> "2 Orbit Lane", "city" -> "Hampton", "postalCode" -> "23666"),
      "tags" -> Json.arr("engineer"),
      "preferences" -> Json.obj("language" -> "scala")
    )

    assertEquals("Dorothy Vaughan", (json \ "name").as[String])
    assertEquals("Hampton", (json \ "address" \ "city").as[String])
    assertEquals(Seq("mathematician", "manager"), (json \ "tags").as[Seq[String]])
    assertEquals(Map("theme" -> "dark", "language" -> "scala"), (json \ "preferences").as[Map[String, String]])
    assertEquals(JsSuccess(customer), decoded)
    assertEquals(None, Json.fromJson[PlayJsonCustomer](withoutOptionalField).get.loyaltyNumber)
  }

  @Test
  def reportsValidationErrorsWithJsonPaths(): Unit = {
    val invalid: JsValue = Json.obj(
      "name" -> "Katherine Johnson",
      "age" -> "not-a-number",
      "address" -> Json.obj("street" -> "3 Flight Court", "postalCode" -> "23666"),
      "tags" -> Json.arr("physicist"),
      "preferences" -> Json.obj("language" -> "scala")
    )

    val result: JsResult[PlayJsonCustomer] = Json.fromJson[PlayJsonCustomer](invalid)

    assertTrue(result.isError)
    result match {
      case JsError(errors) =>
        val failingPaths: scala.collection.Seq[String] = errors.map { case (path, _) => path.toString }
        assertTrue(failingPaths.contains("/age"))
        assertTrue(failingPaths.contains("/address/city"))
      case JsSuccess(_, _) =>
        throw new AssertionError("Invalid JSON unexpectedly decoded successfully")
    }
  }

  @Test
  def usesCustomReadsAndWritesForDomainSpecificJsonShape(): Unit = {
    val input: JsValue = Json.obj("reference" -> " ab-123 ", "seats" -> 4)
    val booking: PlayJsonBooking = input.as[PlayJsonBooking]
    val encoded: JsValue = Json.toJson(booking)
    val bookings: Seq[PlayJsonBooking] = Json.arr(input, Json.obj("reference" -> "cd-456", "seats" -> 2)).as[Seq[PlayJsonBooking]]

    assertEquals(PlayJsonBooking("AB-123", 4), booking)
    assertEquals("AB-123", (encoded \ "reference").as[String])
    assertEquals(4, (encoded \ "seats").as[Int])
    assertEquals("AB-123:4", (encoded \ "summary").as[String])
    assertEquals(Seq(PlayJsonBooking("AB-123", 4), PlayJsonBooking("CD-456", 2)), bookings)
  }

  @Test
  def mapsNestedJsonShapeWithFunctionalFormatCombinators(): Unit = {
    val document: JsValue = Json.obj(
      "line" -> Json.obj("sku" -> "BK-42", "quantity" -> 3),
      "fulfillment" -> Json.obj("warehouse" -> "east")
    )
    val decoded: JsResult[PlayJsonLineItem] = Json.fromJson[PlayJsonLineItem](document)
    val encoded: JsValue = Json.toJson(PlayJsonLineItem("PN-1", 12, "west"))

    assertEquals(JsSuccess(PlayJsonLineItem("BK-42", 3, "east")), decoded)
    assertEquals(
      Json.obj(
        "line" -> Json.obj("sku" -> "PN-1", "quantity" -> 12),
        "fulfillment" -> Json.obj("warehouse" -> "west")
      ),
      encoded
    )
  }

  @Test
  def readsPrimitiveOptionsAndCollectionValues(): Unit = {
    val json: JsValue = Json.obj(
      "strings" -> Json.arr("a", "b", "c"),
      "numbers" -> Json.arr(1, 2, 3),
      "maybeText" -> "present",
      "explicitNull" -> play.api.libs.json.JsNull,
      "object" -> Json.obj("key" -> "value")
    )

    assertEquals(Seq("a", "b", "c"), (json \ "strings").as[Seq[String]])
    assertEquals(List(1, 2, 3), (json \ "numbers").as[List[Int]])
    assertEquals(Some("present"), (json \ "maybeText").asOpt[String])
    assertEquals(None, (json \ "explicitNull").asOpt[String])
    assertEquals(None, (json \ "absent").asOpt[String])
    assertEquals(Map("key" -> JsString("value")), (json \ "object").as[Map[String, JsValue]])
  }

  @Test
  def transformsJsonBranchesWithPathCombinators(): Unit = {
    val source: JsObject = Json.obj(
      "customer" -> Json.obj("id" -> "c-123", "name" -> "Ada"),
      "obsolete" -> Json.obj("remove" -> true),
      "audit" -> Json.obj("createdBy" -> "test")
    )

    val picked: JsObject = source.transform((__ \ "customer" \ "id").json.pickBranch).get
    val pruned: JsObject = source.transform((__ \ "obsolete").json.prune).get
    val inserted: JsObject = source.transform((__ \ "customer" \ "status").json.put(JsString("active"))).get

    assertEquals(Json.obj("customer" -> Json.obj("id" -> "c-123")), picked)
    assertFalse((pruned \ "obsolete").isDefined)
    assertEquals("Ada", (pruned \ "customer" \ "name").as[String])
    assertEquals(Json.obj("customer" -> Json.obj("status" -> "active")), inserted)
  }
}
