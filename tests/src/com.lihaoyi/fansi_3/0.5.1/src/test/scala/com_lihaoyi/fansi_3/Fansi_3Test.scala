/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.fansi_3

import fansi.Attr
import fansi.Attrs
import fansi.Back
import fansi.Bold
import fansi.Color
import fansi.ErrorMode
import fansi.Reversed
import fansi.Str
import fansi.Underlined
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Fansi_3Test {
  private val ResetAll: String = "\u001b[0m"
  private val Red: String = "\u001b[31m"
  private val Green: String = "\u001b[32m"
  private val Blue: String = "\u001b[34m"
  private val BlueBackground: String = "\u001b[44m"
  private val BoldOn: String = "\u001b[1m"
  private val UnderlinedOn: String = "\u001b[4m"
  private val ReversedOn: String = "\u001b[7m"
  private val ForegroundReset: String = "\u001b[39m"
  private val BackgroundReset: String = "\u001b[49m"

  @Test
  def plainStringsExposePlainTextCharactersAndDefensiveCopies(): Unit = {
    val text: Str = Str("plain")

    assertEquals(5, text.length)
    assertEquals("plain", text.plainText)
    assertEquals("plain", text.render)
    assertEquals("plain", text.toString)
    assertEquals('p', text.getChar(0))
    assertEquals(0L, text.getColor(0))

    val chars: Array[Char] = text.getChars
    val colors: Array[Long] = text.getColors
    chars(0) = 'X'
    colors(0) = Color.Red.applyMask

    assertEquals('p', text.getChar(0))
    assertEquals(0L, text.getColor(0))
    assertTrue(text.getChars.sameElements(Array('p', 'l', 'a', 'i', 'n')))
    assertTrue(text.getColors.sameElements(Array.fill[Long](5)(0L)))
  }

  @Test
  def fromArraysCopiesInputArraysAndParticipatesInEquality(): Unit = {
    val chars: Array[Char] = Array('o', 'k')
    val colors: Array[Long] = Array(Color.Green.transform(0L), Bold.On.transform(0L))
    val fromArrays: Str = Str.fromArrays(chars, colors)

    chars(0) = 'x'
    colors(0) = 0L

    val rebuilt: Str = Str.fromArrays(Array('o', 'k'), Array(Color.Green.transform(0L), Bold.On.transform(0L)))
    assertEquals("ok", fromArrays.plainText)
    assertEquals(Color.Green.transform(0L), fromArrays.getColor(0))
    assertEquals(Bold.On.transform(0L), fromArrays.getColor(1))
    assertEquals(rebuilt, fromArrays)
    assertEquals(rebuilt.hashCode(), fromArrays.hashCode())
    assertNotEquals(fromArrays, Str("ok"))
    assertNotEquals(fromArrays, "ok")
  }

  @Test
  def individualAndCombinedAttributesRenderAnsiSequences(): Unit = {
    val red: Str = Color.Red(Str("red"))
    val combinedAttributes: Attrs = Color.Green ++ Back.Blue ++ Bold.On ++ Underlined.On ++ Reversed.On
    val combined: Str = combinedAttributes(Str("go"))

    assertEquals(s"${Red}red$ForegroundReset", red.render)
    assertEquals(Color.Red.transform(0L), red.getColor(0))
    assertEquals(
      s"$Green$BlueBackground$BoldOn$UnderlinedOn${ReversedOn}go$ResetAll",
      combined.render
    )
    assertEquals("go", combined.plainText)
    assertEquals(combinedAttributes.transform(0L), combined.getColor(0))
    assertEquals(combinedAttributes.transform(0L), combined.getColor(1))
  }

  @Test
  def colorPalettesAndTrueColorAttributesRoundTripThroughRenderingAndParsing(): Unit = {
    val indexedForeground: Str = Color.Full(196)(Str("hot"))
    val indexedBackground: Str = Back.Full(22)(Str("forest"))
    val trueForeground: Str = Color.True(12, 34, 56)(Str("rgb"))
    val trueBackground: Str = Back.True(0x010203)(Str("back"))

    assertEquals(s"\u001b[38;5;196mhot$ForegroundReset", indexedForeground.render)
    assertEquals(s"\u001b[48;5;22mforest$BackgroundReset", indexedBackground.render)
    assertEquals(s"\u001b[38;2;12;34;56mrgb$ForegroundReset", trueForeground.render)
    assertEquals(s"\u001b[48;2;1;2;3mback$BackgroundReset", trueBackground.render)

    val parsed: Str = Str.Throw("A\u001b[38;2;12;34;56mB\u001b[48;2;1;2;3mC\u001b[0mD")
    assertEquals("ABCD", parsed.plainText)
    assertEquals(0L, parsed.getColor(0))
    assertEquals(Color.True(12, 34, 56).transform(0L), parsed.getColor(1))
    assertEquals(Color.True(12, 34, 56).transform(0L), parsed.getColor(2) & Color.mask)
    assertNotEquals(parsed.getColor(1), parsed.getColor(2))
    assertEquals(0L, parsed.getColor(3))

    assertEquals(0x0c2238, Color.trueIndex(12, 34, 56))
    assertEquals("\u001b[38;2;12;34;56m", Color.trueRgbEscape(12, 34, 56))
    assertEquals("\u001b[48;2;1;2;3m", Back.trueRgbEscape(1, 2, 3))
  }

  @Test
  def ansiParsingSupportsKnownEscapesResetAndErrorModes(): Unit = {
    val raw: String = s"${Red}red$ForegroundReset plain \u001b[48;5;42mbg$ResetAll"
    val parsed: Str = Str.Throw(raw)

    assertEquals("red plain bg", parsed.plainText)
    assertEquals(Color.Red.transform(0L), parsed.getColor(0))
    assertEquals(Color.Red.transform(0L), parsed.getColor(2))
    assertEquals(0L, parsed.getColor(3))
    assertEquals(Back.Full(42).transform(0L), parsed.getColor(parsed.length - 1))

    val unknown: String = "a\u001b[999mb"
    val thrown: IllegalArgumentException = assertThrows(classOf[IllegalArgumentException], () => Str.Throw(unknown))
    assertTrue(thrown.getMessage.contains("Unknown ansi-escape [999m at index 1"))
    assertEquals("a[999mb", Str.Sanitize(unknown).plainText)
    assertEquals("ab", Str.Strip(unknown).plainText)
    assertSame(ErrorMode.Throw, ErrorMode.Throw)
    assertSame(ErrorMode.Sanitize, ErrorMode.Sanitize)
    assertSame(ErrorMode.Strip, ErrorMode.Strip)
    assertTrue(Str.ansiRegex.matcher("before\u001b[999mafter").find())
  }

  @Test
  def concatenationJoiningSplittingAndSubstringsPreserveStyles(): Unit = {
    val red: Str = Color.Red(Str("ab"))
    val blue: Str = Color.Blue(Str("cd"))
    val joined: Str = Str.join(Seq(red, blue), Underlined.On(Str("|")))
    val concatenated: Str = red ++ blue
    val (left, right): (Str, Str) = concatenated.splitAt(2)
    val middle: Str = concatenated.substring(1, 3)

    assertEquals("ab|cd", joined.plainText)
    assertEquals(Color.Red.transform(0L), joined.getColor(0))
    assertEquals(Underlined.On.transform(0L), joined.getColor(2))
    assertEquals(Color.Blue.transform(0L), joined.getColor(3))

    assertEquals("abcd", concatenated.plainText)
    assertEquals(s"${Red}ab$ForegroundReset", left.render)
    assertEquals(s"${Blue}cd$ForegroundReset", right.render)
    assertEquals("bc", middle.plainText)
    assertEquals(Color.Red.transform(0L), middle.getColor(0))
    assertEquals(Color.Blue.transform(0L), middle.getColor(1))
    assertEquals(concatenated, Str(red, blue))
  }

  @Test
  def overlayAndOverlayAllApplyStylesToBoundedRanges(): Unit = {
    val base: Str = Str("abcdef")
    val redMiddle: Str = base.overlay(Color.Red, 1, 4)
    val layered: Str = base.overlayAll(
      Seq(
        (Color.Green, 0, 6),
        (Bold.On, 0, 2),
        (Back.Yellow, 2, 5),
        (Color.Blue, 3, 6),
        (Underlined.On, 5, 6)
      )
    )

    assertEquals(s"a${Red}bcd${ForegroundReset}ef", redMiddle.render)
    assertEquals(0L, redMiddle.getColor(0))
    assertEquals(Color.Red.transform(0L), redMiddle.getColor(1))
    assertEquals(Color.Red.transform(0L), redMiddle.getColor(3))
    assertEquals(0L, redMiddle.getColor(4))

    assertEquals((Color.Green ++ Bold.On).transform(0L), layered.getColor(0))
    assertEquals((Color.Green ++ Back.Yellow).transform(0L), layered.getColor(2))
    assertEquals((Color.Blue ++ Back.Yellow).transform(0L), layered.getColor(3))
    assertEquals((Color.Blue ++ Underlined.On).transform(0L), layered.getColor(5))
    assertEquals("abcdef", layered.plainText)

    assertThrows(classOf[IllegalArgumentException], () => base.overlay(Color.Red, -1, 2))
    assertThrows(classOf[IllegalArgumentException], () => base.overlay(Color.Red, 3, 2))
    assertThrows(classOf[IllegalArgumentException], () => base.overlay(Color.Red, 0, 7))
  }

  @Test
  def attrsUtilitiesExposeCompositionCategoryLookupAndStateTransitions(): Unit = {
    val attrs: Attrs = Attrs(Color.Red, Back.Blue, Bold.On, Color.Green)
    val state: Long = attrs.transform(0L)

    val attrsAsSeq: Seq[Attr] = Attrs.toSeq(attrs)
    assertEquals(3, attrsAsSeq.length)
    assertTrue(attrsAsSeq.contains(Color.Green))
    assertTrue(attrsAsSeq.contains(Back.Blue))
    assertTrue(attrsAsSeq.contains(Bold.On))
    assertFalse(attrsAsSeq.contains(Color.Red))
    assertEquals(state, (Color.Green ++ Back.Blue ++ Bold.On).transform(0L))
    assertEquals(state, Attrs.Empty.transform(state))
    assertFalse(Attrs.Empty == Color.Red)
    assertEquals("Attrs()", Attrs.Empty.toString)
    assertEquals(0L, Attrs.Empty.resetMask)
    assertEquals(0L, Attrs.Empty.applyMask)

    assertEquals(s"$Green$BlueBackground$BoldOn", Attrs.emitAnsiCodes(0L, state))
    assertEquals(s"$ResetAll$Green$BlueBackground", Attrs.emitAnsiCodes(state, (Color.Green ++ Back.Blue).transform(0L)))
    assertEquals(ResetAll, Attrs.emitAnsiCodes(state, 0L))
    assertSame(Color.Green, Color.lookupAttr(Color.Green.applyMask))
    assertSame(Back.Blue, Back.lookupAttr(Back.Blue.applyMask))
    assertEquals(Green, Color.lookupEscape(Color.Green.applyMask))
    assertEquals(BlueBackground, Back.lookupEscape(Back.Blue.applyMask))
    assertEquals("", Bold.lookupEscape(Bold.Off.applyMask))
    assertTrue(Attr.categories.contains(Color))
    assertTrue(Attr.categories.contains(Back))
    assertTrue(Attr.categories.contains(Bold))
    assertTrue(Attr.categories.contains(Underlined))
    assertTrue(Attr.categories.contains(Reversed))
  }

  @Test
  def selectiveResetAttributesClearOnlyMatchingStyleCategories(): Unit = {
    val allStyles: Attrs = Color.Red ++ Back.Blue ++ Bold.On ++ Underlined.On ++ Reversed.On
    val styled: Str = allStyles(Str("x"))
    val withoutForeground: Str = Color.Reset(styled)
    val foregroundOnly: Str = (Back.Reset ++ Bold.Off ++ Underlined.Off ++ Reversed.Off)(styled)
    val plainAgain: Str = Attr.Reset(styled)

    assertEquals((Back.Blue ++ Bold.On ++ Underlined.On ++ Reversed.On).transform(0L), withoutForeground.getColor(0))
    assertEquals(s"$BlueBackground$BoldOn$UnderlinedOn${ReversedOn}x$ResetAll", withoutForeground.render)
    assertEquals(Color.Red.transform(0L), foregroundOnly.getColor(0))
    assertEquals(s"${Red}x$ForegroundReset", foregroundOnly.render)
    assertEquals(0L, plainAgain.getColor(0))
    assertEquals("x", plainAgain.render)
    assertEquals("x", withoutForeground.plainText)
    assertEquals("x", foregroundOnly.plainText)
  }

  @Test
  def renderEmitsAnsiTransitionsForAdjacentPerCharacterStyleChanges(): Unit = {
    val redState: Long = Color.Red.transform(0L)
    val blueState: Long = Color.Blue.transform(0L)
    val blueBackgroundState: Long = (Color.Blue ++ Back.Blue).transform(0L)
    val styled: Str = Str.fromArrays(
      Array('a', 'b', 'c', 'd', 'e'),
      Array(0L, redState, blueState, blueBackgroundState, 0L)
    )

    assertEquals("abcde", styled.plainText)
    assertEquals(
      s"a${Red}b${Blue}c${BlueBackground}d$ForegroundReset${BackgroundReset}e",
      styled.render
    )
    assertEquals(redState, styled.getColor(1))
    assertEquals(blueState, styled.getColor(2))
    assertEquals(blueBackgroundState, styled.getColor(3))
    assertEquals(0L, styled.getColor(4))
  }

  @Test
  def publicAttributeMetadataAndValidationAreAvailable(): Unit = {
    assertEquals("Color.Red", Color.Red.name)
    assertEquals(Some(Red), Color.Red.escapeOpt)
    assertEquals("Bold.Off", Bold.Off.name)
    assertEquals(None, Bold.Off.escapeOpt)
    assertTrue(Color.all.contains(Color.LightMagenta))
    assertTrue(Back.all.contains(Back.LightCyan))
    assertTrue(Bold.all.contains(Bold.On))
    assertTrue(Underlined.all.contains(Underlined.Off))
    assertTrue(Reversed.all.contains(Reversed.Off))

    val trueByIndex: fansi.Attr = Color.True(0x0a0b0c)
    assertEquals("Color.True(10,11,12)", trueByIndex.name)
    assertEquals(Some("\u001b[38;2;10;11;12m"), trueByIndex.escapeOpt)
    assertNotSame(Color.True(0x0a0b0c), trueByIndex)

    assertThrows(classOf[IllegalArgumentException], () => Color.True(-1))
    assertThrows(classOf[IllegalArgumentException], () => Color.True(1 << 24 + 1))
    assertThrows(classOf[IllegalArgumentException], () => Color.True(-1, 0, 0))
    assertThrows(classOf[IllegalArgumentException], () => Color.True(0, 256, 0))
    assertThrows(classOf[IllegalArgumentException], () => Back.True(0, 0, 256))
  }
}
