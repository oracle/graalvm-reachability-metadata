/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_3

import enumeratum.Enum
import enumeratum.EnumEntry
import enumeratum.values.ByteEnum
import enumeratum.values.ByteEnumEntry
import enumeratum.values.CharEnum
import enumeratum.values.CharEnumEntry
import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry
import enumeratum.values.LongEnum
import enumeratum.values.LongEnumEntry
import enumeratum.values.ShortEnum
import enumeratum.values.ShortEnumEntry
import enumeratum.values.StringEnum
import enumeratum.values.StringEnumEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import java.util.NoSuchElementException

class Enumeratum_3Test {
  @Test
  def enumFindValuesAndLookupByEntryName(): Unit = {
    val expectedValues: IndexedSeq[WorkflowState] =
      IndexedSeq(WorkflowState.Submitted, WorkflowState.InProgress, WorkflowState.Completed)

    assertEquals(expectedValues, WorkflowState.values)
    assertEquals(
      Map(
        "Submitted" -> WorkflowState.Submitted,
        "InProgress" -> WorkflowState.InProgress,
        "Completed" -> WorkflowState.Completed
      ),
      WorkflowState.namesToValuesMap
    )
    assertEquals(Map.empty[String, WorkflowState], WorkflowState.extraNamesToValuesMap)
    assertEquals(1, WorkflowState.indexOf(WorkflowState.InProgress))
    assertEquals(Map(WorkflowState.Submitted -> 0, WorkflowState.InProgress -> 1, WorkflowState.Completed -> 2), WorkflowState.valuesToIndex)

    assertSame(WorkflowState.Submitted, WorkflowState.withName("Submitted"))
    assertEquals(Some(WorkflowState.InProgress), WorkflowState.withNameOption("InProgress"))
    assertEquals(Right(WorkflowState.Completed), WorkflowState.withNameEither("Completed"))
    assertEquals(None, WorkflowState.withNameOption("submitted"))
  }

  @Test
  def enumReportsMissingNamesWithStructuredError(): Unit = {
    val thrown: NoSuchElementException = assertThrows(
      classOf[NoSuchElementException],
      () => {
        WorkflowState.withName("Cancelled")
        ()
      }
    )

    assertTrue(thrown.getMessage.contains("Cancelled"))
    assertTrue(thrown.getMessage.contains("Submitted"))
    assertEquals(None, WorkflowState.withNameOption("Cancelled"))

    WorkflowState.withNameEither("Cancelled") match {
      case Left(error) =>
        assertEquals("Cancelled", error.notFoundName)
        assertEquals(WorkflowState.values, error.enumValues)
        assertEquals("Cancelled", error._1)
        assertEquals(WorkflowState.values, error._2)
        assertTrue(error.getMessage.contains("Completed"))
      case Right(value) => fail(s"Expected a missing-name error but found $value")
    }
  }

  @Test
  def caseInsensitiveLookupVariantsUsePrecomputedNameMaps(): Unit = {
    assertSame(WorkflowState.Submitted, WorkflowState.withNameInsensitive("submitted"))
    assertSame(WorkflowState.Submitted, WorkflowState.withNameInsensitive("SUBMITTED"))
    assertEquals(Some(WorkflowState.InProgress), WorkflowState.withNameInsensitiveOption("inprogress"))
    assertEquals(Right(WorkflowState.Completed), WorkflowState.withNameInsensitiveEither("completed"))

    assertSame(WorkflowState.InProgress, WorkflowState.withNameUppercaseOnly("INPROGRESS"))
    assertEquals(Some(WorkflowState.InProgress), WorkflowState.withNameUppercaseOnlyOption("INPROGRESS"))
    assertEquals(None, WorkflowState.withNameUppercaseOnlyOption("InProgress"))
    assertEquals(Right(WorkflowState.Completed), WorkflowState.withNameUppercaseOnlyEither("COMPLETED"))

    assertSame(WorkflowState.Completed, WorkflowState.withNameLowercaseOnly("completed"))
    assertEquals(Some(WorkflowState.Submitted), WorkflowState.withNameLowercaseOnlyOption("submitted"))
    assertEquals(None, WorkflowState.withNameLowercaseOnlyOption("Submitted"))
    assertTrue(WorkflowState.withNameLowercaseOnlyEither("missing").isLeft)
  }

