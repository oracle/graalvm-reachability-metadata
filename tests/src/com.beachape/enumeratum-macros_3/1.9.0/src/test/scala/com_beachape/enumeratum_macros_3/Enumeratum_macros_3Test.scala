/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_macros_3

import enumeratum.EnumMacros
import enumeratum.ValueEnumMacros
import enumeratum.values.AllowAlias
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Enumeratum_macros_3Test {
  @Test
  def enumMacrosFindValuesDiscoversCompanionSingletonsInDeclarationOrder(): Unit = {
    val entries: IndexedSeq[MacroSuit] = MacroSuit.values

    assertEquals(IndexedSeq(MacroSuit.Spades, MacroSuit.Hearts, MacroSuit.Clubs), entries)
    assertEquals(IndexedSeq("spades", "hearts", "clubs"), entries.map(_.entryName))
    assertSame(MacroSuit.Spades, entries.head)
  }

  @Test
  def enumMacrosFindValuesWalksNestedSealedHierarchies(): Unit = {
    val entries: IndexedSeq[MacroTransport] = MacroTransport.values

    assertEquals(
      IndexedSeq(MacroTransport.Car, MacroTransport.Bicycle, MacroTransport.Airplane),
      entries
    )
    assertSame(MacroTransport.Car, entries.head)
    assertSame(MacroTransport.Airplane, entries.last)
  }

  @Test
  def enumMacrosFindValuesReturnsAnEmptySequenceForSealedTypesWithoutEntries(): Unit = {
    assertTrue(EmptyMacroEnum.values.isEmpty)
  }

  @Test
  def enumMacrosMaterializeEnumReturnsTheCompanionModule(): Unit = {
    val companion: MacroSuit.type = materializeMacroEnum[MacroSuit, MacroSuit.type]

    assertSame(MacroSuit, companion)
    assertSame(MacroSuit.Hearts, companion.values(1))
  }

  @Test
  def valueEnumMacrosDiscoverLiteralPrimitiveValues(): Unit = {
    assertEquals(IndexedSeq(1, 2, 3), MacroIntCode.values.map(_.value))
    assertEquals(IndexedSeq(10L, 20L), MacroLongCode.values.map(_.value))
    assertEquals(IndexedSeq(7.toShort, 8.toShort), MacroShortCode.values.map(_.value))
    assertEquals(IndexedSeq(4.toByte, 5.toByte), MacroByteCode.values.map(_.value))
    assertEquals(IndexedSeq('A', 'B'), MacroCharCode.values.map(_.value))
  }

  @Test
  def valueEnumMacrosPreserveSingletonInstancesForEachValueType(): Unit = {
    assertSame(MacroIntCode.One, MacroIntCode.values.head)
    assertSame(MacroLongCode.Ten, MacroLongCode.values.head)
    assertSame(MacroShortCode.Seven, MacroShortCode.values.head)
    assertSame(MacroByteCode.Four, MacroByteCode.values.head)
    assertSame(MacroCharCode.A, MacroCharCode.values.head)
  }

  @Test
  def valueEnumMacrosReadNamedConstructorValueArguments(): Unit = {
    val entries: IndexedSeq[MacroHttpStatus] = MacroHttpStatus.values

    assertEquals(IndexedSeq(MacroHttpStatus.Ok, MacroHttpStatus.NotFound), entries)
    assertEquals(IndexedSeq(200, 404), entries.map(_.value))
    assertEquals(IndexedSeq("ok", "not-found"), entries.map(_.label))
    assertSame(MacroHttpStatus.Ok, entries.head)
  }

  @Test
  def valueEnumMacrosAllowDuplicateValuesWhenTheEnumExtendsAllowAlias(): Unit = {
    val entries: IndexedSeq[MacroAliasCode] = MacroAliasCode.values

    assertEquals(IndexedSeq(MacroAliasCode.Primary, MacroAliasCode.AlsoPrimary), entries)
    assertEquals(IndexedSeq("primary", "primary"), entries.map(_.value))
    assertSame(MacroAliasCode.Primary, entries.head)
  }

  @Test
  def valueEnumMacrosExpandNestedSealedValueHierarchies(): Unit = {
    val entries: IndexedSeq[MacroPermission] = MacroPermission.values

    assertEquals(
      IndexedSeq(MacroPermission.ReadArticle, MacroPermission.PublishArticle),
      entries
    )
    assertEquals(IndexedSeq("article:read", "article:publish"), entries.map(_.value))
    assertSame(MacroPermission.ReadArticle, entries.head)
  }
}

inline def findMacroEnumValues[A]: IndexedSeq[A] =
  ${ EnumMacros.findValuesImpl[A] }

