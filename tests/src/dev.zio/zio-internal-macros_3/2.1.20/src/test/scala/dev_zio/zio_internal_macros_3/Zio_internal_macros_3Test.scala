/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_internal_macros_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.internal.ansi.*

class Zio_internal_macros_3Test {
  @Test
  def ansiCodesExposeExpectedTerminalEscapeSequences(): Unit = {
    val colors: List[Color] = List(Color.Blue, Color.Cyan, Color.Green, Color.Magenta, Color.Red, Color.Yellow)
    val styles: List[Style] = List(Style.Bold, Style.Faint, Style.Underlined, Style.Reversed)
    val colorCodes: List[String] = colors.map(_.code)
    val styleCodes: List[String] = styles.map(_.code)

    assertThat(Reset).isEqualTo("\u001b[0m")
    assertThat(colorCodes).isEqualTo(
      List("\u001b[34m", "\u001b[36m", "\u001b[32m", "\u001b[35m", "\u001b[31m", "\u001b[33m")
    )
    assertThat(styleCodes).isEqualTo(
      List("\u001b[1m", "\u001b[2m", "\u001b[4m", "\u001b[7m")
    )
  }

  @Test
  def ansiStringExtensionsWrapTextWithColorsAndResetCode(): Unit = {
    assertThat("layer".blue).isEqualTo("\u001b[34mlayer\u001b[0m")
    assertThat("layer".cyan).isEqualTo("\u001b[36mlayer\u001b[0m")
    assertThat("layer".green).isEqualTo("\u001b[32mlayer\u001b[0m")
    assertThat("layer".magenta).isEqualTo("\u001b[35mlayer\u001b[0m")
    assertThat("layer".red).isEqualTo("\u001b[31mlayer\u001b[0m")
    assertThat("layer".yellow).isEqualTo("\u001b[33mlayer\u001b[0m")
    assertThat("layer".withAnsi(Color.Red)).isEqualTo("layer".red)
  }

  @Test
  def ansiStringExtensionsWrapTextWithStylesAndResetCode(): Unit = {
    assertThat("layer".bold).isEqualTo("\u001b[1mlayer\u001b[0m")
    assertThat("layer".faint).isEqualTo("\u001b[2mlayer\u001b[0m")
    assertThat("layer".underlined).isEqualTo("\u001b[4mlayer\u001b[0m")
    assertThat("layer".inverted).isEqualTo("\u001b[7mlayer\u001b[0m")
    assertThat("layer".withAnsi(Style.Bold)).isEqualTo("layer".bold)
  }

  @Test
  def customAnsiCodeCanStyleStringsThroughPublicAnsiCodeTrait(): Unit = {
    val brightPurple: AnsiCode = new AnsiCode {
      override val code: String = "\u001b[95m"
    }

    assertThat("highlight".withAnsi(brightPurple)).isEqualTo("\u001b[95mhighlight\u001b[0m")
  }

  @Test
  def ansiCodeSingletonsCanBePatternMatched(): Unit = {
    val colorNames: List[String] = List(Color.Blue, Color.Cyan, Color.Green, Color.Magenta, Color.Red, Color.Yellow).map {
      case Color.Blue    => "blue"
      case Color.Cyan    => "cyan"
      case Color.Green   => "green"
      case Color.Magenta => "magenta"
      case Color.Red     => "red"
      case Color.Yellow  => "yellow"
    }
    val styleNames: List[String] = List(Style.Bold, Style.Faint, Style.Underlined, Style.Reversed).map {
      case Style.Bold       => "bold"
      case Style.Faint      => "faint"
      case Style.Underlined => "underlined"
      case Style.Reversed   => "reversed"
    }

    assertThat(colorNames).isEqualTo(List("blue", "cyan", "green", "magenta", "red", "yellow"))
    assertThat(styleNames).isEqualTo(List("bold", "faint", "underlined", "reversed"))
  }

  @Test
  def ansiStringOpsCanApplyMultipleCodesInSequence(): Unit = {
    val rendered: String = "dependency".red.bold.underlined

    assertThat(rendered).isEqualTo(
      "\u001b[4m\u001b[1m\u001b[31mdependency\u001b[0m\u001b[0m\u001b[0m"
    )
    assertThat(rendered).contains("dependency")
    assertThat(rendered).endsWith(Reset + Reset + Reset)
  }
}
