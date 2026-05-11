/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_core_3

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.JsonNumber
import io.circe.JsonObject
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.circe.Printer
import io.circe.syntax._
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.Try

final case class Profile(
    name: String,
    age: Int,
    created: Instant,
    aliases: List[String],
    metadata: Map[UUID, BigDecimal]
)

object Profile {
  given Encoder[Profile] = Encoder.forProduct5("name", "age", "created", "aliases", "metadata") { profile =>
    (profile.name, profile.age, profile.created, profile.aliases, profile.metadata)
  }

  given Decoder[Profile] = Decoder.forProduct5("name", "age", "created", "aliases", "metadata")(Profile.apply)
}

final case class AccountId(value: UUID)

object AccountId {
  given KeyEncoder[AccountId] = KeyEncoder.instance(_.value.toString)

  given KeyDecoder[AccountId] = KeyDecoder.instance { key =>
    Try(UUID.fromString(key)).toOption.map(AccountId.apply)
  }
}

final case class Command(action: String, target: Option[String])

final case class DerivedJob(jobId: String, ownerName: String, maxRetries: Int = 3)

object DerivedJob {
  private val jsonFields: Set[String] = Set("job_id", "owner_name", "max_retries")

  given Encoder.AsObject[DerivedJob] = Encoder.AsObject.instance { job =>
    JsonObject(
      "job_id" -> Json.fromString(job.jobId),
      "owner_name" -> Json.fromString(job.ownerName),
      "max_retries" -> Json.fromInt(job.maxRetries)
    )
  }

  given Decoder[DerivedJob] = Decoder.instance { cursor =>
    cursor.keys match {
      case Some(keys) =>
        val unexpectedFields: List[String] = keys.filterNot(field => jsonFields.contains(field)).toList
        if unexpectedFields.nonEmpty then
          Left(DecodingFailure(s"Unexpected fields: ${unexpectedFields.mkString(", ")}", cursor.history))
        else
          for {
            jobId <- cursor.get[String]("job_id")
            ownerName <- cursor.get[String]("owner_name")
            maxRetries <- cursor.get[Option[Int]]("max_retries").map(_.getOrElse(3))
          } yield DerivedJob(jobId, ownerName, maxRetries)
      case None => Left(DecodingFailure("Expected JSON object", cursor.history))
    }
  }
}

enum TicketState {
  case Open, InProgress, Closed
}

object TicketState {
  given Encoder[TicketState] = Encoder.instance(state => Json.fromString(state.toString))

  given Decoder[TicketState] = Decoder.decodeString.emap {
    case "Open" => Right(TicketState.Open)
    case "InProgress" => Right(TicketState.InProgress)
    case "Closed" => Right(TicketState.Closed)
    case other => Left(s"enum TicketState does not contain case: $other")
  }
}

object Command {
  given Decoder[Command] = Decoder.instance { (cursor: HCursor) =>
    val actionCursor = cursor.downField("action")
    cursor.get[String]("action").flatMap {
      case "start" => cursor.get[String]("target").map(target => Command("start", Some(target)))
      case "stop" => Right(Command("stop", None))
      case other => Left(DecodingFailure(s"Unsupported action: $other", actionCursor.history))
    }
  }
}

class Circe_core_3Test {
  @Test
  def buildsTransformsAndPrintsJsonDocuments(): Unit = {
    val document: Json = Json.obj(
      "b" -> Json.Null,
      "a" -> Json.arr(Json.fromInt(2), Json.fromString("text")),
      "nested" -> Json.obj("keep" -> Json.True, "drop" -> Json.Null)
    )

    assertThat(document.isObject).isTrue
    assertThat(document.hcursor.downField("nested").downField("keep").as[Boolean]).isEqualTo(Right(true))
    assertThat(Json.fromDouble(Double.NaN).isEmpty).isTrue
    assertThat(Json.fromDoubleOrString(Double.NaN).asString).isEqualTo(Some("NaN"))

    val transformed: Json = document.deepDropNullValues.mapObject(_.add("z", Json.fromLong(3L)))
    assertThat(transformed.noSpacesSortKeys).isEqualTo("""{"a":[2,"text"],"nested":{"keep":true},"z":3}""")

    val pretty: String = Printer.spaces2SortKeys.print(transformed)
    assertThat(pretty).contains("\n  \"a\" : [")
    assertThat(pretty).contains("\n  \"nested\" : {")

    val compactBuffer: ByteBuffer = Printer.noSpaces.printToByteBuffer(transformed, StandardCharsets.UTF_8)
    val compactBytes: Array[Byte] = new Array[Byte](compactBuffer.remaining)
    compactBuffer.get(compactBytes)
    assertThat(new String(compactBytes, StandardCharsets.UTF_8)).isEqualTo(transformed.noSpaces)
  }

