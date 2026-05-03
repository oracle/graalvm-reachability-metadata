/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_internal_macros_3

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.internal.TerminalRendering
import zio.internal.TerminalRendering.LayerWiringError
import zio.internal.TerminalRendering.*
import zio.internal.ansi.*

class Zio_internal_macros_3Test {
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
  def terminalStringExtensionsIndentAndCenterMultilineText(): Unit = {
    assertThat("dependency".center(2)).isEqualTo("  dependency  ")
    val indented: String = "first\nsecond".indent(3)

    assertThat(indented).startsWith("   first\n   second")
    assertThat(indented.trim).isEqualTo("first\n   second")
    assertThat("".indent(2)).isEmpty()
  }

  @Test
  def pluralizationHelpersUseSingularOnlyForOne(): Unit = {
    assertThat(TerminalRendering.pluralize(0, "type", "types")).isEqualTo("0 types")
    assertThat(TerminalRendering.pluralize(1, "type", "types")).isEqualTo("type")
    assertThat(TerminalRendering.pluralize(2, "type", "types")).isEqualTo("2 types")
    assertThat(TerminalRendering.pluralizeLayers(1)).isEqualTo("layer")
    assertThat(TerminalRendering.pluralizeLayers(3)).isEqualTo("3 layers")
    assertThat(TerminalRendering.pluralizeTypes(1)).isEqualTo("type")
    assertThat(TerminalRendering.pluralizeTypes(4)).isEqualTo("4 types")
  }

  @Test
  def missingLayerRendererIncludesTopLevelTransitiveAndProvideSomeGuidance(): Unit = {
    val rendered: String = TerminalRendering.missingLayersError(
      toplevel = List("zio.Clock", "example.Database"),
      transitive = Map("UserService.live" -> List("zio.Console", "example.Repository")),
      isUsingProvideSome = true
    )

    assertThat(rendered).contains("ZLAYER ERROR")
    assertThat(rendered).contains("Please provide layers for the following")
    assertThat(rendered).contains("zio.Clock")
    assertThat(rendered).contains("example.Database")
    assertThat(rendered).contains("Required by")
    assertThat(rendered).contains("UserService.live")
    assertThat(rendered).contains("zio.Console")
    assertThat(rendered).contains("example.Repository")
    assertThat(rendered).contains("Alternatively, you may add them to the remainder type ascription")
    assertThat(rendered).contains("provideSome")
  }

  @Test
  def missingLayerRendererCanOmitProvideSomeGuidanceAndUseDefaults(): Unit = {
    val withoutProvideSome: String = TerminalRendering.missingLayersError(
      toplevel = List("zio.Clock"),
      transitive = Map.empty,
      isUsingProvideSome = false
    )
    val withDefaults: String = TerminalRendering.missingLayersError(List("zio.Random"))

    assertThat(withoutProvideSome).contains("Please provide a layer for the following type")
    assertThat(withoutProvideSome).contains("zio.Clock")
    assertThat(withoutProvideSome).doesNotContain("remainder type ascription")
    assertThat(withDefaults).contains("zio.Random")
    assertThat(withDefaults).contains("provideSome")
  }

  @Test
  def customMissingLayerRenderersDescribeZioAppAndSpecFailures(): Unit = {
    val appError: String = TerminalRendering.missingLayersForZIOApp(Set("zio.Console"))
    val specError: String = TerminalRendering.missingLayersForZIOSpec(Set("zio.Clock", "zio.Random"))
    val customError: String = TerminalRendering.missingLayersCustomError(Set("example.Service"), "workflow", "CUSTOM ERROR")

    assertThat(appError).contains("ZIO APP ERROR")
    assertThat(appError).contains("Your effect requires a service that is not in the environment")
    assertThat(appError).contains("zio.Console")
    assertThat(appError).contains("Call your effect's")

    assertThat(specError).contains("ZIO SPEC ERROR")
    assertThat(specError).contains("Your suite requires services that are not in the environment")
    assertThat(specError).contains("zio.Clock")
    assertThat(specError).contains("zio.Random")

    assertThat(customError).contains("CUSTOM ERROR")
    assertThat(customError).contains("Your workflow requires a service")
    assertThat(customError).contains("example.Service")
  }

