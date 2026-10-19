/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import java.util.Locale

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax.*
import play.api.libs.json.JsArray
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.__

/**
 * Built-in container codecs: collection, array, string-keyed map, tuple and `Locale` Reads and
 * Writes, and the index-qualified error paths they report.
 *
 * A sales dashboard publishes chart series as JSON arrays of `[label, value]` pairs, region
 * rankings as named-field tuples, per-region daily totals as maps of number arrays, and the
 * locales a report is rendered in. A collector reads the same payload back into Scala
 * collections, deduplicating tags into a `Set`, and a malformed upload is rejected at the exact
 * array element that broke.
 */
class SalesDashboardContainerCodecsTest {
  import SalesDashboardContainerCodecsTest.*

  @Test
  def dashboardPayloadRoundTripsThroughContainerCodecs(): Unit = {
    val snapshot: DashboardSnapshot = DashboardSnapshot(
      series = List("Mon" -> 120, "Tue" -> 95, "Wed" -> 143),
      ranking = Seq(("north", 1, BigDecimal("18250.40")), ("south", 2, BigDecimal("9100.00"))),
      dailyTotals = Map("north" -> Array(1200.5, 980.25), "south" -> Array(410.0)),
      tags = Vector("q3", "retail", "q3"),
      locales = Seq(Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("de-CH"))
    )

    val published: JsObject = Json.toJsObject(snapshot)
    assertEquals(
      Json.parse(
        """{
          |  "series": [["Mon", 120], ["Tue", 95], ["Wed", 143]],
          |  "ranking": [
          |    {"region": "north", "rank": 1, "revenue": 18250.40},
          |    {"region": "south", "rank": 2, "revenue": 9100.00}
          |  ],
          |  "dailyTotals": {"north": [1200.5, 980.25], "south": [410.0]},
          |  "tags": ["q3", "retail", "q3"],
          |  "locales": ["pt-BR", "de-CH"]
          |}""".stripMargin
      ),
      published
    )

    val collected: CollectedDashboard = Json.parse(Json.stringify(published)).as[CollectedDashboard]
    assertEquals(List("Mon" -> 120, "Tue" -> 95, "Wed" -> 143), collected.series)
    assertEquals(
      Seq(("north", 1, BigDecimal("18250.40")), ("south", 2, BigDecimal("9100.00"))),
      collected.ranking
    )
    assertEquals(Set("north", "south"), collected.dailyTotals.keySet)
    assertArrayEquals(Array(1200.5, 980.25), collected.dailyTotals("north"))
    assertArrayEquals(Array(410.0), collected.dailyTotals("south"))
    assertEquals(Set("q3", "retail"), collected.tags)
    assertEquals(List(Locale.of("pt", "BR"), Locale.of("de", "CH")), collected.locales)
    assertEquals("Brazil", collected.locales.head.getDisplayCountry(Locale.ENGLISH))
  }

  @Test
  def localesAreExchangedAsTagsOrStructuredObjects(): Unit = {
    val swissGerman: Locale = Locale.forLanguageTag("de-CH")
    val structured: JsObject = Writes.localeObjectWrites.writes(swissGerman)
    assertEquals(Json.obj("language" -> "de", "country" -> "CH"), structured)
    assertEquals(JsSuccess(swissGerman), structured.validate(using Reads.localeObjectReads))
    assertEquals(Some(swissGerman), Json.toJson(swissGerman).asOpt[Locale])
  }

  @Test
  def malformedUploadIsRejectedAtTheOffendingIndexes(): Unit = {
    val upload: JsValue = Json.parse(
      """{
        |  "series": [["Mon", 120], ["Tue", "n/a"]],
        |  "ranking": [{"region": "north", "rank": 1, "revenue": 10}, {"region": "south", "revenue": 5}],
        |  "dailyTotals": {"north": [1.0, "lost"]},
        |  "tags": ["q3"],
        |  "locales": ["en-GB"]
        |}""".stripMargin
    )

    val failures: Map[JsPath, List[String]] = upload.validate[CollectedDashboard] match {
      case JsError(errors) =>
        errors.map((path, details) => path -> details.flatMap((detail: JsonValidationError) => detail.messages).toList).toMap
      case JsSuccess(value, _) => throw new AssertionError(s"Malformed upload was accepted as $value")
    }
    assertEquals(
      Map(
        (__ \ "series")(1) -> List("error.expected.jsnumber"),
        (__ \ "ranking")(1) -> List("'rank' is undefined on object. Available keys are 'region', 'revenue'"),
        (__ \ "dailyTotals" \ "north")(1) -> List("error.expected.jsnumber")
      ),
      failures
    )
  }
}

object SalesDashboardContainerCodecsTest {

  /** Region ranking entries exchanged as objects with named fields. */
  type RegionRank = (String, Int, BigDecimal)

  private val regionRankWrites: OWrites[RegionRank] =
    OWrites.tuple3[String, Int, BigDecimal]("region", "rank", "revenue")

  private val regionRankReads: Reads[RegionRank] =
    Reads.tuple3[String, Int, BigDecimal]("region", "rank", "revenue")

  /** The dashboard state as the publishing service holds it. */
  final case class DashboardSnapshot(
      series: List[(String, Int)],
      ranking: Seq[RegionRank],
      dailyTotals: Map[String, Array[Double]],
      tags: Vector[String],
      locales: Seq[Locale]
  )

  given OWrites[DashboardSnapshot] = OWrites { (snapshot: DashboardSnapshot) =>
    Json.obj(
      "series" -> snapshot.series,
      "ranking" -> JsArray(snapshot.ranking.map(regionRankWrites.writes)),
      "dailyTotals" -> snapshot.dailyTotals,
      "tags" -> snapshot.tags,
      "locales" -> snapshot.locales
    )
  }

  /** The dashboard state as the collecting service reads it back. */
  final case class CollectedDashboard(
      series: List[(String, Int)],
      ranking: Seq[RegionRank],
      dailyTotals: Map[String, Array[Double]],
      tags: Set[String],
      locales: List[Locale]
  )

  given Reads[CollectedDashboard] = (
    (__ \ "series").read[List[(String, Int)]] and
      (__ \ "ranking").read[Seq[RegionRank]](using Reads.seq(using regionRankReads)) and
      (__ \ "dailyTotals").read[Map[String, Array[Double]]] and
      (__ \ "tags").read[Set[String]] and
      (__ \ "locales").read[List[Locale]]
  )(CollectedDashboard.apply)
}
