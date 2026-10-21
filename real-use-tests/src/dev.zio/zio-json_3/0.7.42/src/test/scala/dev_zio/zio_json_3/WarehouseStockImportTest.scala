/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*

/** Exercises custom value and object-key codecs for validating warehouse stock imports. */
class WarehouseStockImportTest {
  final case class Sku(value: String)
  final case class StockCount(value: Int)
  final case class StockImport(featured: Sku, stock: Map[Sku, StockCount])

  private def parseSku(value: String): Either[String, Sku] = {
    if (value.matches("[A-Z]{3}-[0-9]{3}")) Right(Sku(value))
    else Left("SKU must use the format AAA-000")
  }

  private def parseCount(value: Int): Either[String, StockCount] = {
    if (value >= 0) Right(StockCount(value))
    else Left("Stock count must be non-negative")
  }

  given JsonCodec[Sku] = JsonCodec.string.transformOrFail(parseSku, _.value)
  given JsonFieldDecoder[Sku] = JsonFieldDecoder.string.mapOrFail(parseSku)
  given JsonFieldEncoder[Sku] = JsonFieldEncoder.string.contramap[Sku](_.value)
  given JsonCodec[StockCount] = JsonCodec.int.transformOrFail(parseCount, _.value)
  given JsonCodec[StockImport] = DeriveJsonCodec.gen[StockImport]

  @Test
  @Timeout(30)
  def validateIncomingStockAndPublishUpdatedInventory(): Unit = {
    val incoming: String = """
      {
        "featured": "TEA-001",
        "stock": {"TEA-001": 12, "MUG-002": 0}
      }
      """
    val tea: Sku = Sku("TEA-001")
    val mug: Sku = Sku("MUG-002")
    val expected: StockImport = StockImport(
      tea,
      Map(tea -> StockCount(12), mug -> StockCount(0))
    )
    val decoded: Either[String, StockImport] = incoming.fromJson[StockImport]
    assertEquals(Right(expected), decoded)
    val imported: StockImport = decoded.fold(error => fail[StockImport](error), identity)
    assertEquals(12, imported.stock(imported.featured).value)
    assertEquals(0, imported.stock(mug).value, "Zero stock is a valid sold-out item")

    val replenished: StockImport = imported.copy(
      stock = imported.stock.updated(mug, StockCount(8))
    )
    val publishedStock: String = replenished.stock.toJson
    assertEquals(
      Right(Map("TEA-001" -> 12, "MUG-002" -> 8)),
      publishedStock.fromJson[Map[String, Int]],
      "The external inventory format must use SKU keys and numeric counts, not wrapper objects"
    )
    assertEquals(Right(replenished), replenished.toJson.fromJson[StockImport])

    val invalidKey: String = """{"featured":"TEA-001","stock":{"bad-sku":4}}"""
    val invalidCount: String = """{"featured":"TEA-001","stock":{"TEA-001":-1}}"""
    val invalidFeatured: String = """{"featured":"bad-sku","stock":{"TEA-001":4}}"""
    val rejectedKey: Either[String, StockImport] = invalidKey.fromJson[StockImport]
    val rejectedCount: Either[String, StockImport] = invalidCount.fromJson[StockImport]
    val rejectedFeatured: Either[String, StockImport] = invalidFeatured.fromJson[StockImport]

    assertTrue(rejectedKey.isLeft, "Invalid object keys must not enter inventory")
    assertTrue(rejectedCount.isLeft, "Negative stock must not enter inventory")
    assertTrue(rejectedFeatured.isLeft, "SKU validation must also apply to ordinary values")
    assertTrue(rejectedKey.swap.toOption.get.contains("SKU must use the format AAA-000"))
    assertTrue(rejectedCount.swap.toOption.get.contains("Stock count must be non-negative"))
    assertTrue(rejectedFeatured.swap.toOption.get.contains("SKU must use the format AAA-000"))
  }
}
