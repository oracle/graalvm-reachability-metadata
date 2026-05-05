/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_circe_3

import enumeratum.CirceEnum
import enumeratum.CirceKeyEnum
import enumeratum.Enum
import enumeratum.EnumEntry
import enumeratum.{Circe => EnumCirce}
import enumeratum.values.ByteCirceEnum
import enumeratum.values.ByteEnum
import enumeratum.values.ByteEnumEntry
import enumeratum.values.CharCirceEnum
import enumeratum.values.CharEnum
import enumeratum.values.CharEnumEntry
import enumeratum.values.IntCirceEnum
import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry
import enumeratum.values.LongCirceEnum
import enumeratum.values.LongEnum
import enumeratum.values.LongEnumEntry
import enumeratum.values.ShortCirceEnum
import enumeratum.values.ShortEnum
import enumeratum.values.ShortEnumEntry
import enumeratum.values.StringCirceEnum
import enumeratum.values.{Circe => ValueEnumCirce}
import enumeratum.values.StringEnum
import enumeratum.values.StringEnumEntry
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.KeyDecoder
import io.circe.KeyEncoder
import io.circe.syntax._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class Enumeratum_circe_3Test {
  @Test
  def encodesAndDecodesPlainEnumsThroughCirceTypeClasses(): Unit = {
    assertEquals(Json.fromString("Stop"), (TrafficSignal.Stop: TrafficSignal).asJson)
    assertEquals(Json.fromString("Proceed"), (TrafficSignal.Proceed: TrafficSignal).asJson)
    assertRightEquals(TrafficSignal.FlashingYellow, Json.fromString("FlashingYellow").as[TrafficSignal])
    assertRightEquals(TrafficSignal.Stop, Decoder[TrafficSignal].decodeJson(Json.fromString("Stop")))
  }

  @Test
  def reportsDecodingFailuresForUnknownEnumNamesAndWrongJsonTypes(): Unit = {
    assertLeftMessageContains(Json.fromString("Yield").as[TrafficSignal], "'Yield' is not a member of enum")
    assertLeft(Json.fromInt(1).as[TrafficSignal])
  }

  @Test
  def respectsCustomEntryNamesForPlainEnumCodecs(): Unit = {
    assertEquals(Json.fromString("user_signed_in"), (AuditEvent.UserSignedIn: AuditEvent).asJson)
    assertEquals(Json.fromString("password_reset_requested"), (AuditEvent.PasswordResetRequested: AuditEvent).asJson)
    assertRightEquals(AuditEvent.UserSignedIn, Json.fromString("user_signed_in").as[AuditEvent])
    assertRightEquals(AuditEvent.PasswordResetRequested, Json.fromString("password_reset_requested").as[AuditEvent])
    assertLeftMessageContains(Json.fromString("UserSignedIn").as[AuditEvent], "'UserSignedIn' is not a member")
  }

  @Test
  def supportsAlternativeCaseSensitiveEnumCodecs(): Unit = {
    val lowercaseEncoder = EnumCirce.encoderLowercase(TrafficSignal)
    val uppercaseEncoder = EnumCirce.encoderUppercase(TrafficSignal)
    val lowercaseDecoder = EnumCirce.decoderLowercaseOnly(TrafficSignal)
    val uppercaseDecoder = EnumCirce.decoderUppercaseOnly(TrafficSignal)
    val insensitiveDecoder = EnumCirce.decodeCaseInsensitive(TrafficSignal)

    assertEquals(Json.fromString("flashingyellow"), lowercaseEncoder(TrafficSignal.FlashingYellow))
    assertEquals(Json.fromString("FLASHINGYELLOW"), uppercaseEncoder(TrafficSignal.FlashingYellow))
    assertRightEquals(TrafficSignal.FlashingYellow, lowercaseDecoder.decodeJson(Json.fromString("flashingyellow")))
    assertRightEquals(TrafficSignal.FlashingYellow, uppercaseDecoder.decodeJson(Json.fromString("FLASHINGYELLOW")))
    assertRightEquals(TrafficSignal.FlashingYellow, insensitiveDecoder.decodeJson(Json.fromString("fLaShInGyElLoW")))
    assertLeftMessageContains(lowercaseDecoder.decodeJson(Json.fromString("FlashingYellow")), "is not a member")
    assertLeftMessageContains(uppercaseDecoder.decodeJson(Json.fromString("FlashingYellow")), "is not a member")
  }

  @Test
  def encodesAndDecodesPlainEnumsAsJsonObjectKeys(): Unit = {
    val priorities: Map[TrafficSignal, Int] = Map(
      TrafficSignal.Stop -> 1,
      TrafficSignal.Proceed -> 2,
      TrafficSignal.FlashingYellow -> 3
    )

    val json = priorities.asJson
    assertEquals(Some(Json.fromInt(1)), json.hcursor.get[Json]("Stop").toOption)
    assertEquals(Some(Json.fromInt(2)), json.hcursor.get[Json]("Proceed").toOption)
    assertEquals(Some(Json.fromInt(3)), json.hcursor.get[Json]("FlashingYellow").toOption)
    assertRightEquals(priorities, json.as[Map[TrafficSignal, Int]])
    assertLeft(Json.obj("Stop" -> Json.fromInt(1), "Unknown" -> Json.fromInt(2)).as[Map[TrafficSignal, Int]])
  }

  @Test
  def encodesAndDecodesNumericValueEnums(): Unit = {
    assertEquals(Json.fromInt(200), (HttpStatus.Ok: HttpStatus).asJson)
    assertEquals(Json.fromLong(9_223_372_036_854_775_000L), (LedgerId.Archive: LedgerId).asJson)
    assertEquals(Json.fromInt(2), (RetryTier.Secondary: RetryTier).asJson)
    assertEquals(Json.fromInt(7), (WireFlag.Synchronise: WireFlag).asJson)

    assertRightEquals(HttpStatus.NotFound, Json.fromInt(404).as[HttpStatus])
    assertRightEquals(LedgerId.Live, Json.fromLong(9_223_372_036_854_775_001L).as[LedgerId])
    assertRightEquals(RetryTier.Primary, Json.fromInt(1).as[RetryTier])
    assertRightEquals(WireFlag.Acknowledge, Json.fromInt(8).as[WireFlag])

    assertLeftMessageContains(Json.fromInt(500).as[HttpStatus], "500 is not a member")
    assertLeftMessageContains(Json.fromLong(1L).as[LedgerId], "1 is not a member")
    assertLeftMessageContains(Json.fromInt(3).as[RetryTier], "3 is not a member")
    assertLeftMessageContains(Json.fromInt(9).as[WireFlag], "9 is not a member")
  }

  @Test
  def encodesAndDecodesStringValueEnumsIncludingObjectKeys(): Unit = {
    assertEquals(Json.fromString("csv"), (ExportFormat.Csv: ExportFormat).asJson)
    assertEquals(Json.fromString("json"), (ExportFormat.JsonLines: ExportFormat).asJson)
    assertRightEquals(ExportFormat.Parquet, Json.fromString("parquet").as[ExportFormat])
    assertLeftMessageContains(Json.fromString("xml").as[ExportFormat], "xml is not a member")

    val extensions: Map[ExportFormat, String] = Map(ExportFormat.Csv -> ".csv", ExportFormat.JsonLines -> ".jsonl")
    val json = extensions.asJson
    assertEquals(Some(Json.fromString(".csv")), json.hcursor.get[Json]("csv").toOption)
    assertEquals(Some(Json.fromString(".jsonl")), json.hcursor.get[Json]("json").toOption)
    assertRightEquals(extensions, json.as[Map[ExportFormat, String]])
    assertLeft(Json.obj("xml" -> Json.fromString(".xml")).as[Map[ExportFormat, String]])
  }

  @Test
  def encodesAndDecodesCharacterValueEnums(): Unit = {
    assertEquals(Json.fromString("A"), (QualityGrade.Excellent: QualityGrade).asJson)
    assertEquals(Json.fromString("C"), (QualityGrade.Acceptable: QualityGrade).asJson)
    assertRightEquals(QualityGrade.NeedsReview, Json.fromString("B").as[QualityGrade])
    assertLeftMessageContains(Json.fromString("D").as[QualityGrade], "D is not a member")
    assertLeft(Json.fromString("AB").as[QualityGrade])
  }

  @Test
  def supportsStandaloneValueEnumCodecFactoriesWithoutMixinTraits(): Unit = {
    val ticketEncoder: Encoder[TicketState] = ValueEnumCirce.encoder[Int, TicketState](TicketState)
    val ticketDecoder: Decoder[TicketState] = ValueEnumCirce.decoder[Int, TicketState](TicketState)
    val bucketKeyEncoder: KeyEncoder[StorageBucket] = ValueEnumCirce.keyEncoder(StorageBucket)
    val bucketKeyDecoder: KeyDecoder[StorageBucket] = ValueEnumCirce.keyDecoder(StorageBucket)

    assertEquals(Json.fromInt(10), ticketEncoder(TicketState.Open))
    assertRightEquals(TicketState.Closed, ticketDecoder.decodeJson(Json.fromInt(20)))
    assertLeftMessageContains(ticketDecoder.decodeJson(Json.fromInt(30)), "30 is not a member")
    assertEquals("archive", bucketKeyEncoder(StorageBucket.Archive))
    assertEquals(Some(StorageBucket.Hot), bucketKeyDecoder("hot"))
    assertEquals(None, bucketKeyDecoder("cold"))
  }

  private def assertRightEquals[A](expected: A, actual: Decoder.Result[A]): Unit = {
    actual match {
      case Right(value) => assertEquals(expected, value)
      case Left(failure) => fail(s"Expected Right($expected), but decoder failed with: $failure")
    }
  }

  private def assertLeft[A](actual: Decoder.Result[A]): Unit = {
    actual match {
      case Right(value) => fail(s"Expected decoder failure, but decoded: $value")
      case Left(_) => ()
    }
  }

  private def assertLeftMessageContains[A](actual: Decoder.Result[A], expectedText: String): Unit = {
    actual match {
      case Right(value) => fail(s"Expected decoder failure containing '$expectedText', but decoded: $value")
      case Left(failure) => assertTrue(
          failure.message.contains(expectedText),
          s"Expected '${failure.message}' to contain '$expectedText'"
        )
    }
  }
}

