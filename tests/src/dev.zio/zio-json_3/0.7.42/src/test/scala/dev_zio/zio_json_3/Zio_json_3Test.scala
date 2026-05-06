/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.Chunk
import zio.json.*
import zio.json.ast.{Json, JsonCursor}

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneId}
import java.util.{Currency, UUID}
import scala.collection.immutable.{ListMap, TreeSet}

class Zio_json_3Test {
  import Zio_json_3Test.*

  @Test
  def derivedCodecHonorsFieldAnnotationsAliasesDefaultsAndNoExtraFields(): Unit = {
    val id: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val profile: UserProfile = UserProfile(
      userId = id,
      fullName = "Ada Lovelace",
      email = None,
      phone = None,
      tags = Nil,
      internalNote = "not serialized",
      address = Address("St James's Square", 42)
    )

    val encodedAst: Either[String, Json] = profile.toJson.fromJson[Json]

    assertRight(
      encodedAst,
      Json.Obj(
        "id" -> Json.Str(id.toString),
        "full_name" -> Json.Str("Ada Lovelace"),
        "phone" -> Json.Null,
        "address" -> Json.Obj(
          "streetName" -> Json.Str("St James's Square"),
          "zipCode" -> Json.Num(42)
        )
      )
    )
    assertRight(profile.toJson.fromJson[UserProfile], profile.copy(internalNote = "internal"))

    val aliasJson: String =
      s"""{"id":"$id","name":"Ada Lovelace","phone":"+44","address":{"streetName":"St James's Square","zipCode":42}}"""

    assertRight(
      aliasJson.fromJson[UserProfile],
      profile.copy(phone = Some("+44"), internalNote = "internal")
    )

    val jsonWithExtraField: String =
      s"""{"id":"$id","name":"Ada Lovelace","phone":null,"address":{"streetName":"St James's Square","zipCode":42},"unexpected":true}"""

    assertLeftContains(jsonWithExtraField.fromJson[UserProfile], "invalid extra field")
  }

  @Test
  def derivesSealedTraitCodecWithDiscriminatorAndHints(): Unit = {
    val created: AuditEvent = Created(resource = "invoice", revision = 3)
    val deleted: AuditEvent = Deleted(resource = "invoice")
    val heartbeat: AuditEvent = Heartbeat

    assertRoundTrips(created)
    assertRoundTrips(deleted)
    assertRoundTrips(heartbeat)

    assertRight(
      created.toJson.fromJson[Json],
      Json.Obj(
        "kind" -> Json.Str("created"),
        "resource" -> Json.Str("invoice"),
        "revision" -> Json.Num(3)
      )
    )
    assertRight(
      deleted.toJson.fromJson[AuditEvent],
      deleted
    )
    assertLeftContains("""{"kind":"unknown","resource":"invoice"}""".fromJson[AuditEvent], "invalid disambiguator")
  }

  @Test
  def encodesAndDecodesPrimitiveCollectionsMapsEitherAndTuples(): Unit = {
    assertThat("line\n\"quoted\"".toJson).isEqualTo("\"line\\n\\\"quoted\\\"\"")
    assertRight("\"123\"".fromJson[Int], 123)
    assertLeftContains("2147483648".fromJson[Int], "expected an Int")

    val nested: collection.SortedMap[Int, Vector[Option[BigDecimal]]] = collection.SortedMap(
      2 -> Vector(Some(BigDecimal("2.50")), None),
      10 -> Vector.empty
    )
    assertRight(nested.toJson.fromJson[collection.SortedMap[Int, Vector[Option[BigDecimal]]]], nested)

    val listMap: ListMap[Long, String] = ListMap(7L -> "seven", 11L -> "eleven")
    assertRight(listMap.toJson.fromJson[ListMap[Long, String]], listMap)

    val treeSet: TreeSet[String] = TreeSet("gamma", "alpha", "beta")
    assertRight(treeSet.toJson.fromJson[TreeSet[String]], treeSet)

    val chunk: Chunk[Int] = Chunk(1, 2, 3)
    assertThat(chunk.toJson.fromJson[Chunk[Int]].map(_.toList)).isEqualTo(Right(List(1, 2, 3)))

    val eitherLeft: Either[Int, String] = Left(5)
    val eitherRight: Either[Int, String] = Right("ok")
    assertRight(eitherLeft.toJson.fromJson[Either[Int, String]], eitherLeft)
    assertRight(eitherRight.toJson.fromJson[Either[Int, String]], eitherRight)
    assertLeftContains("{}".fromJson[Either[Int, String]], "missing fields")

    val tuple: (Int, String, Boolean, Option[String]) = (1, "two", true, None)
    assertThat(tuple.toJson).isEqualTo("[1,\"two\",true,null]")
    assertRight(tuple.toJson.fromJson[(Int, String, Boolean, Option[String])], tuple)
  }

