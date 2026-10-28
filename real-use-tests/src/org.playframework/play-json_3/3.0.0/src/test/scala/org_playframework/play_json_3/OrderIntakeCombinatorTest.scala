/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax.*
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError
import play.api.libs.json.OWrites
import play.api.libs.json.Reads

/**
 * Hand-written functional combinator `Reads` and `Writes` built from `JsPath` with validation constraints.
 *
 * An order-intake endpoint accepts orders whose JSON layout differs from its domain model: the
 * customer sits in a nested object, the quantity must stay within stock limits, the e-mail must be
 * well formed, and an optional coupon code must match a fixed pattern. The endpoint composes its
 * readers and writers with `and`, guards fields with `Reads` constraints, fills a default priority,
 * reports every violation at once with the path and message key a client can show, and renders
 * accepted orders in its own outbound layout.
 */
class OrderIntakeCombinatorTest {
  import OrderIntakeCombinatorTest.*

  @Test
  def ordersAreValidatedAndRenderedThroughCombinators(): Unit = {
    val accepted: JsValue = Json.parse(
      """{
        |  "customer": {"name": "Ada Lovelace", "email": "ada@example.org"},
        |  "sku": "ENG-001",
        |  "quantity": 3,
        |  "coupon": "SPRING-24"
        |}""".stripMargin
    )
    val order: Order = accepted.as[Order]
    assertEquals(
      Order("Ada Lovelace", "ada@example.org", "ENG-001", 3, Some("SPRING-24"), "standard"),
      order
    )

    val rendered: JsValue = Json.toJson(order)
    assertEquals(
      Json.obj(
        "orderLine" -> Json.obj("sku" -> "ENG-001", "units" -> 3),
        "contact" -> "Ada Lovelace <ada@example.org>",
        "coupon" -> "SPRING-24",
        "priority" -> "standard"
      ),
      rendered
    )

    val express: JsValue = Json.parse(
      """{
        |  "customer": {"name": "Grace Hopper", "email": "grace@example.org"},
        |  "sku": "COBOL-74",
        |  "quantity": 100,
        |  "priority": "express"
        |}""".stripMargin
    )
    val expressOrder: Order = express.as[Order]
    assertEquals(None, expressOrder.coupon)
    assertEquals("express", expressOrder.priority)
    assertEquals(false, Json.toJson(expressOrder).as[JsObject].keys.contains("coupon"))

    val rejected: JsValue = Json.parse(
      """{
        |  "customer": {"name": "X", "email": "not-an-address"},
        |  "sku": "ENG-001",
        |  "quantity": 0,
        |  "coupon": "free stuff",
        |  "priority": "overnight"
        |}""".stripMargin
    )
    val result: JsResult[Order] = rejected.validate[Order]
    val failures: Map[JsPath, List[String]] = result match {
      case JsError(errors) => errors.map((path, messages) => path -> messages.flatMap(_.messages).toList).toMap
      case JsSuccess(value, _) => throw new AssertionError(s"Invalid order was accepted as $value")
    }
    assertEquals(
      Map(
        JsPath \ "customer" \ "name" -> List("error.minLength"),
        JsPath \ "customer" \ "email" -> List("error.email"),
        JsPath \ "quantity" -> List("error.min"),
        JsPath \ "coupon" -> List("error.pattern"),
        JsPath \ "priority" -> List("error.unknown.priority")
      ),
      failures
    )

    val oversized: JsResult[Order] = Json
      .parse("""{"customer": {"name": "Alan", "email": "alan@example.org"}, "sku": "B-1", "quantity": 101}""")
      .validate[Order]
    assertEquals(JsError(JsPath \ "quantity", JsonValidationError("error.max", 100)), oversized)
  }
}

object OrderIntakeCombinatorTest {

  /** Priorities the warehouse knows how to schedule. */
  val Priorities: Set[String] = Set("standard", "express")

  final case class Order(
      customerName: String,
      email: String,
      sku: String,
      quantity: Int,
      coupon: Option[String],
      priority: String
  )

  object Order {
    private val priorityReads: Reads[String] =
      Reads.of[String].filter(JsonValidationError("error.unknown.priority"))(Priorities.contains)

    given Reads[Order] = (
      (JsPath \ "customer" \ "name").read[String](using Reads.minLength[String](2)) and
        (JsPath \ "customer" \ "email").read[String](using Reads.email) and
        (JsPath \ "sku").read[String] and
        (JsPath \ "quantity").read[Int](using Reads.min(1).keepAnd(Reads.max(100))) and
        (JsPath \ "coupon").readNullable[String](using Reads.pattern("[A-Z]+-[0-9]{2}".r)) and
        (JsPath \ "priority").readWithDefault[String]("standard")(using priorityReads)
    )(Order.apply)

    given OWrites[Order] = (
      (JsPath \ "orderLine" \ "sku").write[String] and
        (JsPath \ "orderLine" \ "units").write[Int] and
        (JsPath \ "contact").write[String] and
        (JsPath \ "coupon").writeNullable[String] and
        (JsPath \ "priority").write[String]
    )((order: Order) =>
      (order.sku, order.quantity, s"${order.customerName} <${order.email}>", order.coupon, order.priority)
    )
  }
}
