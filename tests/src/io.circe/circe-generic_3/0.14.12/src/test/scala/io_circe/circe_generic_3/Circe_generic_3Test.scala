/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

final case class GeoPoint(latitude: BigDecimal, longitude: BigDecimal)

object GeoPoint {
  given Codec.AsObject[GeoPoint] = deriveCodec[GeoPoint]
}

final case class PostalAddress(street: String, city: String, location: GeoPoint, tags: List[String])

object PostalAddress {
  given Codec.AsObject[PostalAddress] = deriveCodec[PostalAddress]
}

final case class CustomerProfile(
    id: String,
    displayName: String,
    address: PostalAddress,
    loyaltyPoints: Option[Int],
    preferences: Map[String, Boolean]
)

object CustomerProfile {
  given Encoder.AsObject[CustomerProfile] = deriveEncoder[CustomerProfile]
  given Decoder[CustomerProfile] = deriveDecoder[CustomerProfile]
}

sealed trait DeliveryEvent

object DeliveryEvent {
  final case class Accepted(orderId: String, warehouse: String, priority: Int) extends DeliveryEvent
  final case class Rerouted(orderId: String, reason: String, replacementWarehouse: Option[String]) extends DeliveryEvent
  case object Cancelled extends DeliveryEvent

  given Encoder[DeliveryEvent] = deriveEncoder[DeliveryEvent]
  given Decoder[DeliveryEvent] = deriveDecoder[DeliveryEvent]
}

final case class TreeNode(label: String, children: List[TreeNode])

object TreeNode {
  given Codec.AsObject[TreeNode] = deriveCodec[TreeNode]
}

final case class AutoLineItem(sku: String, quantity: Int)

final case class AutoOrder(number: String, lines: Vector[AutoLineItem], coupon: Option[String])

class Circe_generic_3Test {
  @Test
  def derivesSemiautomaticCodecsForNestedProductsAndCollections(): Unit = {
    val profile: CustomerProfile = CustomerProfile(
      id = "customer-1",
      displayName = "Ada Lovelace",
      address = PostalAddress(
        street = "St James's Square",
        city = "London",
        location = GeoPoint(BigDecimal("51.5072"), BigDecimal("-0.1276")),
        tags = List("billing", "historic")
      ),
      loyaltyPoints = Some(42),
      preferences = Map("email" -> true, "sms" -> false)
    )

    val json: Json = profile.asJson

    assertThat(json.hcursor.get[String]("displayName")).isEqualTo(Right("Ada Lovelace"))
    assertThat(json.hcursor.downField("address").get[String]("city")).isEqualTo(Right("London"))
    assertThat(json.hcursor.downField("address").downField("location").get[BigDecimal]("latitude"))
      .isEqualTo(Right(BigDecimal("51.5072")))
    assertThat(json.hcursor.downField("preferences").get[Boolean]("email")).isEqualTo(Right(true))
    assertThat(json.as[CustomerProfile]).isEqualTo(Right(profile))
  }

  @Test
  def derivedProductDecodersHandleMissingOptionalFieldsAndIgnoreUnknownFields(): Unit = {
    val json: Json = Json.obj(
      "id" -> Json.fromString("customer-2"),
      "displayName" -> Json.fromString("Grace Hopper"),
      "address" -> Json.obj(
        "street" -> Json.fromString("Arlington Boulevard"),
        "city" -> Json.fromString("Arlington"),
        "location" -> Json.obj(
          "latitude" -> Json.fromBigDecimal(BigDecimal("38.8800")),
          "longitude" -> Json.fromBigDecimal(BigDecimal("-77.1067"))
        ),
        "tags" -> Json.arr(Json.fromString("shipping")),
        "ignoredNested" -> Json.fromString("kept out of the case class")
      ),
      "preferences" -> Json.obj("email" -> Json.False),
      "ignoredTopLevel" -> Json.fromInt(1)
    )

    assertThat(json.as[CustomerProfile]).isEqualTo(
      Right(
        CustomerProfile(
          id = "customer-2",
          displayName = "Grace Hopper",
          address = PostalAddress(
            street = "Arlington Boulevard",
            city = "Arlington",
            location = GeoPoint(BigDecimal("38.8800"), BigDecimal("-77.1067")),
            tags = List("shipping")
          ),
          loyaltyPoints = None,
          preferences = Map("email" -> false)
        )
      )
    )
  }