inline def materializeMacroEnum[A, M]: M =
  ${ EnumMacros.materializeEnumImpl[A, M] }

inline def findMacroIntValueEntries[A]: IndexedSeq[A] =
  ${ ValueEnumMacros.findIntValueEntriesImpl[A] }

inline def findMacroLongValueEntries[A]: IndexedSeq[A] =
  ${ ValueEnumMacros.findLongValueEntriesImpl[A] }

inline def findMacroShortValueEntries[A]: IndexedSeq[A] =
  ${ ValueEnumMacros.findShortValueEntriesImpl[A] }

inline def findMacroStringValueEntries[A]: IndexedSeq[A] =
  ${ ValueEnumMacros.findStringValueEntriesImpl[A] }

inline def findMacroByteValueEntries[A]: IndexedSeq[A] =
  ${ ValueEnumMacros.findByteValueEntriesImpl[A] }

inline def findMacroCharValueEntries[A]: IndexedSeq[A] =
  ${ ValueEnumMacros.findCharValueEntriesImpl[A] }

sealed abstract class MacroSuit(val entryName: String)

object MacroSuit {
  case object Spades extends MacroSuit("spades")
  case object Hearts extends MacroSuit("hearts")
  case object Clubs extends MacroSuit("clubs")

  val values: IndexedSeq[MacroSuit] = findMacroEnumValues[MacroSuit]
}

sealed trait MacroTransport

object MacroTransport {
  sealed trait Land extends MacroTransport

  case object Car extends Land
  case object Bicycle extends Land
  case object Airplane extends MacroTransport

  val values: IndexedSeq[MacroTransport] = findMacroEnumValues[MacroTransport]
}

sealed trait EmptyMacroEnum

object EmptyMacroEnum {
  val values: IndexedSeq[EmptyMacroEnum] = findMacroEnumValues[EmptyMacroEnum]
}

sealed abstract class MacroIntCode(val value: Int)

object MacroIntCode {
  case object One extends MacroIntCode(1)
  case object Two extends MacroIntCode(2)
  case object Three extends MacroIntCode(3)

  val values: IndexedSeq[MacroIntCode] = findMacroIntValueEntries[MacroIntCode]
}

sealed abstract class MacroLongCode {
  val value: Long
}

object MacroLongCode {
  case object Ten extends MacroLongCode {
    override val value: Long = 10L
  }

  case object Twenty extends MacroLongCode {
    override val value: Long = 20L
  }

  val values: IndexedSeq[MacroLongCode] = findMacroLongValueEntries[MacroLongCode]
}

sealed abstract class MacroShortCode(val value: Short)

object MacroShortCode {
  case object Seven extends MacroShortCode(7)
  case object Eight extends MacroShortCode(8)

  val values: IndexedSeq[MacroShortCode] = findMacroShortValueEntries[MacroShortCode]
}

sealed abstract class MacroByteCode(val value: Byte)

object MacroByteCode {
  case object Four extends MacroByteCode(4)
  case object Five extends MacroByteCode(5)

  val values: IndexedSeq[MacroByteCode] = findMacroByteValueEntries[MacroByteCode]
}

sealed abstract class MacroCharCode(val value: Char)

object MacroCharCode {
  case object A extends MacroCharCode('A')
  case object B extends MacroCharCode('B')

  val values: IndexedSeq[MacroCharCode] = findMacroCharValueEntries[MacroCharCode]
}

sealed abstract class MacroHttpStatus(val value: Int, val label: String)

object MacroHttpStatus {
  case object Ok extends MacroHttpStatus(value = 200, label = "ok")
  case object NotFound extends MacroHttpStatus(value = 404, label = "not-found")

  val values: IndexedSeq[MacroHttpStatus] = findMacroIntValueEntries[MacroHttpStatus]
}

sealed abstract class MacroAliasCode(val value: String) extends AllowAlias

object MacroAliasCode {
  case object Primary extends MacroAliasCode("primary")
  case object AlsoPrimary extends MacroAliasCode("primary")

  val values: IndexedSeq[MacroAliasCode] = findMacroStringValueEntries[MacroAliasCode]
}

sealed trait MacroPermission {
  def value: String
}

object MacroPermission {
  sealed trait ReadPermission extends MacroPermission
  sealed trait PublishPermission extends MacroPermission

  case object ReadArticle extends ReadPermission {
    override val value: String = "article:read"
  }

  case object PublishArticle extends PublishPermission {
    override val value: String = "article:publish"
  }

  val values: IndexedSeq[MacroPermission] = findMacroStringValueEntries[MacroPermission]
}