  @Test
  def extraNamesMapProvidesLegacyAliasesForLookups(): Unit = {
    assertEquals(
      Map("queued" -> DocumentState.Draft, "archived" -> DocumentState.Retired),
      DocumentState.extraNamesToValuesMap
    )
    assertEquals(
      Map(
        "Draft" -> DocumentState.Draft,
        "Published" -> DocumentState.Published,
        "Retired" -> DocumentState.Retired,
        "queued" -> DocumentState.Draft,
        "archived" -> DocumentState.Retired
      ),
      DocumentState.namesToValuesMap
    )

    assertSame(DocumentState.Draft, DocumentState.withName("queued"))
    assertEquals(Some(DocumentState.Retired), DocumentState.withNameOption("archived"))
    assertEquals(Right(DocumentState.Draft), DocumentState.withNameEither("queued"))
    assertSame(DocumentState.Retired, DocumentState.withNameInsensitive("ARCHIVED"))
    assertEquals(Some(DocumentState.Draft), DocumentState.withNameUppercaseOnlyOption("QUEUED"))
    assertEquals(Some(DocumentState.Retired), DocumentState.withNameLowercaseOnlyOption("archived"))
  }

  @Test
  def namingConventionTraitsTransformEntryNamesAndLookups(): Unit = {
    val expectedNames: Map[NamingStyle, String] = Map(
      NamingStyle.SnakeCaseValue -> "snake_case_value",
      NamingStyle.UpperSnakeCaseValue -> "UPPER_SNAKE_CASE_VALUE",
      NamingStyle.CapitalSnakeCaseValue -> "Capital_Snake_Case_Value",
      NamingStyle.HyphenCaseValue -> "hyphen-case-value",
      NamingStyle.UpperHyphenCaseValue -> "UPPER-HYPHEN-CASE-VALUE",
      NamingStyle.CapitalHyphenCaseValue -> "Capital-Hyphen-Case-Value",
      NamingStyle.DotCaseValue -> "dot.case.value",
      NamingStyle.UpperDotCaseValue -> "UPPER.DOT.CASE.VALUE",
      NamingStyle.CapitalDotCaseValue -> "Capital.Dot.Case.Value",
      NamingStyle.WordCaseValue -> "word case value",
      NamingStyle.UpperWordCaseValue -> "UPPER WORD CASE VALUE",
      NamingStyle.CapitalWordCaseValue -> "Capital Word Case Value",
      NamingStyle.LowerCamelCaseValue -> "lowerCamelCaseValue",
      NamingStyle.CamelCaseValue -> "CamelCaseValue",
      NamingStyle.LowercaseValue -> "lowercasevalue",
      NamingStyle.UppercaseValue -> "UPPERCASEVALUE",
      NamingStyle.UncapitalisedValue -> "uncapitalisedValue"
    )

    expectedNames.foreach { case (entry: NamingStyle, expectedName: String) =>
      assertEquals(expectedName, entry.entryName)
      assertSame(entry, NamingStyle.withName(expectedName))
    }

    assertEquals(expectedNames.values.toSet, NamingStyle.namesToValuesMap.keySet)
  }