  @Test
  def reportsFieldSpecificFailuresFromDerivedProductDecoders(): Unit = {
    val invalid: Json = Json.obj(
      "id" -> Json.fromInt(7),
      "displayName" -> Json.fromString("Alan Turing"),
      "address" -> Json.obj(
        "street" -> Json.fromString("Bletchley Park"),
        "city" -> Json.fromString("Milton Keynes"),
        "location" -> Json.obj(
          "latitude" -> Json.fromString("north"),
          "longitude" -> Json.fromBigDecimal(BigDecimal("-0.739"))
        ),
        "tags" -> Json.fromString("not-a-list")
      ),
      "loyaltyPoints" -> Json.fromString("many"),
      "preferences" -> Json.obj("email" -> Json.fromString("yes"))
    )

    val failures: List[DecodingFailure] = invalid.asAccumulating[CustomerProfile].fold(
      _.toList,
      value => fail(s"Expected decoding to fail, but decoded: $value")
    )

    assertThat(failures.size).isGreaterThanOrEqualTo(4)
    assertThat(failures.flatMap(_.pathToRootString).asJava)
      .contains(".id", ".address.location.latitude", ".address.tags", ".loyaltyPoints", ".preferences.email")
  }

  @Test
  def derivesCodecsForSealedTraitAlternativesAndSingletonObjects(): Unit = {
    val accepted: DeliveryEvent = DeliveryEvent.Accepted("order-1", "warehouse-a", 2)
    val rerouted: DeliveryEvent = DeliveryEvent.Rerouted("order-2", "weather", Some("warehouse-b"))
    val cancelled: DeliveryEvent = DeliveryEvent.Cancelled

    val acceptedJson: Json = accepted.asJson
    val reroutedJson: Json = rerouted.asJson
    val cancelledJson: Json = cancelled.asJson

    assertThat(acceptedJson.hcursor.downField("Accepted").get[String]("warehouse")).isEqualTo(Right("warehouse-a"))
    assertThat(reroutedJson.hcursor.downField("Rerouted").get[Option[String]]("replacementWarehouse"))
      .isEqualTo(Right(Some("warehouse-b")))
    assertThat(cancelledJson.isNull).isFalse
    assertThat(acceptedJson.as[DeliveryEvent]).isEqualTo(Right(accepted))
    assertThat(reroutedJson.as[DeliveryEvent]).isEqualTo(Right(rerouted))
    assertThat(cancelledJson.as[DeliveryEvent]).isEqualTo(Right(cancelled))
  }

  @Test
  def derivedSealedTraitDecodersRejectUnknownConstructors(): Unit = {
    val invalid: Json = Json.obj(
      "Returned" -> Json.obj(
        "orderId" -> Json.fromString("order-3"),
        "reason" -> Json.fromString("damaged")
      )
    )

    val error: DecodingFailure = invalid.as[DeliveryEvent] match {
      case Left(error) => error
      case Right(value) => fail(s"Expected unknown constructor to fail, but decoded: $value")
    }

    assertThat(error.message).isNotBlank
  }

  @Test
  def derivesRecursiveProductCodecs(): Unit = {
    val tree: TreeNode = TreeNode(
      label = "root",
      children = List(
        TreeNode("left", Nil),
        TreeNode("right", List(TreeNode("right.leaf", Nil)))
      )
    )

    val json: Json = tree.asJson

    assertThat(json.hcursor.get[String]("label")).isEqualTo(Right("root"))
    assertThat(json.hcursor.downField("children").downN(1).downField("children").downN(0).get[String]("label"))
      .isEqualTo(Right("right.leaf"))
    assertThat(json.as[TreeNode]).isEqualTo(Right(tree))
  }

  @Test
  def derivesCodecsAutomaticallyAtUseSite(): Unit = {
    import io.circe.generic.auto._

    val order: AutoOrder = AutoOrder(
      number = "order-4",
      lines = Vector(AutoLineItem("keyboard", 1), AutoLineItem("mouse", 2)),
      coupon = None
    )

    val json: Json = order.asJson

    assertThat(json.hcursor.get[String]("number")).isEqualTo(Right("order-4"))
    assertThat(json.hcursor.downField("lines").downN(0).get[String]("sku")).isEqualTo(Right("keyboard"))
    assertThat(json.hcursor.downField("lines").downN(1).get[Int]("quantity")).isEqualTo(Right(2))
    assertThat(json.as[AutoOrder]).isEqualTo(Right(order))
  }
}
