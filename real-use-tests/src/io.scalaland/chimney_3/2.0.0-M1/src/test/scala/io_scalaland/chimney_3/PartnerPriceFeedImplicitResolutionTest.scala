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

import scala.language.implicitConversions

/**
 * Implicit instance resolution controls: total/partial conflict resolution, Scala 3 `Conversion`s
 * and type-constraint evidence picked up during derivation.
 *
 * A retailer imports price lists from partners. Prices arrive as strings and the application keeps
 * two `given` parsers for them: a lenient total one used by the storefront, which treats garbage as
 * a zero price, and a strict partial one used by the audit import, which rejects it. When both are
 * in scope Chimney refuses to guess, so each import states which one wins with
 * `enableImplicitConflictResolution`, per call, per definition, or project-wide through a
 * `TransformerConfiguration`. Partner SKUs are lifted through an existing `Conversion` with
 * `enableImplicitConversions`, and a generic labelling helper relies on `<:<` evidence with
 * `enableTypeConstraintEvidence`.
 */
class PartnerPriceFeedImplicitResolutionTest {

  final case class Money(cents: Long)

  final case class PartnerLine(sku: String, price: String, discount: String)
  final case class PriceLine(sku: String, price: Money, discount: Money)
  final case class ShelfPrice(sku: String, price: Money, discount: Money)

  final case class Sku(code: String)
  final case class PartnerProduct(sku: String, name: String)
  final case class CatalogProduct(sku: Sku, name: String)

  final case class Labelled[A](label: A, weight: Int)
  final case class ShelfLabel(label: String, weight: Long)

  /** Storefront rule: an unreadable price shows as free rather than breaking the page. */
  given Transformer[String, Money] = raw => Money(raw.trim.toLongOption.getOrElse(0L))

  /** Audit rule: an unreadable or negative price is an error in the imported line. */
  given PartialTransformer[String, Money] = PartialTransformer { raw =>
    partial.Result
      .fromCatching(raw.trim.toLong)
      .flatMap(cents =>
        if (cents >= 0) partial.Result.fromValue(Money(cents))
        else partial.Result.fromErrorString(s"negative price $cents")
      )
  }

  /** Partner SKU codes are normalized by a plain Scala conversion the catalog already owns. */
  given Conversion[String, Sku] = code => Sku(code.trim.toUpperCase)

  /** Grams are widened to the catalog's `Long` weights. */
  given Transformer[Int, Long] = _.toLong

  /** The audit import registers the strict parser as the winner once, for every line it derives. */
  given auditImport: PartialTransformer[PartnerLine, PriceLine] = PartialTransformer
    .define[PartnerLine, PriceLine]
    .enableImplicitConflictResolution(PreferPartialTransformer)
    .buildTransformer

  /** Project-wide policy for the storefront module: the lenient total parser always wins. */
  object StorefrontPolicy {
    transparent inline given TransformerConfiguration[?] =
      TransformerConfiguration.default.enableImplicitConflictResolution(PreferTotalTransformer)
  }

  /** Builds a shelf label from any labelled value the caller can prove is a string. */
  private def shelfLabel[A](labelled: Labelled[A])(using A <:< String): ShelfLabel =
    labelled.into[ShelfLabel].enableTypeConstraintEvidence.transform

  private val validLine: PartnerLine = PartnerLine("ab-1", "1999", " 250 ")
  private val brokenLine: PartnerLine = PartnerLine("ab-2", "n/a", "-5")

  @Test
  def totalTransformationUsesTheOnlyApplicableTotalParser(): Unit = {
    assertEquals(PriceLine("ab-2", Money(0), Money(-5)), brokenLine.transformInto[PriceLine])
  }

  @Test
  def preferringThePartialParserReportsInvalidPricesPerField(): Unit = {
    val result: partial.Result[PriceLine] =
      brokenLine.intoPartial[PriceLine].enableImplicitConflictResolution(PreferPartialTransformer).transform

    assertEquals(
      List("price" -> "For input string: \"n/a\"", "discount" -> "negative price -5"),
      result.asErrorPathMessageStrings.toList
    )
    assertEquals(
      Right(PriceLine("ab-1", Money(1999), Money(250))),
      validLine.intoPartial[PriceLine].enableImplicitConflictResolution(PreferPartialTransformer).transform.asEither
    )
  }

  @Test
  def preferringTheTotalParserAcceptsEveryLineLeniently(): Unit = {
    val result: partial.Result[PriceLine] =
      brokenLine.intoPartial[PriceLine].enableImplicitConflictResolution(PreferTotalTransformer).transform

    assertEquals(Some(PriceLine("ab-2", Money(0), Money(-5))), result.asOption)
  }

  @Test
  def auditDefinitionKeepsItsPreferenceForWholeFeeds(): Unit = {
    val feed: List[PartnerLine] = List(validLine, brokenLine, PartnerLine("ab-3", "100", "x"))

    val result: partial.Result[Vector[PriceLine]] = feed.transformIntoPartial[Vector[PriceLine]]

    assertEquals(
      List(
        "(1).price" -> "For input string: \"n/a\"",
        "(1).discount" -> "negative price -5",
        "(2).discount" -> "For input string: \"x\""
      ),
      result.asErrorPathMessageStrings.toList
    )
    assertEquals(
      List("price" -> "For input string: \"n/a\""),
      brokenLine.transformIntoPartial[PriceLine](failFast = true).asErrorPathMessageStrings.toList
    )
  }

  @Test
  def storefrontConfigurationPrefersTheTotalParserProjectWide(): Unit = {
    import StorefrontPolicy.given

    val feed: List[PartnerLine] = List(validLine, brokenLine)
    val result: partial.Result[List[ShelfPrice]] = feed.transformIntoPartial[List[ShelfPrice]]

    assertEquals(
      Right(List(ShelfPrice("ab-1", Money(1999), Money(250)), ShelfPrice("ab-2", Money(0), Money(-5)))),
      result.asEither
    )
    assertEquals(
      Some(ShelfPrice("ab-2", Money(0), Money(-5))),
      brokenLine.into[ShelfPrice].partial.transform.asOption
    )
  }

  @Test
  def implicitConversionsLiftPartnerSkus(): Unit = {
    val products: List[PartnerProduct] = List(PartnerProduct(" ab-1 ", "Desk lamp"), PartnerProduct("cd-9", "Chair"))

    val catalog: List[CatalogProduct] = products.map(_.into[CatalogProduct].enableImplicitConversions.transform)

    assertEquals(List(CatalogProduct(Sku("AB-1"), "Desk lamp"), CatalogProduct(Sku("CD-9"), "Chair")), catalog)
  }

  @Test
  def typeConstraintEvidenceTransformsGenericFields(): Unit = {
    assertEquals(ShelfLabel("Desk lamp", 1200L), shelfLabel(Labelled("Desk lamp", 1200)))
  }
}
