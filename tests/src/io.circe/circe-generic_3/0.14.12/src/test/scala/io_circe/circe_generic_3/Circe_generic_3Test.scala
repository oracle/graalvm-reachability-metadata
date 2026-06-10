/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

object CirceGenericFixtures {
  final case class Address(street: String, city: String, postalCode: String)

  object Address {
    given Codec.AsObject[Address] = deriveCodec[Address]
  }

  final case class UserProfile(
      id: Long,
      name: String,
      email: Option[String],
      active: Boolean,
      address: Address,
      tags: List[String],
      preferences: Map[String, Int]
  )

  object UserProfile {
    given Encoder.AsObject[UserProfile] = deriveEncoder[UserProfile]
    given Decoder[UserProfile] = deriveDecoder[UserProfile]
  }

  sealed trait DeliveryStatus

  object DeliveryStatus {
    final case class Scheduled(slot: String, priority: Int) extends DeliveryStatus
    final case class Delivered(receivedBy: String) extends DeliveryStatus
    case object Cancelled extends DeliveryStatus

    given Encoder.AsObject[DeliveryStatus] = deriveEncoder[DeliveryStatus]
    given Decoder[DeliveryStatus] = deriveDecoder[DeliveryStatus]
  }

  final case class Shipment(id: String, status: DeliveryStatus, history: List[DeliveryStatus])

  object Shipment {
    given Codec.AsObject[Shipment] = deriveCodec[Shipment]
  }

  final case class Dimensions(width: BigDecimal, height: BigDecimal, depth: BigDecimal)

  final case class InventoryItem(
      sku: String,
      quantity: Int,
      dimensions: Dimensions,
      attributes: Map[String, String]
  )

  object InventoryItem {
    given Codec.AsObject[Dimensions] = deriveCodec[Dimensions]
    given Codec.AsObject[InventoryItem] = deriveCodec[InventoryItem]
  }

  final case class AutoPermission(resource: String, scopes: Set[String])
  final case class AutoMember(userName: String, permissions: Vector[AutoPermission])
  final case class AutoTeam(name: String, members: List[AutoMember])
}

class Circe_generic_3Test {
  import CirceGenericFixtures._

  @Test
  def semiautomaticallyDerivesEncodersAndDecodersForNestedProducts(): Unit = {
    val profile: UserProfile = UserProfile(
      id = 42L,
      name = "Ada Lovelace",
      email = Some("ada@example.test"),
      active = true,
      address = Address("Example Street 1", "London", "N1"),
      tags = List("admin", "analytics"),
      preferences = Map("theme" -> 2, "pageSize" -> 50)
    )

    val json: Json = profile.asJson

    assertThat(json.hcursor.get[String]("name")).isEqualTo(Right("Ada Lovelace"))
    assertThat(json.hcursor.downField("address").get[String]("city")).isEqualTo(Right("London"))
    assertThat(json.hcursor.downField("tags").values.map(_.size)).isEqualTo(Some(2))
    assertThat(json.as[UserProfile]).isEqualTo(Right(profile))
  }

  @Test
  def semiautomaticDecodersReportInvalidProductFields(): Unit = {
    val invalidJson: Json = Json.obj(
      "id" -> Json.fromString("not-a-long"),
      "name" -> Json.fromString("Grace Hopper"),
      "email" -> Json.Null,
      "active" -> Json.True,
      "address" -> Json.obj(
        "street" -> Json.fromString("Compiler Way"),
        "city" -> Json.fromString("Arlington"),
        "postalCode" -> Json.fromString("22201")
      ),
      "tags" -> Json.arr(Json.fromString("compiler")),
      "preferences" -> Json.obj("theme" -> Json.fromInt(1))
    )

    val decoded: Decoder.Result[UserProfile] = invalidJson.as[UserProfile]

    assertThat(decoded.isLeft).isTrue
    assertThat(decoded.fold(_.history.map(_.toString).asJava, _ => List.empty[String].asJava))
      .contains("DownField(id)")
  }

  @Test
  def derivesCodecsForSealedTraitHierarchiesAndNestedCollections(): Unit = {
    import DeliveryStatus._

    val shipment: Shipment = Shipment(
      id = "shipment-1",
      status = Scheduled("2026-06-10T10:00:00Z", priority = 3),
      history = List(Delivered("receiving-desk"), Cancelled)
    )

    val json: Json = shipment.asJson
    val statusKeys: Set[String] = json.hcursor.downField("status").keys.map(_.toSet).getOrElse(Set.empty)
    val historyEntries: Int = json.hcursor.downField("history").values.map(_.size).getOrElse(0)

    assertThat(statusKeys.asJava).containsExactly("Scheduled")
    assertThat(historyEntries).isEqualTo(2)
    assertThat(json.as[Shipment]).isEqualTo(Right(shipment))
  }

  @Test
  def derivedAdtDecoderRejectsUnknownConstructors(): Unit = {
    val json: Json = Json.obj(
      "id" -> Json.fromString("shipment-2"),
      "status" -> Json.obj("Unknown" -> Json.obj("reason" -> Json.fromString("missing"))),
      "history" -> Json.arr()
    )

    val decoded: Decoder.Result[Shipment] = json.as[Shipment]

    assertThat(decoded.isLeft).isTrue
    assertThat(decoded.fold(_.message, _ => "")).isNotBlank
  }

  @Test
  def derivedCodecHandlesNumbersAndMapsWithoutCustomInstances(): Unit = {
    import InventoryItem.given

    val item: InventoryItem = InventoryItem(
      sku = "part-7",
      quantity = 12,
      dimensions = Dimensions(BigDecimal("1.25"), BigDecimal("2.50"), BigDecimal("3.75")),
      attributes = Map("material" -> "steel", "finish" -> "brushed")
    )

    val json: Json = item.asJson

    assertThat(json.hcursor.downField("dimensions").get[BigDecimal]("height")).isEqualTo(Right(BigDecimal("2.50")))
    assertThat(json.hcursor.downField("attributes").get[String]("material")).isEqualTo(Right("steel"))
    assertThat(json.as[InventoryItem]).isEqualTo(Right(item))
  }

  @Test
  def automaticDerivationProvidesCodecsFromPublicAutoImport(): Unit = {
    import io.circe.generic.auto._

    val team: AutoTeam = AutoTeam(
      name = "native-image",
      members = List(
        AutoMember("trinity", Vector(AutoPermission("metadata", Set("read", "write")))),
        AutoMember("morpheus", Vector(AutoPermission("reports", Set("read"))))
      )
    )

    val json: Json = team.asJson

    assertThat(json.hcursor.downField("members").downN(0).get[String]("userName"))
      .isEqualTo(Right("trinity"))
    assertThat(json.hcursor.downField("members").downN(0).downField("permissions").downN(0).get[Set[String]]("scopes"))
      .isEqualTo(Right(Set("read", "write")))
    assertThat(json.as[AutoTeam]).isEqualTo(Right(team))
  }
}