  @Test
  def warningRenderersReportUnusedLayersAndUnnecessaryProvideCalls(): Unit = {
    val unusedLayers: String = TerminalRendering.unusedLayersError(List("Clock.live", "Console.test"))
    val unusedProvideSomeTypes: String = TerminalRendering.unusedProvideSomeLayersError(List("java.lang.String"))
    val provideSomeNothingEnv: String = TerminalRendering.provideSomeNothingEnvError
    val superfluousProvideCustom: String = TerminalRendering.superfluousProvideCustomError

    assertThat(unusedLayers).contains("ZLAYER WARNING")
    assertThat(unusedLayers).contains("You have provided more than is required")
    assertThat(unusedLayers).contains("Clock.live")
    assertThat(unusedLayers).contains("Console.test")
    assertThat(unusedLayers).contains("2 layers")

    assertThat(unusedProvideSomeTypes).contains("provideSome")
    assertThat(unusedProvideSomeTypes).contains("java.lang.String")
    assertThat(unusedProvideSomeTypes).contains("type")

    assertThat(provideSomeNothingEnv).contains("You are using")
    assertThat(provideSomeNothingEnv).contains("provideSome")
    assertThat(provideSomeNothingEnv).contains("Simply use")
    assertThat(provideSomeNothingEnv).contains("provide")

    assertThat(superfluousProvideCustom).contains("provideCustom")
    assertThat(superfluousProvideCustom).contains("None of the default services are required")
  }

  @Test
  def ambiguousAndCircularLayerRenderersDescribeConflictingWiring(): Unit = {
    val ambiguous: String = TerminalRendering.ambiguousLayersError(
      List(
        "java.lang.String" -> List("firstStringLayer", "secondStringLayer"),
        "example.Service" -> List("serviceLayerA", "serviceLayerB")
      )
    )
    val circular: String = TerminalRendering.circularityError(List("Database.live" -> "UserService.live"))

    assertThat(ambiguous).contains("ZLAYER ERROR")
    assertThat(ambiguous).contains("Ambiguous layers! I cannot decide which to use")
    assertThat(ambiguous).contains("following")
    assertThat(ambiguous).contains("java.lang.String")
    assertThat(ambiguous).contains("firstStringLayer")
    assertThat(ambiguous).contains("secondStringLayer")
    assertThat(ambiguous).contains("example.Service")
    assertThat(ambiguous).contains("serviceLayerA")
    assertThat(ambiguous).contains("serviceLayerB")

    assertThat(circular).contains("Circular Dependency Detected")
    assertThat(circular).contains("Database.live")
    assertThat(circular).contains("UserService.live")
    assertThat(circular).contains("simultaneously requires and is required by another")
  }

  @Test
  def ambiguousLayerRendererUsesSingularWordingForOneAmbiguousType(): Unit = {
    val rendered: String = TerminalRendering.ambiguousLayersError(
      List("example.Repository" -> List("primaryRepositoryLayer", "fallbackRepositoryLayer"))
    )
    val plainText: String = removeAnsiEscapes(rendered)

    assertThat(plainText).contains("Ambiguous layers! I cannot decide which to use.")
    assertThat(plainText).contains("You have provided more than one layer for the following type:")
    assertThat(plainText).doesNotContain("following 1 types")
    assertThat(plainText).contains("example.Repository is provided by:")
    assertThat(plainText).contains("1. primaryRepositoryLayer")
    assertThat(plainText).contains("2. fallbackRepositoryLayer")
  }

  @Test
  def byNameParameterRendererShowsOriginalSignatureAndLambdaWorkaround(): Unit = {
    val rendered: String = TerminalRendering.byNameParameterInMacroError(
      method = "createLayerByName",
      fullMethodSignature = "def createLayerByName(i: Int, x: => MyLayer): zio.ULayer[MyLayer]",
      byNameParameters = Seq("x: => MyLayer")
    )

    assertThat(rendered).contains("ZLAYER ERROR")
    assertThat(rendered).contains("Scala 2 compiler")
    assertThat(rendered).contains("createLayerByName")
    assertThat(rendered).contains("x: => MyLayer")
    assertThat(rendered).contains("x: () => MyLayer")
    assertThat(rendered).contains("val temp = createLayerByName(...)")
  }

