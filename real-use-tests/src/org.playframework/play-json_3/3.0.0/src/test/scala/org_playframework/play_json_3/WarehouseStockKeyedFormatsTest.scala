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
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError
import play.api.libs.json.KeyReads
import play.api.libs.json.KeyWrites
import play.api.libs.json.OFormat

/**
 * Domain scalar codecs: value-class formats from `Json.valueFormat`, Scala `Enumeration` formats
 * from `Json.formatEnum`, and `Map` formats keyed by domain types through `KeyReads`/`KeyWrites`.
 *
 * A warehouse service publishes stock snapshots whose SKUs are value classes, whose statuses are
 * an enumeration with wire names, and whose stock levels are maps keyed by SKU and by zone. The
 * snapshot round-trips through JSON with bare-string SKUs and statuses, while an unknown status,
 * a malformed SKU key and a malformed SKU value are each rejected at the path that carries them.
 */
class WarehouseStockKeyedFormatsTest {
  import WarehouseStockKeyedFormatsTest.*

  @Test
  def stockSnapshotsRoundTripWithDomainKeysAndScalars(): Unit = {
    val snapshot: StockSnapshot = StockSnapshot(
      warehouse = "AMS-1",
      featured = Sku("SKU-0042"),
      statuses = Map(Sku("SKU-0042") -> StockStatus.InStock, Sku("SKU-0077") -> StockStatus.Backordered),
      levels = Map(Sku("SKU-0042") -> 120, Sku("SKU-0077") -> 0),
      zoneCapacity = Map(Zone.Cold -> 40, Zone.Bulk -> 900)
    )

    val json: JsValue = Json.toJson(snapshot)

    assertEquals(JsString("SKU-0042"), (json \ "featured").get)
    assertEquals(JsString("in_stock"), (json \ "statuses" \ "SKU-0042").get)
    assertEquals(JsString("backordered"), (json \ "statuses" \ "SKU-0077").get)
    assertEquals(120, (json \ "levels" \ "SKU-0042").as[Int])
    assertEquals(900, (json \ "zoneCapacity" \ "bulk").as[Int])
    assertEquals(Set("cold", "bulk"), (json \ "zoneCapacity").as[Map[String, Int]].keySet)

    val restored: StockSnapshot = Json.parse(Json.stringify(json)).as[StockSnapshot]
    assertEquals(snapshot, restored)

    val restocking: Seq[Sku] = restored.statuses.collect {
      case (sku, StockStatus.Backordered) => sku
    }.toSeq
    assertEquals(Seq(Sku("SKU-0077")), restocking)
  }

  @Test
  def standaloneCodecsReadAndWriteDomainScalars(): Unit = {
    assertEquals(JsString("discontinued"), Json.toJson(StockStatus.Discontinued))
    assertEquals(JsSuccess(StockStatus.Backordered), JsString("backordered").validate[StockStatus.Value])
    assertEquals(JsSuccess(Sku("SKU-0100")), JsString("SKU-0100").validate[Sku])

    val byZone: JsValue = Json.obj("cold" -> Json.arr("SKU-0001"), "dry" -> Json.arr("SKU-0002", "SKU-0003"))
    val placement: Map[Zone.Value, List[Sku]] = byZone.as[Map[Zone.Value, List[Sku]]]
    assertEquals(List(Sku("SKU-0002"), Sku("SKU-0003")), placement(Zone.Dry))
    assertEquals(byZone, Json.toJson(placement))
  }

  @Test
  def malformedDomainValuesAreRejectedAtTheirPaths(): Unit = {
    val unknownStatus: JsValue = Json.parse(
      """{"warehouse": "AMS-1", "featured": "SKU-0042",
        | "statuses": {"SKU-0042": "lost"},
        | "levels": {"SKU-0042": 1}, "zoneCapacity": {"cold": 1}}""".stripMargin
    )
    assertEquals(Set(JsPath \ "statuses" \ "SKU-0042"), errorPaths(unknownStatus.validate[StockSnapshot]))

    val badKey: JsValue = Json.parse(
      """{"warehouse": "AMS-1", "featured": "SKU-0042",
        | "statuses": {"SKU-0042": "in_stock"},
        | "levels": {"sku-42": 1}, "zoneCapacity": {"cold": 1}}""".stripMargin
    )
    val keyErrors: Seq[JsonValidationError] = errorsAt(badKey.validate[StockSnapshot], JsPath \ "levels" \ "sku-42")
    assertEquals(Seq("error.sku.format"), keyErrors.flatMap(_.messages))

    val badFeatured: JsValue = Json.parse(
      """{"warehouse": "AMS-1", "featured": "nope",
        | "statuses": {}, "levels": {}, "zoneCapacity": {"vault": 1}}""".stripMargin
    )
    val paths: Set[JsPath] = errorPaths(badFeatured.validate[StockSnapshot])
    assertTrue(paths.contains(JsPath \ "featured"), paths.toString)
    assertTrue(paths.contains(JsPath \ "zoneCapacity" \ "vault"), paths.toString)
  }
}

object WarehouseStockKeyedFormatsTest {

  /** A stock-keeping unit identifier carried as a bare JSON string. */
  final case class Sku(value: String) extends AnyVal

  object Sku {
    private val Shape: scala.util.matching.Regex = "SKU-[0-9]{4}".r

    def parse(raw: String): JsResult[Sku] =
      if (Shape.matches(raw)) JsSuccess(Sku(raw)) else JsError("error.sku.format")

    given Format[Sku] = {
      val plain: Format[Sku] = Json.valueFormat[Sku]
      Format(plain.flatMapResult(sku => parse(sku.value)), plain)
    }

    given KeyReads[Sku] = KeyReads(parse)

    given KeyWrites[Sku] = KeyWrites(_.value)
  }

  /** Stock statuses published under lower-case wire names. */
  object StockStatus extends Enumeration {
    val InStock: Value = Value("in_stock")
    val Backordered: Value = Value("backordered")
    val Discontinued: Value = Value("discontinued")

    given Format[StockStatus.Value] = Json.formatEnum(StockStatus)
  }

  /** Storage zones of a warehouse, used as map keys. */
  object Zone extends Enumeration {
    val Cold: Value = Value("cold")
    val Dry: Value = Value("dry")
    val Bulk: Value = Value("bulk")

    given KeyReads[Zone.Value] = KeyReads { raw =>
      Zone.values.find(_.toString == raw) match {
        case Some(zone) => JsSuccess(zone)
        case None => JsError("error.zone.unknown")
      }
    }

    given KeyWrites[Zone.Value] = KeyWrites(_.toString)
  }

  final case class StockSnapshot(
      warehouse: String,
      featured: Sku,
      statuses: Map[Sku, StockStatus.Value],
      levels: Map[Sku, Int],
      zoneCapacity: Map[Zone.Value, Int]
  )

  object StockSnapshot {
    given OFormat[StockSnapshot] = Json.format[StockSnapshot]
  }

  private def errorsOf(result: JsResult[?]): Seq[(JsPath, Seq[JsonValidationError])] = result match {
    case JsError(errors) => errors.map((path, errs) => (path, errs.toSeq)).toSeq
    case JsSuccess(value, _) => throw new AssertionError(s"expected a validation error, got $value")
  }

  def errorPaths(result: JsResult[?]): Set[JsPath] = errorsOf(result).map(_._1).toSet

  def errorsAt(result: JsResult[?], path: JsPath): Seq[JsonValidationError] =
    errorsOf(result).filter(_._1 == path).flatMap(_._2)
}