  @Test
  def intValueEnumFindsValuesAndReportsMissingValues(): Unit = {
    val expectedValues: IndexedSeq[HttpCode] = IndexedSeq(HttpCode.Ok, HttpCode.Created, HttpCode.NotFound)

    assertEquals(expectedValues, HttpCode.values)
    assertEquals(Map(200 -> HttpCode.Ok, 201 -> HttpCode.Created, 404 -> HttpCode.NotFound), HttpCode.valuesToEntriesMap)
    assertEquals(200, HttpCode.Ok.value)
    assertSame(HttpCode.Created, HttpCode.withValue(201))
    assertEquals(Some(HttpCode.NotFound), HttpCode.withValueOpt(404))
    assertEquals(Right(HttpCode.Ok), HttpCode.withValueEither(200))
    assertEquals(None, HttpCode.withValueOpt(500))

    val thrown: NoSuchElementException = assertThrows(
      classOf[NoSuchElementException],
      () => {
        HttpCode.withValue(500)
        ()
      }
    )
    assertTrue(thrown.getMessage.contains("500"))

    HttpCode.withValueEither(500) match {
      case Left(error) =>
        assertEquals(500, error.notFoundValue)
        assertEquals(HttpCode.values, error.enumValues)
        assertEquals(500, error._1)
        assertEquals(HttpCode.values, error._2)
      case Right(value) => fail(s"Expected a missing-value error but found $value")
    }
  }

  @Test
  def valueEnumsSupportByteShortLongCharAndStringEntries(): Unit = {
    assertEquals(IndexedSeq(ByteFlag.One, ByteFlag.Two), ByteFlag.values)
    assertEquals(1.toByte, ByteFlag.One.value)
    assertSame(ByteFlag.Two, ByteFlag.withValue(2.toByte))
    assertEquals(None, ByteFlag.withValueOpt(3.toByte))

    assertEquals(IndexedSeq(ShortPort.Http, ShortPort.Https), ShortPort.values)
    assertEquals(80.toShort, ShortPort.Http.value)
    assertSame(ShortPort.Https, ShortPort.withValue(443.toShort))

    assertEquals(IndexedSeq(LongIdentifier.Primary, LongIdentifier.Secondary), LongIdentifier.values)
    assertEquals(10000000000L, LongIdentifier.Primary.value)
    assertEquals(Right(LongIdentifier.Secondary), LongIdentifier.withValueEither(10000000001L))

    assertEquals(IndexedSeq(CharGrade.A, CharGrade.B), CharGrade.values)
    assertEquals('A', CharGrade.A.value)
    assertSame(CharGrade.B, CharGrade.withValue('B'))

    assertEquals(IndexedSeq(FeatureFlag.Enabled, FeatureFlag.Disabled), FeatureFlag.values)
    assertEquals("enabled", FeatureFlag.Enabled.value)
    assertEquals(Map("enabled" -> FeatureFlag.Enabled, "disabled" -> FeatureFlag.Disabled), FeatureFlag.valuesToEntriesMap)
    assertSame(FeatureFlag.Disabled, FeatureFlag.withValue("disabled"))
    assertFalse(FeatureFlag.withValueEither("unknown").isRight)
  }

  @Test
  def explicitEntryNamesReplaceCaseObjectNamesInEnumLookups(): Unit = {
    assertEquals("scope:read", AccessLevel.ReadOnly.entryName)
    assertEquals("scope:write", AccessLevel.WriteOnly.entryName)
    assertEquals(
      Map("scope:read" -> AccessLevel.ReadOnly, "scope:write" -> AccessLevel.WriteOnly),
      AccessLevel.namesToValuesMap
    )

    assertSame(AccessLevel.ReadOnly, AccessLevel.withName("scope:read"))
    assertSame(AccessLevel.WriteOnly, AccessLevel.withName("scope:write"))
    assertEquals(None, AccessLevel.withNameOption("ReadOnly"))
    assertEquals(None, AccessLevel.withNameOption("WriteOnly"))
  }
}

sealed trait WorkflowState extends EnumEntry
object WorkflowState extends Enum[WorkflowState] {
  val values: IndexedSeq[WorkflowState] = findValues

  case object Submitted extends WorkflowState
  case object InProgress extends WorkflowState
  case object Completed extends WorkflowState
}

sealed trait DocumentState extends EnumEntry
object DocumentState extends Enum[DocumentState] {
  val values: IndexedSeq[DocumentState] = findValues

  override def extraNamesToValuesMap: Map[String, DocumentState] =
    Map("queued" -> Draft, "archived" -> Retired)

  case object Draft extends DocumentState
  case object Published extends DocumentState
  case object Retired extends DocumentState
}

