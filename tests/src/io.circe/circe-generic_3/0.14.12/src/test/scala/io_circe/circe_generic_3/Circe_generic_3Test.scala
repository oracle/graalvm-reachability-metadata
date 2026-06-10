/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import cats.data.Validated
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

final case class PostalAddress(street: String, city: String, postalCode: String)

object PostalAddress {
  given Codec.AsObject[PostalAddress] = deriveCodec[PostalAddress]
}

final case class CustomerProfile(
    id: Long,
    name: String,
    active: Boolean,
    address: PostalAddress,
    tags: List[String],
    balances: Map[String, BigDecimal],
    nickname: Option[String]
)

object CustomerProfile {
  given Encoder.AsObject[CustomerProfile] = deriveEncoder[CustomerProfile]
  given Decoder[CustomerProfile] = deriveDecoder[CustomerProfile]
}

final case class CatalogNode(name: String, children: List[CatalogNode])

object CatalogNode {
  given Codec.AsObject[CatalogNode] = deriveCodec[CatalogNode]
}

enum JobState {
  case Queued, Running, Completed
}

object JobState {
  given Encoder[JobState] = Encoder.encodeString.contramap(_.toString)

  given Decoder[JobState] = Decoder.decodeString.emap {
    case "Queued" => Right(JobState.Queued)
    case "Running" => Right(JobState.Running)
    case "Completed" => Right(JobState.Completed)
    case other => Left(s"Unknown job state: $other")
  }
}

final case class ProcessingJob(id: String, state: JobState, attempts: Int)

object ProcessingJob {
  given Codec.AsObject[ProcessingJob] = deriveCodec[ProcessingJob]
}

sealed trait AuditEvent

object AuditEvent {
  final case class Created(id: Long, profile: CustomerProfile) extends AuditEvent
  final case class Renamed(id: Long, previousName: String, currentName: String) extends AuditEvent
  final case class Removed(id: Long, permanent: Boolean) extends AuditEvent

  given Encoder[AuditEvent] = deriveEncoder[AuditEvent]
  given Decoder[AuditEvent] = deriveDecoder[AuditEvent]
}

final case class AutoCoordinates(latitude: BigDecimal, longitude: BigDecimal)
final case class AutoWarehouse(name: String, coordinates: AutoCoordinates, stockedSkus: Vector[String])

final class Circe_generic_3Test {
  @Test
  def derivesProductEncodersAndDecodersForNestedCaseClasses(): Unit = {
    val profile: CustomerProfile = sampleProfile

    val json: Json = profile.asJson

    assertThat(json.hcursor.get[Long]("id")).isEqualTo(Right(101L))
    assertThat(json.hcursor.downField("address").get[String]("city")).isEqualTo(Right("Prague"))
    assertThat(json.hcursor.get[List[String]]("tags")).isEqualTo(Right(List("premium", "newsletter")))
    assertThat(json.hcursor.get[Map[String, BigDecimal]]("balances"))
      .isEqualTo(Right(Map("credits" -> BigDecimal("12.50"), "debits" -> BigDecimal("3"))))
    assertThat(json.hcursor.get[Option[String]]("nickname")).isEqualTo(Right(Some("ada")))
    assertThat(json.as[CustomerProfile]).isEqualTo(Right(profile))
  }

  @Test
  def decodesMissingOptionalFieldsAndExplicitNulls(): Unit = {
    val withoutNickname: Json = Json.obj(
      "id" -> Json.fromLong(202L),
      "name" -> Json.fromString("Grace Hopper"),
      "active" -> Json.False,
      "address" -> Json.obj(
        "street" -> Json.fromString("Compiler Ave"),
        "city" -> Json.fromString("Arlington"),
        "postalCode" -> Json.fromString("22201")
      ),
      "tags" -> Json.arr(Json.fromString("compiler")),
      "balances" -> Json.obj("credits" -> Json.fromBigDecimal(BigDecimal("99.95")))
    )
    val withNullNickname: Json = withoutNickname.deepMerge(Json.obj("nickname" -> Json.Null))

    assertThat(withoutNickname.as[CustomerProfile].map(_.nickname)).isEqualTo(Right(None))
    assertThat(withNullNickname.as[CustomerProfile].map(_.nickname)).isEqualTo(Right(None))
  }

