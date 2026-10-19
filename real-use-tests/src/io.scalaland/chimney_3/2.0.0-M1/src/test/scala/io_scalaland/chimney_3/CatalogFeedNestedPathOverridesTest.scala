/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Nested path selectors in `withField*` overrides: `everyItem`, `everyMapKey`, `everyMapValue`,
 * `matching[Subtype]`, `matchingSome`, `matchingLeft` and `matchingRight`.
 *
 * A product catalog exports its products into a partner feed. The feed mostly mirrors the catalog,
 * but a handful of fields deep inside collections, maps, options, sealed hierarchies and eithers
 * have no source counterpart. Instead of declaring a separate transformer for every nested type,
 * the export wires those fields in a single derivation by addressing them through path selectors,
 * and the import direction validates nested values with partial overrides whose errors carry the
 * path of the offending element.
 */
class CatalogFeedNestedPathOverridesTest {

  final case class Locale(code: String)
  final case class Title(text: String)
  final case class Money(amount: BigDecimal, currency: String)
  final case class Variant(sku: String, price: Money, stock: Int)

  sealed trait Availability
  final case class InStock(warehouse: String) extends Availability
  final case class Backordered(days: Int) extends Availability
  case object Discontinued extends Availability

  final case class InternalTeam(name: String)
  final case class ExternalSupplier(id: Int, name: String)

  final case class Product(
      id: String,
      variants: List[Variant],
      titles: Map[Locale, Title],
      promo: Option[Money],
      availability: Availability,
      supplier: Either[InternalTeam, ExternalSupplier]
  )

  final case class LocaleTag(tag: String)
  final case class TitleEntry(text: String, length: Int)
  final case class MoneyEntry(amount: BigDecimal, currency: String)
  final case class VariantEntry(sku: String, price: MoneyEntry, stock: Int, listed: Boolean)
  final case class PromoEntry(amount: BigDecimal, currency: String, badge: String)

  /** The partner schema spells its availability cases differently, so they match ignoring case. */
  sealed trait FeedAvailability
  final case class INSTOCK(warehouse: String, pickupAvailable: Boolean) extends FeedAvailability
  final case class BACKORDERED(days: Int, notifyCustomer: Boolean) extends FeedAvailability
  case object DISCONTINUED extends FeedAvailability

  final case class TeamEntry(name: String, escalationChannel: String)
  final case class SupplierEntry(id: Int, name: String, portalUrl: String)

  final case class FeedProduct(
      id: String,
      variants: Vector[VariantEntry],
      titles: Map[LocaleTag, TitleEntry],
      promo: Option[PromoEntry],
      availability: FeedAvailability,
      supplier: Either[TeamEntry, SupplierEntry]
  )

  final case class VariantRow(sku: String, price: String, currency: String, stock: String)
  final case class ImportedVariant(sku: String, price: BigDecimal, currency: String, stock: Int)
  final case class TagRow(value: String)
  final case class ImportedTag(value: String, normalized: String)
  final case class ProductRow(id: String, variants: List[VariantRow], tags: Map[String, TagRow])
  final case class ImportedProduct(id: String, variants: List[ImportedVariant], tags: Map[String, ImportedTag])

  private def toFeed(product: Product): FeedProduct =
    product
      .into[FeedProduct]
      .withFieldComputedFrom(_.variants.everyItem)(_.variants.everyItem.listed, variant => variant.stock > 0)
      .withFieldRenamed(_.titles.everyMapKey.code, _.titles.everyMapKey.tag)
      .withFieldComputedFrom(_.titles.everyMapValue)(_.titles.everyMapValue.length, title => title.text.length)
      .withFieldComputedFrom(_.promo.matchingSome)(
        _.promo.matchingSome.badge,
        money => s"-${money.amount} ${money.currency}"
      )
      .withFieldComputedFrom(_.availability.matching[InStock])(
        _.availability.matching[INSTOCK].pickupAvailable,
        inStock => inStock.warehouse.startsWith("store-")
      )
      .withFieldConst(_.availability.matching[BACKORDERED].notifyCustomer, true)
      .withFieldConst(_.supplier.matchingLeft.escalationChannel, "#catalog-oncall")
      .withFieldComputedFrom(_.supplier.matchingRight)(
        _.supplier.matchingRight.portalUrl,
        supplier => s"https://partners.example.com/suppliers/${supplier.id}"
      )
      .enableCustomSubtypeNameComparison(TransformedNamesComparison.CaseInsensitiveEquality)
      .transform

  private def importRow(row: ProductRow): partial.Result[ImportedProduct] =
    row
      .intoPartial[ImportedProduct]
      .withFieldComputedPartialFrom(_.variants.everyItem)(
        _.variants.everyItem.price,
        variant => partial.Result.fromOption(variant.price.toDoubleOption.map(BigDecimal(_)))
      )
      .withFieldComputedPartialFrom(_.variants.everyItem)(
        _.variants.everyItem.stock,
        variant =>
          partial.Result.fromEitherString(
            variant.stock.toIntOption.filter(_ >= 0).toRight(s"invalid stock '${variant.stock}'")
          )
      )
      .withFieldComputedFrom(_.tags.everyMapValue)(_.tags.everyMapValue.normalized, tag => tag.value.trim.toLowerCase)
      .transform

