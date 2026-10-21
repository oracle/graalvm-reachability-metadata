/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Positional tuple conversions: case classes to and from tuples, tuple-to-tuple conversions with
 * nested element transformers, and maps converted to and from sequences of key-value tuples.
 *
 * A utility company ingests smart-meter readings that its query layer returns as plain tuples. The
 * ingestion service turns those rows into typed readings positionally, with partial parsing that
 * reports which tuple element was malformed, and exports typed readings back to tuples for a
 * columnar writer. Per-meter totals travel as maps and are flattened to tuple lists for reports.
 */
class MeterReadingsTupleConversionsTest {

  final case class MeterId(value: String)
  final case class KilowattHours(value: BigDecimal)

  final case class Reading(meter: MeterId, usage: KilowattHours, takenAtEpoch: Long)
  final case class ExportedReading(meter: String, usage: String, takenAtEpoch: Long)
  final case class Tariff(name: String, pricePerKwh: BigDecimal, active: Boolean)

  given Transformer[String, MeterId] = raw => MeterId(raw.trim.toUpperCase)
  given Transformer[MeterId, String] = _.value
  given Transformer[KilowattHours, String] = kwh => kwh.value.bigDecimal.toPlainString
  given Transformer[Int, Long] = _.toLong

  /** Meter firmware sends usage as text; anything unreadable or negative is rejected. */
  given PartialTransformer[String, KilowattHours] = PartialTransformer { raw =>
    partial.Result
      .fromCatching(BigDecimal(raw.trim))
      .flatMap(value =>
        if (value >= 0) partial.Result.fromValue(KilowattHours(value))
        else partial.Result.fromErrorString(s"negative usage $value")
      )
  }

  /** Epoch timestamps arrive as text and must be numeric. */
  given PartialTransformer[String, Long] = PartialTransformer(raw => partial.Result.fromCatching(raw.trim.toLong))

  @Test
  def typedTuplesBecomeCaseClassesPositionally(): Unit = {
    val row: (String, BigDecimal, Boolean) = ("night", BigDecimal("0.125"), true)

    assertEquals(Tariff("night", BigDecimal("0.125"), true), row.transformInto[Tariff])
  }

  @Test
  def caseClassesBecomeTuplesForTheColumnarWriter(): Unit = {
    val reading: Reading = Reading(MeterId("M-17"), KilowattHours(BigDecimal("12.50")), 1700000000L)

    val row: (String, String, Long) = reading.transformInto[(String, String, Long)]

    assertEquals(("M-17", "12.50", 1700000000L), row)
    assertEquals(ExportedReading("M-17", "12.50", 1700000000L), row.transformInto[ExportedReading])
  }

  @Test
  def tupleElementsAreConvertedByElementTransformers(): Unit = {
    val raw: (String, Int) = (" m-3 ", 42)

    val converted: (MeterId, Long) = raw.transformInto[(MeterId, Long)]

    assertEquals((MeterId("M-3"), 42L), converted)
  }

  @Test
  def rawRowsAreParsedIntoReadingsWithPositionalErrorPaths(): Unit = {
    val good: (String, String, String) = ("m-1", "3.75", "1700000100")
    val bad: (String, String, String) = ("m-2", "-1", "yesterday")

    assertEquals(
      Right(Reading(MeterId("M-1"), KilowattHours(BigDecimal("3.75")), 1700000100L)),
      good.transformIntoPartial[Reading].asEither
    )
    assertEquals(
      List("_2" -> "negative usage -1", "_3" -> "For input string: \"yesterday\""),
      bad.transformIntoPartial[Reading].asErrorPathMessageStrings.toList
    )
  }

  @Test
  def batchesOfRawRowsReportTheRowAndElementOfEachFailure(): Unit = {
    val batch: List[(String, String, String)] = List(
      ("m-1", "1.0", "1700000000"),
      ("m-2", "abc", "1700000001"),
      ("m-3", "2.5", "1700000002")
    )

    val result: partial.Result[Vector[Reading]] = batch.transformIntoPartial[Vector[Reading]]

    assertEquals(
      List("(1)._2" -> "Character a is neither a decimal digit number, decimal point, nor \"e\" notation exponential mark."),
      result.asErrorPathMessageStrings.toList
    )
    assertEquals(
      Some(Vector(MeterId("M-1"), MeterId("M-3"))),
      batch.filterNot(_._1 == "m-2").transformIntoPartial[Vector[Reading]].asOption.map(_.map(_.meter))
    )
  }

  @Test
  def mapsAndTupleSequencesConvertIntoEachOther(): Unit = {
    val totals: Map[MeterId, KilowattHours] = Map(
      MeterId("M-1") -> KilowattHours(BigDecimal("10.5")),
      MeterId("M-2") -> KilowattHours(BigDecimal("7"))
    )

    val reportRows: List[(String, String)] = totals.transformInto[List[(String, String)]]
    assertEquals(List("M-1" -> "10.5", "M-2" -> "7"), reportRows.sortBy(_._1))

    val uploaded: Vector[(String, Int)] = Vector(" m-9 " -> 3, "m-8" -> 5)
    val counts: Map[MeterId, Long] = uploaded.transformInto[Map[MeterId, Long]]
    assertEquals(Map(MeterId("M-9") -> 3L, MeterId("M-8") -> 5L), counts)
  }

  @Test
  def readingFieldsCanBeOverriddenWhenBuildingFromTuples(): Unit = {
    val row: (String, String, String) = ("m-4", "0.5", "not-a-timestamp")

    val result: partial.Result[Reading] = row
      .intoPartial[Reading]
      .withFieldConst(_.takenAtEpoch, 1700000999L)
      .transform

    assertEquals(Right(Reading(MeterId("M-4"), KilowattHours(BigDecimal("0.5")), 1700000999L)), result.asEither)
  }
}
