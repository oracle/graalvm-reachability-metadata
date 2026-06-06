/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_internal_macros_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.internal.ansi._

class Zio_internal_macros_2_13Test {
  @Test
  def ansiCodesAndStringExtensionsApplyExpectedTerminalEscapes(): Unit = {
    assertThat(Reset).isEqualTo("\u001b[0m")
    assertThat(Color.Blue.code).isEqualTo("\u001b[34m")
    assertThat(Color.Cyan.code).isEqualTo("\u001b[36m")
    assertThat(Color.Green.code).isEqualTo("\u001b[32m")
    assertThat(Color.Magenta.code).isEqualTo("\u001b[35m")
    assertThat(Color.Red.code).isEqualTo("\u001b[31m")
    assertThat(Color.Yellow.code).isEqualTo("\u001b[33m")
    assertThat(Style.Bold.code).isEqualTo("\u001b[1m")
    assertThat(Style.Faint.code).isEqualTo("\u001b[2m")
    assertThat(Style.Underlined.code).isEqualTo("\u001b[4m")
    assertThat(Style.Reversed.code).isEqualTo("\u001b[7m")

    assertThat("layer".blue).isEqualTo("\u001b[34mlayer\u001b[0m")
    assertThat("layer".cyan).isEqualTo("\u001b[36mlayer\u001b[0m")
    assertThat("layer".green).isEqualTo("\u001b[32mlayer\u001b[0m")
    assertThat("layer".magenta).isEqualTo("\u001b[35mlayer\u001b[0m")
    assertThat("layer".red).isEqualTo("\u001b[31mlayer\u001b[0m")
    assertThat("layer".yellow).isEqualTo("\u001b[33mlayer\u001b[0m")
    assertThat("layer".bold).isEqualTo("\u001b[1mlayer\u001b[0m")
    assertThat("layer".faint).isEqualTo("\u001b[2mlayer\u001b[0m")
    assertThat("layer".underlined).isEqualTo("\u001b[4mlayer\u001b[0m")
    assertThat("layer".inverted).isEqualTo("\u001b[7mlayer\u001b[0m")
    assertThat("layer".withAnsi(Color.Red)).isEqualTo("layer".red)
  }

  @Test
  def customAnsiCodeCanStyleStringsThroughPublicAnsiCodeTrait(): Unit = {
    val brightPurple: AnsiCode = new AnsiCode {
      override val code: String = "\u001b[95m"
    }

    assertThat("highlight".withAnsi(brightPurple)).isEqualTo("\u001b[95mhighlight\u001b[0m")
  }

  @Test
  def ansiStringExtensionsComposeMultipleStylesInOrder(): Unit = {
    val styledFailure: String = "failure".red.bold.underlined
    val invertedWarning: String = "warning".yellow.inverted

    assertThat(styledFailure).isEqualTo("\u001b[4m\u001b[1m\u001b[31mfailure\u001b[0m\u001b[0m\u001b[0m")
    assertThat(invertedWarning).isEqualTo("\u001b[7m\u001b[33mwarning\u001b[0m\u001b[0m")
  }

  @Test
  def ansiStringOpsCanBeInvokedExplicitly(): Unit = {
    val ops: AnsiStringOps = new AnsiStringOps("explicit")

    assertThat(ops.withAnsi(Style.Bold)).isEqualTo("explicit".bold)
    assertThat(ops.withAnsi(Color.Green)).isEqualTo("explicit".green)
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
  def ansiCodesExposeStableNamesAndProductSemantics(): Unit = {
    assertThat(Color.Blue.productPrefix).isEqualTo("Blue")
    assertThat(Color.Cyan.toString).isEqualTo("Cyan")
    assertThat(Color.Green.productArity).isEqualTo(0)
    assertThat(Style.Bold.productPrefix).isEqualTo("Bold")
    assertThat(Style.Underlined.toString).isEqualTo("Underlined")
    assertThat(Style.Reversed.productArity).isEqualTo(0)
  }
}