sealed trait TrafficSignal extends EnumEntry
object TrafficSignal extends Enum[TrafficSignal] with CirceEnum[TrafficSignal] with CirceKeyEnum[TrafficSignal] {
  case object Stop extends TrafficSignal
  case object Proceed extends TrafficSignal
  case object FlashingYellow extends TrafficSignal

  val values = IndexedSeq(Stop, Proceed, FlashingYellow)
}

sealed trait AuditEvent extends EnumEntry with EnumEntry.Snakecase
object AuditEvent extends Enum[AuditEvent] with CirceEnum[AuditEvent] {
  case object UserSignedIn extends AuditEvent
  case object PasswordResetRequested extends AuditEvent

  val values = IndexedSeq(UserSignedIn, PasswordResetRequested)
}

sealed abstract class HttpStatus(val value: Int) extends IntEnumEntry
object HttpStatus extends IntEnum[HttpStatus] with IntCirceEnum[HttpStatus] {
  case object Ok extends HttpStatus(200)
  case object NotFound extends HttpStatus(404)

  val values = IndexedSeq(Ok, NotFound)
}

sealed abstract class LedgerId(val value: Long) extends LongEnumEntry
object LedgerId extends LongEnum[LedgerId] with LongCirceEnum[LedgerId] {
  case object Archive extends LedgerId(9_223_372_036_854_775_000L)
  case object Live extends LedgerId(9_223_372_036_854_775_001L)

