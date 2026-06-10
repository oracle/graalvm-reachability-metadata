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
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

final case class GenericAddress(street: String, zip: Int)

object GenericAddress {
  given Codec.AsObject[GenericAddress] = deriveCodec[GenericAddress]
}

final case class GenericUser(
    name: String,
    age: Int,
    address: GenericAddress,
    nickname: Option[String],
    tags: List[String],
    scores: Map[String, BigDecimal]
)

object GenericUser {
  given Codec.AsObject[GenericUser] = deriveCodec[GenericUser]
}

final case class AuditRecord(id: Long, flags: List[Boolean], data: Map[String, BigDecimal])

object AuditRecord {
  given Encoder.AsObject[AuditRecord] = deriveEncoder[AuditRecord]
  given Decoder[AuditRecord] = deriveDecoder[AuditRecord]
}

sealed trait DomainEvent

final case class UserRegistered(user: GenericUser) extends DomainEvent

final case class UserTagged(name: String, tags: List[String]) extends DomainEvent

object DomainEvent {
  given Encoder[DomainEvent] = deriveEncoder[DomainEvent]
  given Decoder[DomainEvent] = deriveDecoder[DomainEvent]
}

final case class AutoMetric(name: String, values: Vector[Double])

final case class AutoReport(metric: AutoMetric, labels: Set[String], active: Boolean)

class Circe_generic_3Test {
  @Test
  def derivesSemiAutomaticCodecsForNestedProducts(): Unit = {
    val user: GenericUser = sampleUser

    val json: Json = user.asJson

    assertEquals(Right("Ada Lovelace"), json.hcursor.get[String]("name"))
    assertEquals(Right(36), json.hcursor.get[Int]("age"))
    assertEquals(Right("St James Square"), json.hcursor.downField("address").get[String]("street"))
    assertEquals(Right(Some("ada")), json.hcursor.get[Option[String]]("nickname"))
    assertEquals(Right(List("math", "compiler")), json.hcursor.get[List[String]]("tags"))
    assertEquals(Right(Map("analysis" -> BigDecimal("98.5"), "logic" -> BigDecimal("100"))), json.hcursor.get[Map[String, BigDecimal]]("scores"))
    assertEquals(Right(user), json.as[GenericUser])
  }

  @Test
  def decodesMissingOptionalProductFieldsAsNone(): Unit = {
    val json: Json = Json.obj(
      "name" -> Json.fromString("Grace Hopper"),
      "age" -> Json.fromInt(85),
      "address" -> Json.obj(
        "street" -> Json.fromString("Arlington"),
        "zip" -> Json.fromInt(22201)
      ),
      "tags" -> Json.arr(Json.fromString("navy"), Json.fromString("compiler")),
      "scores" -> Json.obj("cobol" -> Json.fromBigDecimal(BigDecimal("99.75")))
    )

    val expected: GenericUser = GenericUser(
      name = "Grace Hopper",
      age = 85,
      address = GenericAddress("Arlington", 22201),
      nickname = None,
      tags = List("navy", "compiler"),
      scores = Map("cobol" -> BigDecimal("99.75"))
    )

    assertEquals(Right(expected), json.as[GenericUser])
  }

  @Test
  def exposesSemiAutomaticallyDerivedObjectEncoders(): Unit = {
    val record: AuditRecord = AuditRecord(
      id = 42L,
      flags = List(true, false, true),
      data = Map("latency" -> BigDecimal("12.5"), "ratio" -> BigDecimal("0.875"))
    )

    val jsonObject: JsonObject = summon[Encoder.AsObject[AuditRecord]].encodeObject(record)

    assertTrue(jsonObject.contains("id"))
    assertTrue(jsonObject.contains("flags"))
    assertTrue(jsonObject.contains("data"))
    assertEquals(Right(record), jsonObject.asJson.as[AuditRecord])
    assertEquals("""{"data":{"latency":12.5,"ratio":0.875},"flags":[true,false,true],"id":42}""", jsonObject.asJson.noSpacesSortKeys)
  }

  @Test
  def accumulatesDerivedDecoderFailuresForInvalidProducts(): Unit = {
    val invalid: Json = Json.obj(
      "name" -> Json.fromInt(7),
      "age" -> Json.fromString("old"),
      "address" -> Json.obj(
        "street" -> Json.False,
        "zip" -> Json.fromString("not-a-zip-code")
      ),
      "nickname" -> Json.fromInt(123),
      "tags" -> Json.fromString("not-a-list"),
      "scores" -> Json.obj("analysis" -> Json.fromString("excellent"))
    )

    val failures: List[DecodingFailure] = invalid.asAccumulating[GenericUser].fold(
      _.toList,
      value => fail[List[DecodingFailure]](s"Expected decoding to fail, but decoded: $value")
    )

    assertTrue(failures.size >= 6, s"Expected several accumulated failures, but got: $failures")
    assertTrue(failures.forall(_.message.nonEmpty))
    assertTrue(failures.exists(_.history.nonEmpty))
  }

  @Test
  def derivesCodecsForSealedTraitHierarchies(): Unit = {
    val events: List[DomainEvent] = List(
      UserRegistered(sampleUser),
      UserTagged("Ada Lovelace", List("featured", "admin"))
    )

    events.foreach { event =>
      val json: Json = event.asJson

      assertTrue(json.isObject)
      assertEquals(Right(event), json.as[DomainEvent])
    }
  }

  @Test
  def reportsUnknownSealedTraitConstructorsAsDecodeFailures(): Unit = {
    val unknownEvent: Json = Json.obj(
      "PasswordReset" -> Json.obj(
        "name" -> Json.fromString("Ada Lovelace")
      )
    )

    val result: Either[DecodingFailure, DomainEvent] = unknownEvent.as[DomainEvent]

    assertTrue(result.isLeft)
    result match {
      case Left(failure) => assertFalse(failure.message.isBlank)
      case Right(event) => fail[Unit](s"Expected unknown event decoding to fail, but decoded: $event")
    }
  }

  @Test
  def derivesCodecsAutomaticallyFromImportedGenericInstances(): Unit = {
    import io.circe.generic.auto.*

    val report: AutoReport = AutoReport(
      metric = AutoMetric("throughput", Vector(1.25, 2.5, 5.0)),
      labels = Set("native-image", "scala-3"),
      active = true
    )

    val json: Json = report.asJson

    assertEquals(Right("throughput"), json.hcursor.downField("metric").get[String]("name"))
    assertEquals(Right(Vector(1.25, 2.5, 5.0)), json.hcursor.downField("metric").get[Vector[Double]]("values"))
    assertEquals(Right(Set("native-image", "scala-3")), json.hcursor.get[Set[String]]("labels"))
    assertEquals(Right(report), json.as[AutoReport])
  }

  private def sampleUser: GenericUser = {
    GenericUser(
      name = "Ada Lovelace",
      age = 36,
      address = GenericAddress("St James Square", 12345),
      nickname = Some("ada"),
      tags = List("math", "compiler"),
      scores = Map("analysis" -> BigDecimal("98.5"), "logic" -> BigDecimal("100"))
    )
  }
}