  @Test
  def manipulatesJsonObjectsWithoutLosingInsertionOrder(): Unit = {
    val base: JsonObject = JsonObject(
      "name" -> Json.fromString("Ada"),
      "nullable" -> Json.Null
    )
    val updated: JsonObject = base
      .add("enabled", Json.fromBoolean(true))
      .remove("nullable")
      .mapValues(_.withString(value => Json.fromString(value.toUpperCase)))

    assertThat(updated.keys.toSeq.asJava).containsExactly("name", "enabled")
    assertThat(updated("name")).isEqualTo(Some(Json.fromString("ADA")))
    assertThat(updated.contains("nullable")).isFalse
    assertThat(updated.size).isEqualTo(2)

    val left: JsonObject = JsonObject(
      "config" -> Json.obj("threads" -> Json.fromInt(2), "debug" -> Json.False),
      "owner" -> Json.fromString("test")
    )
    val right: JsonObject = JsonObject(
      "config" -> Json.obj("debug" -> Json.True, "region" -> Json.fromString("eu"))
    )

    assertThat(left.deepMerge(right).toJson.noSpacesSortKeys)
      .isEqualTo("""{"config":{"debug":true,"region":"eu","threads":2},"owner":"test"}""")
  }

  @Test
  def navigatesEditsAndDeletesWithCursors(): Unit = {
    val document: Json = Json.obj(
      "users" -> Json.arr(
        Json.obj("name" -> Json.fromString("Ada"), "scores" -> Json.arr(Json.fromInt(1), Json.fromInt(2))),
        Json.obj("name" -> Json.fromString("Linus"), "scores" -> Json.arr(Json.fromInt(3), Json.fromInt(5)))
      )
    )

    val secondName = document.hcursor.downField("users").downN(1).downField("name")
    assertThat(secondName.succeeded).isTrue
    assertThat(secondName.key).isEqualTo(Some("name"))
    assertThat(secondName.as[String]).isEqualTo(Right("Linus"))
    assertThat(secondName.pathString).isEqualTo(".users[1].name")

    val incremented: Json = decodeOptionOrFail(
      document.hcursor
        .downField("users")
        .downArray
        .downField("scores")
        .downN(1)
        .withFocus(incrementJsonIntegerBy(10))
        .top
    )
    assertThat(incremented.hcursor.downField("users").downArray.downField("scores").downN(1).as[Int])
      .isEqualTo(Right(12))

    val withoutFirstUserScores: Json = decodeOptionOrFail(
      incremented.hcursor.downField("users").downArray.downField("scores").delete.top
    )
    assertThat(withoutFirstUserScores.hcursor.downField("users").downArray.downField("scores").succeeded).isFalse
    assertThat(withoutFirstUserScores.hcursor.downField("users").downN(1).downField("scores").as[List[Int]])
      .isEqualTo(Right(List(3, 5)))
  }

  @Test
  def encodesAndDecodesProductsCollectionsAndJavaTimeValues(): Unit = {
    val firstMetadataKey: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val secondMetadataKey: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val profile: Profile = Profile(
      name = "Ada Lovelace",
      age = 36,
      created = Instant.parse("2024-01-02T03:04:05Z"),
      aliases = List("analyst", "programmer"),
      metadata = Map(firstMetadataKey -> BigDecimal("12.5"), secondMetadataKey -> BigDecimal("7"))
    )

    val json: Json = profile.asJson
    assertThat(json.hcursor.get[String]("name")).isEqualTo(Right("Ada Lovelace"))
    assertThat(json.hcursor.get[Instant]("created")).isEqualTo(Right(profile.created))
    assertThat(decodeOrFail(json.hcursor.get[List[String]]("aliases")).asJava).containsExactly("analyst", "programmer")
    assertThat(decodeOrFail(json.hcursor.get[Map[UUID, BigDecimal]]("metadata"))).isEqualTo(profile.metadata)
    assertThat(json.as[Profile]).isEqualTo(Right(profile))
  }

  @Test
  def accumulatesFieldDecodingFailuresForProductDecoders(): Unit = {
    val invalidProfile: Json = Json.obj(
      "name" -> Json.fromInt(42),
      "age" -> Json.fromString("old"),
      "created" -> Json.fromString("not-an-instant"),
      "aliases" -> Json.fromString("not-a-list"),
      "metadata" -> Json.obj("not-a-uuid" -> Json.fromString("not-a-number"))
    )

    val failures: List[DecodingFailure] = invalidProfile.asAccumulating[Profile].fold(
      _.toList,
      value => fail(s"Expected decoding to fail, but decoded: $value")
    )

    assertThat(failures.size).isGreaterThanOrEqualTo(4)
    assertThat(failures.map(_.history.nonEmpty).asJava).contains(true)
  }

