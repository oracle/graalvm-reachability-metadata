/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.core.TraceVisitor.TraceException
import upickle.default.*
import upickle.implicits.flatten
import upickle.implicits.key

/**
 * Flattened JSON layouts through the upickle.implicits.flatten annotation on nested case-class
 * fields and on a catch-all Map field, as a marketplace keeps structured listing models while
 * exchanging the flat product records its sellers upload.
 */
class MarketplaceListingFlattenTest {

  import MarketplaceListingFlattenTest.*

  @Test
  def nestedModelsAreWrittenAsOneFlatRecord(): Unit = {
    val listing: Listing = Listing(
      sku = "CHAIR-OAK-01",
      title = "Oak dining chair",
      pricing = Pricing("EUR", BigDecimal("149.00"), Tax("DE", 19)),
      dimensions = Dimensions(450, 920, 510),
      attributes = Map("color" -> "natural", "material" -> "oak")
    )

    val json: String = write(listing)
    val record: ujson.Value = ujson.read(json)

    assertEquals(
      Set(
        "sku",
        "title",
        "currency",
        "list_price",
        "tax_region",
        "vatPercent",
        "widthMm",
        "heightMm",
        "depthMm",
        "color",
        "material"
      ),
      record.obj.keySet.toSet,
      json
    )
    assertEquals("EUR", record("currency").str)
    assertEquals("DE", record("tax_region").str)
    assertEquals(920, record("heightMm").num.toInt)
    assertEquals("oak", record("material").str)
    assertTrue(!json.contains("discountPercent"), json)
    assertEquals(listing, read[Listing](json))
  }

  @Test
  def sellerUploadIsReadIntoStructuredModelsWithExtrasCollected(): Unit = {
    val upload: String =
      """[
        |  {
        |    "material": "steel",
        |    "depthMm": 600,
        |    "list_price": "89.50",
        |    "title": "Steel shelf",
        |    "warranty": "2y",
        |    "vatPercent": 20,
        |    "sku": "SHELF-ST-04",
        |    "widthMm": 800,
        |    "currency": "GBP",
        |    "discountPercent": 15,
        |    "heightMm": 1800,
        |    "tax_region": "UK"
        |  },
        |  {
        |    "sku": "LAMP-BR-02",
        |    "title": "Brass desk lamp",
        |    "currency": "EUR",
        |    "list_price": "39.90",
        |    "tax_region": "FR",
        |    "vatPercent": 20,
        |    "widthMm": 150,
        |    "heightMm": 420,
        |    "depthMm": 150
        |  }
        |]""".stripMargin

    val listings: List[Listing] = read[List[Listing]](upload)

    assertEquals(
      Listing(
        sku = "SHELF-ST-04",
        title = "Steel shelf",
        pricing = Pricing("GBP", BigDecimal("89.50"), Tax("UK", 20), discountPercent = 15),
        dimensions = Dimensions(800, 1800, 600),
        attributes = Map("material" -> "steel", "warranty" -> "2y")
      ),
      listings.head
    )
    assertEquals(Map.empty[String, String], listings(1).attributes)
    assertEquals(0, listings(1).pricing.discountPercent)
    assertEquals(BigDecimal("76.075"), listings.head.pricing.salePrice)
    assertEquals(Some(Dimensions(150, 420, 150)), listings.find(_.sku == "LAMP-BR-02").map(_.dimensions))
  }

  @Test
  def flattenedCatalogRoundTripsThroughTheJsonTree(): Unit = {
    val catalog: Catalog = Catalog(
      seller = "nordic-home",
      listings = Vector(
        Listing(
          sku = "RUG-WL-09",
          title = "Wool rug",
          pricing = Pricing("SEK", BigDecimal("1299"), Tax("SE", 25), discountPercent = 10),
          dimensions = Dimensions(1600, 5, 2300),
          attributes = Map("pattern" -> "striped")
        )
      )
    )

    val tree: ujson.Value = writeJs(catalog)

    assertEquals("striped", tree("listings")(0)("pattern").str)
    assertEquals(10, tree("listings")(0)("discountPercent").num.toInt)
    assertEquals("SE", tree("listings")(0)("tax_region").str)
    assertEquals(catalog, read[Catalog](tree))
    assertEquals(catalog, read[Catalog](write(catalog, indent = 2)))
  }

  @Test
  def recordMissingAFlattenedFieldIsRejected(): Unit = {
    val incomplete: String =
      """{"sku": "BOX-1", "title": "Storage box", "currency": "EUR", "list_price": "12.00",
        | "tax_region": "NL", "vatPercent": 21, "widthMm": 300, "depthMm": 300}""".stripMargin

    val failure: TraceException = assertThrows(classOf[TraceException], () => read[Listing](incomplete))

    assertTrue(failure.getCause.getMessage.contains("heightMm"), failure.getCause.getMessage)
  }
}

object MarketplaceListingFlattenTest {

  final case class Tax(@key("tax_region") region: String, vatPercent: Int) derives ReadWriter

  final case class Pricing(
      currency: String,
      @key("list_price") listPrice: BigDecimal,
      @flatten tax: Tax,
      discountPercent: Int = 0
  ) derives ReadWriter {
    def salePrice: BigDecimal = listPrice * (100 - discountPercent) / 100
  }

  final case class Dimensions(widthMm: Int, heightMm: Int, depthMm: Int) derives ReadWriter

  final case class Listing(
      sku: String,
      title: String,
      @flatten pricing: Pricing,
      @flatten dimensions: Dimensions,
      @flatten attributes: Map[String, String] = Map.empty
  ) derives ReadWriter

  final case class Catalog(seller: String, listings: Vector[Listing]) derives ReadWriter
}
