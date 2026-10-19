/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Json, ParsingFailure}
import io.circe.optics.JsonPath.root
import io.circe.parser.parse
import java.util.Locale
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises JSON optics traversals over parsed supplier catalogs to normalize nested product tags. */
@Timeout(60)
class SupplierCatalogNormalizationTest {
  private def normalizeCatalog(payload: String): Either[ParsingFailure, Json] =
    parse(payload).map { catalog =>
      root.departments.each.products.each.tags.each.string.modify { tag =>
        tag.trim.toLowerCase(Locale.ROOT)
      }(catalog)
    }

  @Test
  def normalizesTagsAcrossDepartmentsWithoutRequiringAClosedProductSchema(): Unit = {
    val supplierPayload: String =
      """{
        |  "supplier": "north-coast",
        |  "departments": [
        |    {
        |      "name": "Stationery",
        |      "products": [
        |        {"sku": "pen-7", "tags": [" BLUE ", "Office"], "price": 1.25},
        |        {"sku": "pad-2", "tags": [], "recycled": true},
        |        {"sku": "clip-9", "packSize": 100}
        |      ]
        |    },
        |    {
        |      "name": "Kitchen",
        |      "products": [
        |        {"sku": "mug-4", "tags": [" Gift", "CERAMIC "], "color": "BLUE"}
        |      ]
        |    },
        |    {"name": "Seasonal", "products": []}
        |  ],
        |  "extension": {"tags": ["DO NOT CHANGE"], "revision": 3}
        |}""".stripMargin

    val normalized: Json = normalizeCatalog(supplierPayload).fold(error => throw error, identity)
    val storedCatalog: String = normalized.noSpaces
    val restored: Json = parse(storedCatalog).fold(error => throw error, identity)

    assertEquals(
      List("blue", "office", "gift", "ceramic"),
      root.departments.each.products.each.tags.each.string.getAll(restored)
    )
    assertEquals(
      List("pen-7", "pad-2", "clip-9", "mug-4"),
      root.departments.each.products.each.sku.string.getAll(restored)
    )

    val expectedPayload: String =
      """{
        |  "supplier": "north-coast",
        |  "departments": [
        |    {
        |      "name": "Stationery",
        |      "products": [
        |        {"sku": "pen-7", "tags": ["blue", "office"], "price": 1.25},
        |        {"sku": "pad-2", "tags": [], "recycled": true},
        |        {"sku": "clip-9", "packSize": 100}
        |      ]
        |    },
        |    {
        |      "name": "Kitchen",
        |      "products": [
        |        {"sku": "mug-4", "tags": ["gift", "ceramic"], "color": "BLUE"}
        |      ]
        |    },
        |    {"name": "Seasonal", "products": []}
        |  ],
        |  "extension": {"tags": ["DO NOT CHANGE"], "revision": 3}
        |}""".stripMargin

    assertEquals(parse(expectedPayload), Right(restored))
  }

  @Test
  def rejectsATruncatedSupplierFeedBeforePublishingACatalog(): Unit = {
    val truncatedPayload: String =
      """{"departments":[{"products":[{"sku":"pen-7","tags":[" BLUE "]}]}]"""

    assertTrue(normalizeCatalog(truncatedPayload).isLeft)
  }
}
