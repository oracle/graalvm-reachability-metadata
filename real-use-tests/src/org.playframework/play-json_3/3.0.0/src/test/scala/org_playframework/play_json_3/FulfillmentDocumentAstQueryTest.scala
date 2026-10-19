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
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsDefined
import play.api.libs.json.JsFalse
import play.api.libs.json.JsLookupResult
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsTrue
import play.api.libs.json.JsUndefined
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.__

/**
 * Untyped JSON AST construction, navigation and editing through `JsValue`, `JsObject`,
 * `JsArray` and `JsLookupResult` without any case-class codec.
 *
 * A fulfillment service receives shipment documents from many carriers whose schemas drift, so it
 * never binds them to a model. It builds outbound documents with `Json.obj`/`Json.arr` and the
 * `JsObject`/`JsArray` editing operators, queries inbound ones with `\`, `\\`, array indexes and
 * `JsPath` lookups, pattern-matches on the value subtypes to compute totals, and explains a
 * missing field to the carrier with the lookup's own diagnostic.
 */
class FulfillmentDocumentAstQueryTest {
  import FulfillmentDocumentAstQueryTest.*

  @Test
  def outboundShipmentIsAssembledAndEditedAsAnAst(): Unit = {
    val draft: JsObject = Json.obj(
      "shipmentId" -> "SHP-1001",
      "express" -> false,
      "parcels" -> Json.arr(
        Json.obj("sku" -> "LAMP-01", "qty" -> 2, "weightKg" -> BigDecimal("1.25")),
        Json.obj("sku" -> "BULB-60", "qty" -> 10, "weightKg" -> BigDecimal("0.05"))
      ),
      "notes" -> JsNull
    )

    val parcels: JsArray = (draft \ "parcels").as[JsArray]
    val withCable: JsArray =
      parcels :+ Json.obj("sku" -> "CABLE-3M", "qty" -> 1, "weightKg" -> BigDecimal("0.30"))
    val withInsert: JsArray =
      withCable.prepend(Json.obj("sku" -> "MANUAL", "qty" -> 1, "weightKg" -> BigDecimal("0.10")))
    assertEquals(4, withInsert.value.size)
    assertEquals(JsString("MANUAL"), (withInsert \ 0 \ "sku").get)
    assertEquals(JsString("CABLE-3M"), (withInsert \ 3 \ "sku").get)

    val finalized: JsObject = (draft - "notes" - "express" ++ Json.obj("parcels" -> withInsert)) +
      ("carrier" -> JsString("ParcelNet")) +
      ("express" -> JsTrue)
    assertEquals(
      Seq("shipmentId", "parcels", "carrier", "express"),
      finalized.fields.map(_._1).toSeq
    )
    assertEquals(Some(JsTrue), finalized.value.get("express"))
    assertFalse(finalized.keys.contains("notes"))

    assertEquals(BigDecimal("3.40"), totalWeight(finalized))
    assertEquals(14, totalUnits(finalized))
    assertEquals(
      """{"shipmentId":"SHP-1001","parcels":[""" +
        """{"sku":"MANUAL","qty":1,"weightKg":0.1},""" +
        """{"sku":"LAMP-01","qty":2,"weightKg":1.25},""" +
        """{"sku":"BULB-60","qty":10,"weightKg":0.05},""" +
        """{"sku":"CABLE-3M","qty":1,"weightKg":0.3}],""" +
        """"carrier":"ParcelNet","express":true}""",
      Json.stringify(finalized)
    )
    assertEquals(finalized, Json.parse(Json.prettyPrint(finalized)))
  }

  @Test
  def inboundManifestIsQueriedWithoutBindingToAModel(): Unit = {
    val manifest: JsValue = Json.parse(
      """{
        |  "carrier": {"name": "Fjord Freight", "contact": {"email": "ops@fjord.example"}},
        |  "legs": [
        |    {"from": "OSL", "to": "BGO", "items": [{"sku": "LAMP-01", "qty": 2}]},
        |    {"from": "BGO", "to": "TOS", "items": [
        |      {"sku": "BULB-60", "qty": 10},
        |      {"sku": "LAMP-01", "qty": 1, "fragile": true}
        |    ]}
        |  ],
        |  "customs": null
        |}""".stripMargin
    )

    assertEquals("ops@fjord.example", (manifest \ "carrier" \ "contact" \ "email").as[String])
    assertEquals(Some("TOS"), (manifest \ "legs" \ 1 \ "to").asOpt[String])
    assertEquals(JsString("OSL"), (manifest \ "legs").head.apply("from"))
    assertEquals(JsString("BGO"), (manifest \ "legs").last.apply("from"))

    val skus: Seq[String] = (manifest \\ "sku").map(_.as[String]).toSeq
    assertEquals(Seq("LAMP-01", "BULB-60", "LAMP-01"), skus)
    assertEquals(skus, (__ \\ "sku")(manifest).map(_.as[String]))
    assertEquals(
      List(JsString("OSL"), JsString("BGO")),
      (__ \ "legs" \\ "from")(manifest)
    )

    val unitsBySku: Map[String, Int] = (manifest \\ "items")
      .flatMap(_.as[JsArray].value)
      .groupMapReduce(item => (item \ "sku").as[String])(item => (item \ "qty").as[Int])(_ + _)
    assertEquals(Map("LAMP-01" -> 3, "BULB-60" -> 10), unitsBySku)

    val fragile: Seq[String] = (manifest \\ "items").flatMap(_.as[JsArray].value).collect {
      case item: JsObject if (item \ "fragile").asOpt[Boolean].contains(true) =>
        (item \ "sku").as[String]
    }.toSeq
    assertEquals(Seq("LAMP-01"), fragile)

    (manifest \ "customs") match {
      case JsDefined(JsNull) => ()
      case other => throw new AssertionError(s"customs should be an explicit null, got $other")
    }
    assertEquals(None, (manifest \ "customs").asOpt[String])
    assertTrue((manifest \ "customs").isDefined)
  }