  @Test
  def encodesAndDecodesMapsWithCustomKeyCodecs(): Unit = {
    val first: AccountId = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    val second: AccountId = AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

    val json: Json = Map(first -> 10, second -> 20).asJson

    assertThat(json.noSpacesSortKeys)
      .isEqualTo("""{"11111111-1111-1111-1111-111111111111":10,"22222222-2222-2222-2222-222222222222":20}""")
    assertThat(json.as[Map[AccountId, Int]]).isEqualTo(Right(Map(first -> 10, second -> 20)))

    val invalid: Either[DecodingFailure, Map[AccountId, Int]] = Json.obj("not-a-uuid" -> Json.fromInt(1)).as[Map[AccountId, Int]]
    assertThat(invalid.isLeft).isTrue
  }

  @Test
  def usesCustomDecoderInstancesForDomainValidation(): Unit = {
    val start: Json = Json.obj("action" -> Json.fromString("start"), "target" -> Json.fromString("service-a"))
    val stop: Json = Json.obj("action" -> Json.fromString("stop"))
    val unsupported: Json = Json.obj("action" -> Json.fromString("restart"), "target" -> Json.fromString("service-a"))

    assertThat(start.as[Command]).isEqualTo(Right(Command("start", Some("service-a"))))
    assertThat(stop.as[Command]).isEqualTo(Right(Command("stop", None)))

    val error: DecodingFailure = unsupported.as[Command] match {
      case Left(error) => error
      case Right(value) => fail(s"Expected unsupported action to fail, but decoded: $value")
    }
    assertThat(error.message).isEqualTo("Unsupported action: restart")
    assertThat(error.history.asJava).isNotEmpty
  }

  @Test
  def encodesAndDecodesProductsWithDefaultsAndStrictDecoding(): Unit = {
    val encoded: Json = DerivedJob("job-1", "Ada", 5).asJson
    assertThat(encoded.noSpacesSortKeys).isEqualTo("""{"job_id":"job-1","max_retries":5,"owner_name":"Ada"}""")

    val decodedWithDefault: Either[DecodingFailure, DerivedJob] = Json.obj(
      "job_id" -> Json.fromString("job-2"),
      "owner_name" -> Json.fromString("Linus")
    ).as[DerivedJob]
    assertThat(decodedWithDefault).isEqualTo(Right(DerivedJob("job-2", "Linus")))

    val decodedWithUnknownField: Either[DecodingFailure, DerivedJob] = Json.obj(
      "job_id" -> Json.fromString("job-3"),
      "owner_name" -> Json.fromString("Grace"),
      "unexpected" -> Json.True
    ).as[DerivedJob]
    assertThat(decodedWithUnknownField.isLeft).isTrue
  }

  @Test
  def encodesAndDecodesSingletonEnums(): Unit = {
    assertThat(TicketState.Open.asJson).isEqualTo(Json.fromString("Open"))
    assertThat(TicketState.InProgress.asJson).isEqualTo(Json.fromString("InProgress"))
    assertThat(Json.fromString("Closed").as[TicketState]).isEqualTo(Right(TicketState.Closed))

    val invalid: Either[DecodingFailure, TicketState] = Json.fromString("Blocked").as[TicketState]
    assertThat(invalid.isLeft).isTrue
  }

  @Test
  def preservesJsonNumberPrecisionAndRejectsInvalidNumericInput(): Unit = {
    val number: JsonNumber = decodeOptionOrFail(JsonNumber.fromString("12345678901234567890.125"))
    val json: Json = Json.fromJsonNumber(number)

    assertThat(json.asNumber.flatMap(_.toBigDecimal)).isEqualTo(Some(BigDecimal("12345678901234567890.125")))
    assertThat(json.as[BigDecimal]).isEqualTo(Right(BigDecimal("12345678901234567890.125")))
    assertThat(JsonNumber.fromString("1.2.3").isEmpty).isTrue
    assertThat(JsonNumber.fromString("not-a-number").isEmpty).isTrue
  }

  private def incrementJsonIntegerBy(amount: Int)(json: Json): Json = {
    json.asNumber.flatMap(_.toInt).map(value => Json.fromInt(value + amount)).getOrElse(json)
  }

  private def decodeOrFail[A](result: Either[DecodingFailure, A]): A = {
    result.fold(error => fail(s"Expected decoding success but got: $error"), identity)
  }

  private def decodeOptionOrFail[A](option: Option[A]): A = {
    option.getOrElse(fail("Expected value to be present"))
  }
}