  val values = IndexedSeq(Archive, Live)
}

sealed abstract class RetryTier(val value: Short) extends ShortEnumEntry
object RetryTier extends ShortEnum[RetryTier] with ShortCirceEnum[RetryTier] {
  case object Primary extends RetryTier(1.toShort)
  case object Secondary extends RetryTier(2.toShort)

  val values = IndexedSeq(Primary, Secondary)
}

sealed abstract class ExportFormat(val value: String) extends StringEnumEntry
object ExportFormat extends StringEnum[ExportFormat] with StringCirceEnum[ExportFormat] {
  case object Csv extends ExportFormat("csv")
  case object JsonLines extends ExportFormat("json")
  case object Parquet extends ExportFormat("parquet")

  val values = IndexedSeq(Csv, JsonLines, Parquet)
}

sealed abstract class QualityGrade(val value: Char) extends CharEnumEntry
object QualityGrade extends CharEnum[QualityGrade] with CharCirceEnum[QualityGrade] {
  case object Excellent extends QualityGrade('A')
  case object NeedsReview extends QualityGrade('B')
  case object Acceptable extends QualityGrade('C')

  val values = IndexedSeq(Excellent, NeedsReview, Acceptable)
}

sealed abstract class WireFlag(val value: Byte) extends ByteEnumEntry
object WireFlag extends ByteEnum[WireFlag] with ByteCirceEnum[WireFlag] {
  case object Synchronise extends WireFlag(7.toByte)
  case object Acknowledge extends WireFlag(8.toByte)

  val values = IndexedSeq(Synchronise, Acknowledge)
}

sealed abstract class TicketState(val value: Int) extends IntEnumEntry
object TicketState extends IntEnum[TicketState] {
  case object Open extends TicketState(10)
  case object Closed extends TicketState(20)

  val values = IndexedSeq(Open, Closed)
}

sealed abstract class StorageBucket(val value: String) extends StringEnumEntry
object StorageBucket extends StringEnum[StorageBucket] {
  case object Hot extends StorageBucket("hot")
  case object Archive extends StorageBucket("archive")

  val values = IndexedSeq(Hot, Archive)
}
