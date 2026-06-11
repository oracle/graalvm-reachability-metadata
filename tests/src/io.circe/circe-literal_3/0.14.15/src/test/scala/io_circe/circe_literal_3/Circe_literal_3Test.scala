/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.literal.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

final case class ShipmentItem(sku: String, quantity: Int)

object ShipmentItem {
  given Encoder[ShipmentItem] = Encoder.forProduct2("sku", "quantity") { item =>
    (item.sku, item.quantity)
  }
}

final case class Shipment(id: String, carrier: String, items: List[ShipmentItem])

object Shipment {
  given Encoder[Shipment] = Encoder.forProduct3("id", "carrier", "items") { shipment =>
    (shipment.id, shipment.carrier, shipment.items)
  }
}

final case class TenantKey(value: String)

object TenantKey {
  given KeyEncoder[TenantKey] = KeyEncoder.instance(key => s"tenant-${key.value}")
}

class Circe_literal_3Test {
  @Test
  def createsNestedJsonLiteralContainingEveryJsonValueKind(): Unit = {
    val document: Json = json"""
      {
        "name": "circe literal",
        "enabled": true,
        "deleted": false,
        "nothing": null,
        "integer": 42,
        "decimal": 12.50,
        "exponent": -1.25e2,
        "escaped": "line\nsnowman \u2603 quote \"",
        "items": [1, "two", { "three": 3 }]
      }
      """

    assertThat(document.hcursor.get[String]("name")).isEqualTo(Right("circe literal"))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[Boolean]("deleted")).isEqualTo(Right(false))
    assertThat(document.hcursor.downField("nothing").focus.exists(_.isNull)).isTrue
    assertThat(document.hcursor.get[Int]("integer")).isEqualTo(Right(42))
    assertThat(document.hcursor.get[BigDecimal]("decimal")).isEqualTo(Right(BigDecimal("12.50")))
    assertThat(document.hcursor.get[BigDecimal]("exponent")).isEqualTo(Right(BigDecimal("-125")))
    assertThat(document.hcursor.get[String]("escaped")).isEqualTo(Right("line\nsnowman ☃ quote \""))
    assertThat(document.hcursor.downField("items").downN(2).get[Int]("three")).isEqualTo(Right(3))
  }

  @Test
  def createsTopLevelScalarLiterals(): Unit = {
    val nullValue: Json = json"null"
    val trueValue: Json = json"true"
    val falseValue: Json = json"false"
    val stringValue: Json = json""""standalone""""
    val numberValue: Json = json"12345678901234567890.125"

    assertThat(nullValue.isNull).isTrue
    assertThat(trueValue.asBoolean).isEqualTo(Some(true))
    assertThat(falseValue.asBoolean).isEqualTo(Some(false))
    assertThat(stringValue.asString).isEqualTo(Some("standalone"))
    assertThat(numberValue.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("12345678901234567890.125")))
  }

  @Test
  def preservesLiteralFieldOrderWhenPrintingCompactJson(): Unit = {
    val document: Json = json"""{
      "first": 1,
      "second": { "nested": true },
      "third": ["a", "b"],
      "fourth": null
    }"""

    assertThat(document.noSpaces).isEqualTo(
      """{"first":1,"second":{"nested":true},"third":["a","b"],"fourth":null}"""
    )
  }

  @Test
  def interpolatesPrimitiveCollectionAndOptionalValuesWithCirceEncoders(): Unit = {
    val name: String = "Ada"
    val count: Int = 3
    val enabled: Boolean = true
    val tags: List[String] = List("math", "compiler")
    val present: Option[Int] = Some(7)
    val absent: Option[String] = None

    val document: Json = json"""{
      "name": $name,
      "count": $count,
      "enabled": $enabled,
      "tags": $tags,
      "present": $present,
      "absent": $absent
    }"""

    assertThat(document.hcursor.get[String]("name")).isEqualTo(Right("Ada"))
    assertThat(document.hcursor.get[Int]("count")).isEqualTo(Right(3))
    assertThat(document.hcursor.get[Boolean]("enabled")).isEqualTo(Right(true))
    assertThat(document.hcursor.get[List[String]]("tags")).isEqualTo(Right(List("math", "compiler")))
    assertThat(document.hcursor.get[Option[Int]]("present")).isEqualTo(Right(Some(7)))
    assertThat(document.hcursor.get[Option[String]]("absent")).isEqualTo(Right(None))
  }

  @Test
  def interpolatesJsonFragmentsInsideObjectsAndArrays(): Unit = {
    val firstItem: Json = json"""{ "id": 1, "status": "ready" }"""
    val secondItem: Json = json"""{ "id": 2, "status": "queued" }"""
    val metadata: Json = json"""{ "source": "literal", "retries": 0 }"""

    val document: Json = json"""{
      "items": [$firstItem, $secondItem],
      "metadata": $metadata
    }"""

    assertThat(document.hcursor.downField("items").downArray.get[Int]("id")).isEqualTo(Right(1))
    assertThat(document.hcursor.downField("items").downN(1).get[String]("status")).isEqualTo(Right("queued"))
    assertThat(document.hcursor.downField("metadata").get[String]("source")).isEqualTo(Right("literal"))
    assertThat(document.noSpacesSortKeys).isEqualTo(
      """{"items":[{"id":1,"status":"ready"},{"id":2,"status":"queued"}],"metadata":{"retries":0,"source":"literal"}}"""
    )
  }

  @Test
  def interpolatesObjectKeysWithStringAndCustomKeyEncoders(): Unit = {
    val plainKey: String = "region"
    val tenantKey: TenantKey = TenantKey("alpha")
    val code: String = "eu-central"
    val quota: Int = 12

    val document: Json = json"""{
      $plainKey: $code,
      $tenantKey: { "quota": $quota }
    }"""

    assertThat(document.hcursor.get[String]("region")).isEqualTo(Right("eu-central"))
    assertThat(document.hcursor.downField("tenant-alpha").get[Int]("quota")).isEqualTo(Right(12))
    assertThat(document.noSpaces).isEqualTo("""{"region":"eu-central","tenant-alpha":{"quota":12}}""")
  }

  @Test
  def escapesInterpolatedStringsAndKeysContainingJsonSpecialCharacters(): Unit = {
    val dynamicKey: String = "line\nbreak quote\" slash\\ snowman☃"
    val dynamicValue: String = "tab\treturn\rquote\" slash\\ snowman☃"

    val document: Json = json"""{
      $dynamicKey: $dynamicValue
    }"""

    assertThat(document.hcursor.get[String](dynamicKey)).isEqualTo(Right(dynamicValue))
    assertThat(document.noSpaces).isEqualTo(
      """{"line\nbreak quote\" slash\\ snowman☃":"tab\treturn\rquote\" slash\\ snowman☃"}"""
    )
  }

  @Test
  def interpolatesDomainObjectsThroughUserProvidedEncoders(): Unit = {
    val shipment: Shipment = Shipment(
      id = "shipment-1",
      carrier = "bicycle",
      items = List(ShipmentItem("book", 2), ShipmentItem("pen", 5))
    )

    val document: Json = json"""{
      "event": "created",
      "shipment": $shipment
    }"""

    assertThat(document.hcursor.get[String]("event")).isEqualTo(Right("created"))
    assertThat(document.hcursor.downField("shipment").get[String]("id")).isEqualTo(Right("shipment-1"))
    assertThat(document.hcursor.downField("shipment").downField("items").downArray.get[String]("sku"))
      .isEqualTo(Right("book"))
    assertThat(document.hcursor.downField("shipment").downField("items").downN(1).get[Int]("quantity"))
      .isEqualTo(Right(5))
  }

  @Test
  def interpolatesTopLevelValuesThroughCirceEncoders(): Unit = {
    val shipment: Shipment = Shipment("shipment-2", "rail", List(ShipmentItem("paper", 10)))
    val numbers: List[Int] = List(1, 1, 2, 3, 5)
    val missing: Option[String] = None

    val shipmentJson: Json = json"$shipment"
    val numbersJson: Json = json"$numbers"
    val missingJson: Json = json"$missing"

    assertThat(shipmentJson.hcursor.get[String]("carrier")).isEqualTo(Right("rail"))
    assertThat(numbersJson.as[List[Int]]).isEqualTo(Right(numbers))
    assertThat(missingJson.isNull).isTrue
  }

  @Test
  def interpolatesInlineExpressionsInValueAndKeyPositions(): Unit = {
    val document: Json = json"""{
      "sum": ${1 + 2 + 3},
      ${"dynamic" + "Key"}: ${List("a", "b").mkString(":")},
      "nested": { ${TenantKey("beta")}: ${Option("enabled")} }
    }"""

    assertThat(document.hcursor.get[Int]("sum")).isEqualTo(Right(6))
    assertThat(document.hcursor.get[String]("dynamicKey")).isEqualTo(Right("a:b"))
    assertThat(document.hcursor.downField("nested").get[Option[String]]("tenant-beta")).isEqualTo(Right(Some("enabled")))
  }
}