  @Test
  def accumulatesProductAndNestedFieldDecodingFailures(): Unit = {
    val invalid: Json = Json.obj(
      "id" -> Json.fromString("not-a-long"),
      "name" -> Json.fromInt(42),
      "active" -> Json.fromString("yes"),
      "address" -> Json.obj(
        "street" -> Json.True,
        "city" -> Json.fromString("Prague"),
        "postalCode" -> Json.fromInt(12345)
      ),
      "tags" -> Json.arr(Json.fromString("ok"), Json.fromInt(10)),
      "balances" -> Json.obj("credits" -> Json.fromString("not-a-decimal")),
      "nickname" -> Json.fromBoolean(true)
    )

    val failures: List[DecodingFailure] = invalid.asAccumulating[CustomerProfile] match {
      case Validated.Invalid(errors) => errors.toList
      case Validated.Valid(profile) => fail(s"Expected decoding to fail, but decoded: $profile")
    }

    assertThat(failures.size).isGreaterThanOrEqualTo(6)
    assertThat(failures.map(_.history.nonEmpty).asJava).contains(true)
  }

  @Test
  def derivesRecursiveCodecsForTreeLikeProducts(): Unit = {
    val catalog: CatalogNode = CatalogNode(
      "root",
      List(
        CatalogNode("books", List(CatalogNode("scala", Nil), CatalogNode("graalvm", Nil))),
        CatalogNode("hardware", Nil)
      )
    )

    val json: Json = catalog.asJson

    assertThat(json.hcursor.downField("children").downArray.get[String]("name")).isEqualTo(Right("books"))
    assertThat(json.hcursor.downField("children").downArray.downField("children").downN(1).get[String]("name"))
      .isEqualTo(Right("graalvm"))
    assertThat(json.as[CatalogNode]).isEqualTo(Right(catalog))
  }

  @Test
  def derivesSealedTraitCodecsForCoproducts(): Unit = {
    val events: List[AuditEvent] = List(
      AuditEvent.Created(1L, sampleProfile),
      AuditEvent.Renamed(1L, "Ada", "Ada Lovelace"),
      AuditEvent.Removed(2L, permanent = false)
    )

    val json: Json = events.asJson

    assertThat(json.asArray.map(_.size)).isEqualTo(Some(3))
    assertThat(json.as[List[AuditEvent]]).isEqualTo(Right(events))
  }

  @Test
  def derivedProductsUseCustomFieldCodecsInScope(): Unit = {
    val runningJob: ProcessingJob = ProcessingJob("job-7", JobState.Running, 2)
    val completedJson: Json = Json.obj(
      "id" -> Json.fromString("job-8"),
      "state" -> Json.fromString("Completed"),
      "attempts" -> Json.fromInt(3)
    )

    val encoded: Json = runningJob.asJson

    assertThat(encoded.hcursor.get[String]("state")).isEqualTo(Right("Running"))
    assertThat(encoded.as[ProcessingJob]).isEqualTo(Right(runningJob))
    assertThat(completedJson.as[ProcessingJob]).isEqualTo(Right(ProcessingJob("job-8", JobState.Completed, 3)))

    val invalid: Either[DecodingFailure, ProcessingJob] = completedJson
      .deepMerge(Json.obj("state" -> Json.fromString("Paused")))
      .as[ProcessingJob]
    assertThat(invalid.isLeft).isTrue
  }

  @Test
  def supportsAutomaticDerivationFromGenericAutoImport(): Unit = {
    import io.circe.generic.auto._

    val warehouse: AutoWarehouse = AutoWarehouse(
      "central",
      AutoCoordinates(BigDecimal("50.0755"), BigDecimal("14.4378")),
      Vector("sku-1", "sku-2", "sku-3")
    )

    val json: Json = warehouse.asJson

    assertThat(json.hcursor.downField("coordinates").get[BigDecimal]("latitude"))
      .isEqualTo(Right(BigDecimal("50.0755")))
    assertThat(json.hcursor.get[Vector[String]]("stockedSkus"))
      .isEqualTo(Right(Vector("sku-1", "sku-2", "sku-3")))
    assertThat(json.as[AutoWarehouse]).isEqualTo(Right(warehouse))
  }

  @Test
  def derivedDecodersRejectUnknownSealedTraitConstructors(): Unit = {
    val unknownEvent: Json = Json.obj(
      "Archived" -> Json.obj(
        "id" -> Json.fromLong(5L),
        "reason" -> Json.fromString("retention-policy")
      )
    )

    val decoded: Either[DecodingFailure, AuditEvent] = unknownEvent.as[AuditEvent]

    decoded match {
      case Left(failure) => assertThat(failure.message).isNotBlank
      case Right(event) => fail(s"Expected unknown event constructor to fail, but decoded: $event")
    }
  }

  private def sampleProfile: CustomerProfile = {
    CustomerProfile(
      id = 101L,
      name = "Ada Lovelace",
      active = true,
      address = PostalAddress("Analytical Engine Road", "Prague", "11000"),
      tags = List("premium", "newsletter"),
      balances = Map("credits" -> BigDecimal("12.50"), "debits" -> BigDecimal("3")),
      nickname = Some("ada")
    )
  }
}