  @Test
  def missingFieldsAreReportedWithTheLookupDiagnostic(): Unit = {
    val manifest: JsObject = Json.obj(
      "carrier" -> Json.obj("name" -> "Fjord Freight"),
      "legs" -> Json.arr(Json.obj("from" -> "OSL", "to" -> "BGO"))
    )

    val missingEmail: JsLookupResult = manifest \ "carrier" \ "contact" \ "email"
    assertTrue(missingEmail.isEmpty)
    assertEquals(None, missingEmail.toOption)
    assertEquals(JsString("unknown"), missingEmail.getOrElse(JsString("unknown")))
    assertEquals(
      "'contact' is undefined on object. Available keys are 'name'",
      diagnostic(manifest \ "carrier" \ "contact")
    )

    val missingLeg: JsLookupResult = manifest \ "legs" \ 3
    assertTrue(missingLeg.isEmpty)
    assertTrue(diagnostic(missingLeg).contains("out of bounds"), diagnostic(missingLeg))

    val notAnArray: JsLookupResult = manifest \ "carrier" \ 0
    assertTrue(notAnArray.isEmpty)
    assertTrue(diagnostic(notAnArray).contains("is not an array"), diagnostic(notAnArray))

    assertTrue((manifest \\ "email").isEmpty)
    assertEquals(Nil, (__ \ "carrier" \ "contact" \ "email")(manifest))
    assertFalse((manifest \ "legs" \ 0 \ "from").validate[String].isError)
    assertTrue((manifest \ "legs" \ 0 \ "eta").validate[String].isError)
  }

  @Test
  def documentsAreMergedAndRenderedForTransport(): Unit = {
    val carrierDefaults: JsObject = Json.obj(
      "label" -> Json.obj("format" -> "PDF", "size" -> "A6"),
      "insured" -> false,
      "tags" -> Json.arr("standard")
    )
    val override_ : JsObject = Json.obj(
      "label" -> Json.obj("size" -> "4x6"),
      "insured" -> true,
      "tags" -> Json.arr("fragile")
    )

    val merged: JsObject = carrierDefaults.deepMerge(override_)
    assertEquals(Json.obj("format" -> "PDF", "size" -> "4x6"), (merged \ "label").get)
    assertEquals(JsTrue, (merged \ "insured").get)
    assertEquals(Json.arr("fragile"), (merged \ "tags").get)

    val shallow: JsObject = carrierDefaults ++ override_
    assertEquals(Json.obj("size" -> "4x6"), (shallow \ "label").get)

    val recipient: JsObject = Json.obj("name" -> "S\u00f8ren \u00c5se", "city" -> "Troms\u00f8")
    assertEquals(
      "{\"name\":\"S\\u00F8ren \\u00C5se\",\"city\":\"Troms\\u00F8\"}",
      Json.asciiStringify(recipient)
    )
    assertEquals(recipient, Json.parse(Json.asciiStringify(recipient)))
    assertEquals(recipient, Json.parse(Json.toBytes(recipient)))

    assertEquals(
      Seq("object", "boolean", "array", "null", "number", "string"),
      Seq(
        merged \ "label",
        merged \ "insured",
        merged \ "tags",
        Json.obj("v" -> JsNull) \ "v",
        Json.obj("v" -> 3) \ "v",
        Json.obj("v" -> "x") \ "v"
      ).map(lookup => kind(lookup.get))
    )
    assertEquals("boolean", kind(JsFalse))
    assertEquals("boolean", kind(JsBoolean(true)))
  }
}

object FulfillmentDocumentAstQueryTest {

  /** Sums `qty * weightKg` over every parcel by matching on the AST node types. */
  def totalWeight(shipment: JsObject): BigDecimal =
    (shipment \ "parcels").as[JsArray].value.foldLeft(BigDecimal(0)) {
      case (sum, parcel: JsObject) =>
        (parcel.value.get("qty"), parcel.value.get("weightKg")) match {
          case (Some(JsNumber(qty)), Some(JsNumber(weight))) => sum + qty * weight
          case _ => throw new AssertionError(s"parcel without qty or weight: $parcel")
        }
      case (_, other) => throw new AssertionError(s"parcel is not an object: $other")
    }

  /** Counts the units of every parcel through recursive `\\` lookup. */
  def totalUnits(shipment: JsObject): Int =
    (shipment \\ "qty").map(_.as[Int]).sum

  /** Returns the message a lookup that found nothing carries for the caller. */
  def diagnostic(lookup: JsLookupResult): String = lookup match {
    case undefined: JsUndefined => undefined.validationError.message
    case JsDefined(value) => throw new AssertionError(s"expected a missing value, found $value")
  }

  /** Names the JSON type of a node the way the carrier API documentation does. */
  def kind(value: JsValue): String = value match {
    case _: JsObject => "object"
    case _: JsArray => "array"
    case _: JsBoolean => "boolean"
    case _: JsNumber => "number"
    case _: JsString => "string"
    case JsNull => "null"
  }
}