sealed trait NamingStyle extends EnumEntry
object NamingStyle extends Enum[NamingStyle] {
  val values: IndexedSeq[NamingStyle] = findValues

  case object SnakeCaseValue extends NamingStyle with EnumEntry.Snakecase
  case object UpperSnakeCaseValue extends NamingStyle with EnumEntry.UpperSnakecase
  case object CapitalSnakeCaseValue extends NamingStyle with EnumEntry.CapitalSnakecase
  case object HyphenCaseValue extends NamingStyle with EnumEntry.Hyphencase
  case object UpperHyphenCaseValue extends NamingStyle with EnumEntry.UpperHyphencase
  case object CapitalHyphenCaseValue extends NamingStyle with EnumEntry.CapitalHyphencase
  case object DotCaseValue extends NamingStyle with EnumEntry.Dotcase
  case object UpperDotCaseValue extends NamingStyle with EnumEntry.UpperDotcase
  case object CapitalDotCaseValue extends NamingStyle with EnumEntry.CapitalDotcase
  case object WordCaseValue extends NamingStyle with EnumEntry.Words
  case object UpperWordCaseValue extends NamingStyle with EnumEntry.UpperWords
  case object CapitalWordCaseValue extends NamingStyle with EnumEntry.CapitalWords
  case object LowerCamelCaseValue extends NamingStyle with EnumEntry.LowerCamelcase
  case object CamelCaseValue extends NamingStyle with EnumEntry.Camelcase
  case object LowercaseValue extends NamingStyle with EnumEntry.Lowercase
  case object UppercaseValue extends NamingStyle with EnumEntry.Uppercase
  case object UncapitalisedValue extends NamingStyle with EnumEntry.Uncapitalised
}

sealed trait AccessLevel extends EnumEntry
object AccessLevel extends Enum[AccessLevel] {
  val values: IndexedSeq[AccessLevel] = findValues

  case object ReadOnly extends AccessLevel {
    override val entryName: String = "scope:read"
  }

  case object WriteOnly extends AccessLevel {
    override val entryName: String = "scope:write"
  }
}

sealed abstract class HttpCode(val value: Int) extends IntEnumEntry
object HttpCode extends IntEnum[HttpCode] {
  case object Ok extends HttpCode(200)
  case object Created extends HttpCode(201)
  case object NotFound extends HttpCode(404)

  val values: IndexedSeq[HttpCode] = IndexedSeq(Ok, Created, NotFound)
}

sealed abstract class ByteFlag(val value: Byte) extends ByteEnumEntry
object ByteFlag extends ByteEnum[ByteFlag] {
  case object One extends ByteFlag(1.toByte)
  case object Two extends ByteFlag(2.toByte)

  val values: IndexedSeq[ByteFlag] = IndexedSeq(One, Two)
}

sealed abstract class ShortPort(val value: Short) extends ShortEnumEntry
object ShortPort extends ShortEnum[ShortPort] {
  case object Http extends ShortPort(80.toShort)
  case object Https extends ShortPort(443.toShort)

  val values: IndexedSeq[ShortPort] = IndexedSeq(Http, Https)
}

sealed abstract class LongIdentifier(val value: Long) extends LongEnumEntry
object LongIdentifier extends LongEnum[LongIdentifier] {
  case object Primary extends LongIdentifier(10000000000L)
  case object Secondary extends LongIdentifier(10000000001L)

  val values: IndexedSeq[LongIdentifier] = IndexedSeq(Primary, Secondary)
}

sealed abstract class CharGrade(val value: Char) extends CharEnumEntry
object CharGrade extends CharEnum[CharGrade] {
  case object A extends CharGrade('A')
  case object B extends CharGrade('B')

  val values: IndexedSeq[CharGrade] = IndexedSeq(A, B)
}

sealed abstract class FeatureFlag(val value: String) extends StringEnumEntry
object FeatureFlag extends StringEnum[FeatureFlag] {
  case object Enabled extends FeatureFlag("enabled")
  case object Disabled extends FeatureFlag("disabled")

  val values: IndexedSeq[FeatureFlag] = IndexedSeq(Enabled, Disabled)
}
