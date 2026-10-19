/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import cats.Eq
import cats.data.Chain
import cats.data.NonEmptyChain
import cats.data.NonEmptyList
import cats.data.NonEmptyVector
import cats.data.Validated
import cats.data.ValidatedNec
import cats.syntax.all.*
import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.cats.*
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial
import io.scalaland.chimney.partial.syntax.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The chimney-cats integration: cats non-empty collections and `Chain` as transformation
 * targets, `ValidatedNec` rules lifted into `partial.Result`, results reported as `Validated`,
 * and cats type classes (`Parallel`, `Semigroup`, `Eq`, `Arrow`) for results and transformers.
 *
 * A warehouse imports purchase orders exported by a supplier as loosely typed rows. The domain
 * model uses cats data types to make "at least one line" a compile-time guarantee, the existing
 * business rules are written as `ValidatedNec` and plugged into Chimney unchanged, and the
 * import report is produced as a `ValidatedNec` the rest of the cats-based service consumes.
 */
class WarehouseOrderImportCatsIntegrationTest {

  final case class RawLine(sku: String, quantity: String)
  final case class RawOrder(orderId: String, lines: List[RawLine], tags: List[String], docks: Vector[String])

  final case class Line(sku: String, quantity: Int)
  final case class Order(orderId: String, lines: NonEmptyList[Line], tags: Chain[String], docks: NonEmptyVector[String])

  final case class LineSummary(sku: String, units: Int)

  /** A cats-validated business rule the warehouse already uses outside of Chimney. */
  private def positiveQuantity(raw: String): ValidatedNec[String, Int] =
    raw.trim.toIntOption match {
      case Some(value) if value > 0 => value.validNec
      case Some(value) => s"quantity $value must be positive".invalidNec
      case None => s"'$raw' is not a number".invalidNec
    }

  /** Order identifiers follow the supplier's `PO-<digits>` scheme. */
  private def orderNumber(raw: String): ValidatedNec[String, String] =
    if (raw.startsWith("PO-") && raw.drop(3).nonEmpty && raw.drop(3).forall(_.isDigit)) raw.validNec
    else s"'$raw' is not a purchase order number".invalidNec

  /** Each line runs the `ValidatedNec` rule, lifted into a `partial.Result` with `asResult`. */
  given PartialTransformer[RawLine, Line] = PartialTransformer
    .define[RawLine, Line]
    .withFieldComputedPartial(_.quantity, line => positiveQuantity(line.quantity).asResult)
    .buildTransformer

  /** Lists become `NonEmptyList`/`NonEmptyVector` (partial) and `Chain` (total) via chimney-cats. */
  given PartialTransformer[RawOrder, Order] = PartialTransformer
    .define[RawOrder, Order]
    .withFieldComputedPartial(_.orderId, order => orderNumber(order.orderId).asResult)
    .buildTransformer

  private def importOrder(raw: RawOrder): ValidatedNec[partial.Error, Order] =
    raw.transformIntoPartial[Order].asValidatedNec

  private def report(result: ValidatedNec[partial.Error, Order]): List[String] =
    result.fold(_.toList.map(error => s"${error.path.asString}: ${error.message.asString}"), _ => Nil)

  @Test
  def importsAValidOrderIntoCatsCollections(): Unit = {
    val raw: RawOrder = RawOrder(
      orderId = "PO-1001",
      lines = List(RawLine("BOLT-M8", " 40 "), RawLine("NUT-M8", "40")),
      tags = List("fragile", "express"),
      docks = Vector("D1")
    )

    val imported: ValidatedNec[partial.Error, Order] = importOrder(raw)

    val expected: Order = Order(
      orderId = "PO-1001",
      lines = NonEmptyList.of(Line("BOLT-M8", 40), Line("NUT-M8", 40)),
      tags = Chain("fragile", "express"),
      docks = NonEmptyVector.one("D1")
    )
    assertEquals(Validated.valid(expected), imported)
    assertEquals(2, imported.toOption.get.lines.length)
    assertEquals(List("fragile", "express"), imported.toOption.get.tags.toList)
  }

  @Test
  def reportsEveryProblemAsValidatedNecWithFieldPaths(): Unit = {
    val raw: RawOrder = RawOrder(
      orderId = "1001",
      lines = List(RawLine("BOLT-M8", "0"), RawLine("NUT-M8", "forty")),
      tags = Nil,
      docks = Vector.empty
    )

    val imported: ValidatedNec[partial.Error, Order] = importOrder(raw)

    assertTrue(imported.isInvalid)
    assertEquals(
      List(
        "<computed for _.orderId>: '1001' is not a purchase order number",
        "lines(0) => <computed for _.quantity>: quantity 0 must be positive",
        "lines(1) => <computed for _.quantity>: 'forty' is not a number",
        "docks: empty value"
      ),
      report(imported)
    )
  }