  @Test
  def exportsNestedFieldsOfEveryCollectionMapOptionSubtypeAndEitherBranch(): Unit = {
    val lamp: Product = Product(
      id = "P-100",
      variants = List(
        Variant("LAMP-WHITE", Money(BigDecimal("49.90"), "EUR"), 12),
        Variant("LAMP-BLACK", Money(BigDecimal("52.50"), "EUR"), 0)
      ),
      titles = Map(Locale("en") -> Title("Desk lamp"), Locale("de") -> Title("Schreibtischlampe")),
      promo = Some(Money(BigDecimal("5"), "EUR")),
      availability = InStock("store-berlin"),
      supplier = Right(ExternalSupplier(42, "Lumen GmbH"))
    )

    assertEquals(
      FeedProduct(
        id = "P-100",
        variants = Vector(
          VariantEntry("LAMP-WHITE", MoneyEntry(BigDecimal("49.90"), "EUR"), 12, listed = true),
          VariantEntry("LAMP-BLACK", MoneyEntry(BigDecimal("52.50"), "EUR"), 0, listed = false)
        ),
        titles = Map(
          LocaleTag("en") -> TitleEntry("Desk lamp", 9),
          LocaleTag("de") -> TitleEntry("Schreibtischlampe", 17)
        ),
        promo = Some(PromoEntry(BigDecimal("5"), "EUR", "-5 EUR")),
        availability = INSTOCK("store-berlin", pickupAvailable = true),
        supplier = Right(SupplierEntry(42, "Lumen GmbH", "https://partners.example.com/suppliers/42"))
      ),
      toFeed(lamp)
    )
  }

  @Test
  def appliesTheOverridesOnlyToTheBranchesTheProductActuallyHas(): Unit = {
    val chair: Product = Product(
      id = "P-200",
      variants = Nil,
      titles = Map.empty,
      promo = None,
      availability = Backordered(14),
      supplier = Left(InternalTeam("Furniture"))
    )
    val retired: Product = chair.copy(
      id = "P-300",
      availability = Discontinued,
      variants = List(Variant("STOOL-OAK", Money(BigDecimal("80"), "EUR"), 3))
    )

    val exportedChair: FeedProduct = toFeed(chair)
    assertEquals(BACKORDERED(14, notifyCustomer = true), exportedChair.availability)
    assertEquals(Left(TeamEntry("Furniture", "#catalog-oncall")), exportedChair.supplier)
    assertEquals(None, exportedChair.promo)
    assertEquals(Vector.empty, exportedChair.variants)
    assertEquals(Map.empty, exportedChair.titles)

    val exportedRetired: FeedProduct = toFeed(retired)
    assertEquals(DISCONTINUED, exportedRetired.availability)
    assertEquals(
      Vector(VariantEntry("STOOL-OAK", MoneyEntry(BigDecimal("80"), "EUR"), 3, listed = true)),
      exportedRetired.variants
    )
  }

  @Test
  def importsRowsWhoseNestedValuesAllParse(): Unit = {
    val row: ProductRow = ProductRow(
      id = "P-400",
      variants = List(VariantRow("MUG-RED", "8.5", "EUR", "120"), VariantRow("MUG-BLUE", "9", "EUR", "0")),
      tags = Map("material" -> TagRow("  Ceramic "), "care" -> TagRow("DISHWASHER"))
    )

    assertEquals(
      Some(
        ImportedProduct(
          id = "P-400",
          variants = List(
            ImportedVariant("MUG-RED", BigDecimal(8.5), "EUR", 120),
            ImportedVariant("MUG-BLUE", BigDecimal(9.0), "EUR", 0)
          ),
          tags = Map(
            "material" -> ImportedTag("  Ceramic ", "ceramic"),
            "care" -> ImportedTag("DISHWASHER", "dishwasher")
          )
        )
      ),
      importRow(row).asOption
    )
  }

  @Test
  def reportsEveryInvalidNestedValueUnderItsElementPath(): Unit = {
    val row: ProductRow = ProductRow(
      id = "P-500",
      variants = List(
        VariantRow("PEN-1", "1.20", "EUR", "10"),
        VariantRow("PEN-2", "free", "EUR", "5"),
        VariantRow("PEN-3", "2.00", "EUR", "-1")
      ),
      tags = Map.empty
    )

    val errors: List[(String, String)] = importRow(row).asErrorPathMessageStrings.toList

    assertEquals(
      List(
        "variants(1).variants => <computed for _.variants.everyItem.price>" -> "empty value",
        "variants(2).variants => <computed for _.variants.everyItem.stock>" -> "invalid stock '-1'"
      ),
      errors
    )
  }
}