  @Test
  def byNameParameterRendererRewritesEveryByNameParameterInSignature(): Unit = {
    val fullMethodSignature: String = "def combine(left: => LeftLayer, right: => RightLayer): zio.ULayer[CombinedLayer]"
    val rendered: String = TerminalRendering.byNameParameterInMacroError(
      method = "combine",
      fullMethodSignature = fullMethodSignature,
      byNameParameters = Seq("left: => LeftLayer", "right: => RightLayer")
    )
    val plainText: String = removeAnsiEscapes(rendered)

    assertThat(plainText).contains(fullMethodSignature)
    assertThat(plainText).contains(
      "def combine(left: () => LeftLayer, right: () => RightLayer): zio.ULayer[CombinedLayer]"
    )
    assertThat(plainText).contains("ZLayer.provide(combine(...))")
    assertThat(plainText).contains("val temp = combine(...)")
  }

  @Test
  def layerWiringErrorCaseClassesExposeProductAndCopySemantics(): Unit = {
    val missingTopLevel: LayerWiringError.MissingTopLevel = LayerWiringError.MissingTopLevel("zio.Clock")
    val missingTransitive: LayerWiringError.MissingTransitive =
      LayerWiringError.MissingTransitive("UserService.live", List("zio.Console", "zio.Random"))
    val circular: LayerWiringError.Circular = LayerWiringError.Circular("Database.live", "UserService.live")

    assertThat(missingTopLevel.layer).isEqualTo("zio.Clock")
    assertThat(missingTopLevel.copy(layer = "zio.Random")).isEqualTo(LayerWiringError.MissingTopLevel("zio.Random"))
    assertThat(missingTopLevel.productPrefix).isEqualTo("MissingTopLevel")
    assertThat(missingTopLevel.productElement(0)).isEqualTo("zio.Clock")

    assertThat(missingTransitive.layer).isEqualTo("UserService.live")
    assertThat(missingTransitive.deps).isEqualTo(List("zio.Console", "zio.Random"))
    assertThat(missingTransitive.copy(deps = List("zio.Clock"))).isEqualTo(
      LayerWiringError.MissingTransitive("UserService.live", List("zio.Clock"))
    )
    assertThat(missingTransitive.productElementName(1)).isEqualTo("deps")

    assertThat(circular.layer).isEqualTo("Database.live")
    assertThat(circular.dependency).isEqualTo("UserService.live")
    assertThat(circular.copy(dependency = "Repository.live")).isEqualTo(
      LayerWiringError.Circular("Database.live", "Repository.live")
    )
    assertThat(circular.toString).contains("Circular")
  }

  @Test
  def terminalRenderingMainPrintsSampleDiagnostics(): Unit = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()

    Console.withOut(output) {
      TerminalRendering.main(Array.empty)
    }

    val rendered: String = output.toString(StandardCharsets.UTF_8)
    assertThat(rendered).contains("Clock")
    assertThat(rendered).contains("Database")
    assertThat(rendered).contains("ZLAYER ERROR")
    assertThat(rendered).contains("ZLAYER WARNING")
    assertThat(rendered).contains("Ambiguous layers! I cannot decide which to use")
    assertThat(rendered).contains("createLayerByName")
    assertThat(rendered).contains("ZLayer.provide(temp)")
  }

  @Test
  def ansiCodeSingletonsAndLayerWiringErrorsCanBePatternMatched(): Unit = {
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
    val wiringDescriptions: List[String] = List(
      LayerWiringError.MissingTopLevel("A"),
      LayerWiringError.MissingTransitive("B", List("C", "D")),
      LayerWiringError.Circular("E", "F")
    ).map {
      case LayerWiringError.MissingTopLevel(layer)       => s"missing:$layer"
      case LayerWiringError.MissingTransitive(layer, ds) => s"transitive:$layer:${ds.mkString(",")}"
      case LayerWiringError.Circular(layer, dependency)  => s"circular:$layer:$dependency"
    }

    assertThat(colorNames).isEqualTo(List("blue", "cyan", "green", "magenta", "red", "yellow"))
    assertThat(styleNames).isEqualTo(List("bold", "faint", "underlined", "reversed"))
    assertThat(wiringDescriptions).isEqualTo(List("missing:A", "transitive:B:C,D", "circular:E:F"))
  }

  private def removeAnsiEscapes(value: String): String =
    value.replaceAll("\u001b\\[[;\\d]*m", "")
}