  @Test
  def rejectsAnOrderWithoutLinesBecauseTheTargetIsNonEmpty(): Unit = {
    val raw: RawOrder = RawOrder("PO-1002", lines = Nil, tags = List("backorder"), docks = Vector("D2", "D3"))

    val result: partial.Result[Order] = raw.transformIntoPartial[Order]

    assertEquals(List("lines" -> "empty value"), result.asErrorPathMessageStrings.toList)
    assertEquals(
      result.asErrorPathMessageStrings.toList,
      result.asValidatedNel.fold(_.toList.map(e => e.path.asString -> e.message.asString), _ => Nil)
    )
  }

  @Test
  def importsABatchInParallelAccumulatingErrorsAcrossOrders(): Unit = {
    val good: RawOrder = RawOrder("PO-2001", List(RawLine("WASHER", "500")), Nil, Vector("D1"))
    val badNumber: RawOrder = RawOrder("PO-2002", List(RawLine("SCREW", "-3")), Nil, Vector("D1"))
    val badId: RawOrder = RawOrder("2003", List(RawLine("RIVET", "10")), Nil, Vector("D4"))

    val okBatch: partial.Result[List[Order]] = List(good, good.copy(orderId = "PO-2004")).parTraverse(_.transformIntoPartial[Order])
    assertEquals(List("PO-2001", "PO-2004"), okBatch.asOption.get.map(_.orderId))

    val failedBatch: partial.Result[List[Order]] = List(good, badNumber, badId).parTraverse(_.transformIntoPartial[Order])
    assertEquals(
      List("quantity -3 must be positive", "'2003' is not a purchase order number"),
      failedBatch.asErrorPathMessageStrings.map(_._2).toList
    )

    val sequential: partial.Result[List[Order]] = List(good, badNumber, badId).traverse(_.transformIntoPartial[Order])
    assertEquals(List("quantity -3 must be positive"), sequential.asErrorPathMessageStrings.map(_._2).toList)
  }

  @Test
  def combinesErrorsWithSemigroupAndComparesResultsWithEq(): Unit = {
    val first: partial.Result[Order] = RawOrder("x", List(RawLine("A", "1")), Nil, Vector("D1")).transformIntoPartial[Order]
    val second: partial.Result[Order] = RawOrder("PO-1", Nil, Nil, Vector("D1")).transformIntoPartial[Order]

    val combined: Option[partial.Result.Errors] = (first, second) match {
      case (e1: partial.Result.Errors, e2: partial.Result.Errors) => Some(e1 |+| e2)
      case _ => None
    }
    assertEquals(
      List("<computed for _.orderId>" -> "'x' is not a purchase order number", "lines" -> "empty value"),
      combined.get.asErrorPathMessageStrings.toList
    )

    given Eq[Line] = Eq.fromUniversalEquals
    val again: partial.Result[Line] = RawLine("A", "7").transformIntoPartial[Line]
    assertTrue(again === partial.Result.fromValue(Line("A", 7)))
    assertTrue(again =!= partial.Result.fromValue(Line("A", 8)))
  }

  @Test
  def composesTransformersWithCatsArrowSyntax(): Unit = {
    val toSummary: Transformer[Line, LineSummary] = Transformer
      .define[Line, LineSummary]
      .withFieldRenamed(_.quantity, _.units)
      .buildTransformer
    val describe: Transformer[LineSummary, String] = summary => s"${summary.units} x ${summary.sku}"

    val pipeline: Transformer[Line, String] = toSummary >>> describe
    assertEquals("12 x HINGE", pipeline.transform(Line("HINGE", 12)))

    val shouted: Transformer[Line, String] = pipeline.map(_.toUpperCase)
    assertEquals("12 X HINGE", shouted.transform(Line("hinge", 12)))

    val parseThenSummarize: PartialTransformer[RawLine, LineSummary] =
      summon[PartialTransformer[RawLine, Line]].map(line => toSummary.transform(line))
    assertEquals(Right(LineSummary("HINGE", 3)), parseThenSummarize.transform(RawLine("HINGE", "3")).asEither)

    given Transformer[Line, LineSummary] = toSummary
    val nonEmptySummaries: NonEmptyChain[LineSummary] =
      NonEmptyChain(Line("A", 1), Line("B", 2)).transformInto[NonEmptyChain[LineSummary]]
    assertEquals(List(LineSummary("A", 1), LineSummary("B", 2)), nonEmptySummaries.toChain.toList)
  }
}