  @Test
  def supportsJsonAstCursorsTransformationsMergingAndTypedConversion(): Unit = {
    val document: Json = assertRightValue(
      """{"items":[{"id":1,"active":true},{"id":2,"active":false}],"meta":{"count":2}}""".fromJson[Json]
    )
    val activeCursor: JsonCursor[Json, Json.Bool] =
      JsonCursor.field("items").isArray.element(1).isObject.field("active").isBool
    val countCursor: JsonCursor[Json, Json.Num] =
      JsonCursor.field("meta").isObject.field("count").isNumber

    assertRight(document.get(activeCursor), Json.Bool.False)
    assertRight(document.get(countCursor).flatMap(_.as[Int]), 2)

    val activated: Json = assertRightValue(document.transformAt(activeCursor)(_ => Json.Bool.True))
    assertRight(activated.get(activeCursor), Json.Bool.True)

    val withoutCount: Json = assertRightValue(activated.delete(countCursor))
    assertLeftContains(withoutCount.get(countCursor), "No such field")

    val incremented: Json = document.transformDown(_.mapNumber(_.add(java.math.BigDecimal.ONE)))
    assertRight(incremented.get(countCursor).flatMap(_.as[Int]), 3)

    val numberCount: Int = document.foldDown(0) {
      case (count, _: Json.Num) => count + 1
      case (count, _) => count
    }
    assertThat(numberCount).isEqualTo(3)

    val merged: Json = Json.Obj(
      "settings" -> Json.Obj("theme" -> Json.Str("dark"), "size" -> Json.Num(1)),
      "items" -> Json.Arr(Json.Num(1))
    ).merge(
      Json.Obj(
        "settings" -> Json.Obj("size" -> Json.Num(2), "enabled" -> Json.Bool.True),
        "items" -> Json.Arr(Json.Num(9), Json.Num(10))
      )
    )

    assertThat(merged).isEqualTo(
      Json.Obj(
        "settings" -> Json.Obj(
          "theme" -> Json.Str("dark"),
          "size" -> Json.Num(2),
          "enabled" -> Json.Bool.True
        ),
        "items" -> Json.Arr(Json.Num(9), Json.Num(10))
      )
    )
  }

  @Test
  def supportsJavaTimeUuidCurrencyAndCustomTransformedCodecs(): Unit = {
    val appointment: Appointment = Appointment(
      id = UUID.fromString("00000000-0000-0000-0000-000000000123"),
      day = LocalDate.parse("2024-02-29"),
      startsAt = OffsetDateTime.parse("2024-02-29T10:15:30+01:00"),
      recordedAt = Instant.parse("2024-02-29T09:15:30Z"),
      zone = ZoneId.of("Europe/Paris"),
      currency = Currency.getInstance("EUR")
    )

    assertRoundTrips(appointment)
    assertRight("\"2024-02-29\"".fromJson[LocalDate], LocalDate.parse("2024-02-29"))
    assertLeftContains("\"2024-02-30\"".fromJson[LocalDate], "expected a LocalDate")

    assertThat(UserId(42).toJson).isEqualTo("42")
    assertRight("42".fromJson[UserId], UserId(42))
    assertLeftContains("0".fromJson[UserId], "id must be positive")

    val labelledEncoder: JsonEncoder[Labelled] =
      JsonEncoder.int.zip(JsonEncoder.string).contramap(labelled => (labelled.id, labelled.label))
    val labelledDecoder: JsonDecoder[Labelled] =
      JsonDecoder.int.zip(JsonDecoder.string).map { case (id, label) => Labelled(id, label) }

    assertThat(labelledEncoder.encodeJson(Labelled(9, "nine"), None).toString).isEqualTo("[9,\"nine\"]")
    assertRight(labelledDecoder.decodeJson("[9,\"nine\"]"), Labelled(9, "nine"))
  }

  private def assertRoundTrips[A: JsonCodec](value: A): Unit =
    assertRight(value.toJson.fromJson[A], value)

  private def assertRight[A](actual: Either[String, A], expected: A): Unit =
    assertThat(actual).isEqualTo(Right(expected))

  private def assertRightValue[A](actual: Either[String, A]): A =
    actual match {
      case Right(value) => value
      case Left(error) => throw new AssertionError(s"Expected Right, got Left($error)")
    }

  private def assertLeftContains[A](actual: Either[String, A], expectedMessagePart: String): Unit =
    actual match {
      case Left(error) => assertThat(error).contains(expectedMessagePart)
      case Right(value) => throw new AssertionError(s"Expected Left containing '$expectedMessagePart', got Right($value)")
    }
}

object Zio_json_3Test {
  final case class Address(streetName: String, zipCode: Int)
  object Address {
    implicit val codec: JsonCodec[Address] = DeriveJsonCodec.gen[Address]
  }

  @jsonMemberNames(SnakeCase)
  @jsonNoExtraFields
  final case class UserProfile(
    @jsonField("id") userId: UUID,
    @jsonAliases("name", "displayName") fullName: String,
    email: Option[String],
    @jsonExplicitNull phone: Option[String],
    @jsonExplicitEmptyCollections(encoding = false, decoding = false) tags: List[String] = Nil,
    @jsonExclude internalNote: String = "internal",
    address: Address
  )
  object UserProfile {
    implicit val codec: JsonCodec[UserProfile] = DeriveJsonCodec.gen[UserProfile]
  }

  @jsonDiscriminator("kind")
  sealed trait AuditEvent
  object AuditEvent {
    implicit val codec: JsonCodec[AuditEvent] = DeriveJsonCodec.gen[AuditEvent]
  }

  @jsonHint("created")
  final case class Created(resource: String, revision: Int) extends AuditEvent

  @jsonHint("deleted")
  final case class Deleted(resource: String) extends AuditEvent

  @jsonHint("heartbeat")
  case object Heartbeat extends AuditEvent

  final case class Appointment(
    id: UUID,
    day: LocalDate,
    startsAt: OffsetDateTime,
    recordedAt: Instant,
    zone: ZoneId,
    currency: Currency
  )
  object Appointment {
    implicit val codec: JsonCodec[Appointment] = DeriveJsonCodec.gen[Appointment]
  }

  final case class UserId(value: Int)
  object UserId {
    implicit val codec: JsonCodec[UserId] = JsonCodec.int.transformOrFail(
      id => Either.cond(id > 0, UserId(id), "id must be positive"),
      _.value
    )
  }

  final case class Labelled(id: Int, label: String)
}
