/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_geirsson.metaconfig_core_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import metaconfig.Conf
import metaconfig.ConfCodec
import metaconfig.ConfDecoder
import metaconfig.ConfDecoderEx
import metaconfig.ConfDecoderExT
import metaconfig.ConfDynamic
import metaconfig.ConfEncoder
import metaconfig.ConfError
import metaconfig.Configured
import metaconfig.Input
import metaconfig.Position
import metaconfig.annotation.Description
import metaconfig.annotation.Dynamic
import metaconfig.annotation.ExampleUsage
import metaconfig.annotation.ExtraName
import metaconfig.annotation.Hidden
import metaconfig.annotation.Inline
import metaconfig.annotation.Usage
import metaconfig.cli.CliApp
import metaconfig.cli.Command
import metaconfig.cli.HelpCommand
import metaconfig.cli.TabCompleteCommand
import metaconfig.cli.TabCompleteOptions
import metaconfig.generic.Settings
import metaconfig.generic.Surface
import metaconfig.generic.deriveCodec
import metaconfig.generic.deriveSurface
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

class Metaconfig_core_2_13Test {

  import Metaconfig_core_2_13Test._

  @Test
  def confValuesCanBeNormalizedPatchedTraversedAndPrinted(): Unit = {
    val original: Conf = Conf.Obj(
      "alpha" -> Conf.Obj("first" -> Conf.fromInt(1)),
      "enabled" -> Conf.fromBoolean(false),
      "items" -> Conf.fromList(List(Conf.fromString("one")))
    )
    val revised: Conf = Conf.Obj(
      "alpha" -> Conf.Obj("first" -> Conf.fromInt(1), "second" -> Conf.fromInt(2)),
      "enabled" -> Conf.fromBoolean(true),
      "items" -> Conf.fromList(List(Conf.fromString("one"), Conf.fromString("two")))
    )

    val patch: Conf = Conf.patch(original, revised)
    val patched: Conf = Conf.applyPatch(original, patch)
    assertThat(patched).isEqualTo(revised)

    val dotted: Conf = Conf.Obj(
      "z" -> Conf.fromString("last"),
      "alpha.beta" -> Conf.fromNumberOrString("42"),
      "alpha.gamma" -> Conf.fromBoolean(true)
    )
    val normalized: Conf = dotted.normalize
    assertThat(normalized.getNested[Int]("alpha", "beta").get).isEqualTo(42)
    assertThat(normalized.getNested[Boolean]("alpha", "gamma").get).isTrue()
    assertThat(normalized.asInstanceOf[Conf.Obj].keys).isEqualTo(List("alpha", "z"))

    val visitedKinds = List.newBuilder[String]
    normalized.foreach(conf => visitedKinds += conf.kind)
    assertThat(visitedKinds.result().asJava).containsExactly("Map[K, V]", "Map[K, V]", "Number", "Boolean", "String")

    val hocon: String = Conf.printHocon(revised)
    assertThat(hocon).contains("alpha.first = 1")
    assertThat(hocon).contains("enabled = true")
    assertThat(hocon).contains("items = [")
  }

  @Test
  def dynamicConfNavigationReadsNestedFieldsAndReportsMissingSelections(): Unit = {
    val conf: Conf = Conf.Obj(
      "database" -> Conf.Obj(
        "host" -> Conf.fromString("localhost"),
        "pool" -> Conf.Obj("size" -> Conf.fromInt(8))
      ),
      "features" -> Conf.Obj("http2" -> Conf.fromBoolean(true))
    )

    val dynamic: ConfDynamic = conf.dynamic
    val host: Configured[String] = dynamic.selectDynamic("database").selectDynamic("host").as[String]
    val poolSize: Configured[Int] =
      dynamic.selectDynamic("database").selectDynamic("pool").selectDynamic("size").as[Int]
    val http2: Configured[Boolean] = dynamic.selectDynamic("features").selectDynamic("http2").as[Boolean]

    assertThat(host.get).isEqualTo("localhost")
    assertThat(poolSize.get).isEqualTo(8)
    assertThat(http2.get).isTrue()

    val selected: Configured[Conf] = dynamic.selectDynamic("database").selectDynamic("pool").asConf
    assertThat(selected.isOk).isTrue()
    assertThat(selected.get).isEqualTo(Conf.Obj("size" -> Conf.fromInt(8)))

    val missing: Configured[String] = dynamic.selectDynamic("database").selectDynamic("missing").as[String]
    assertThat(missing.isNotOk).isTrue()
    val Left(error) = missing.toEither
    assertThat(error.isMissingField).isTrue()
  }

  @Test
  def encodersAndDecodersRoundTripPrimitiveCollectionAndPathValues(): Unit = {
    val path: Path = Paths.get("config", "application.conf")
    val encoded: Conf = ConfEncoder[ListMap[String, Option[List[Path]]]].write(
      ListMap(
        "paths" -> Some(List(path)),
        "missing" -> None
      )
    )

    val decoded: Configured[ListMap[String, Option[List[Path]]]] = encoded.as[ListMap[String, Option[List[Path]]]]
    assertThat(decoded.isOk).isTrue()
    val decodedMap: ListMap[String, Option[List[Path]]] = decoded.get
    assertThat(decodedMap("paths").get).isEqualTo(List(path))
    assertThat(decodedMap("missing")).isEqualTo(None)

    val right: Either[Int, String] = Conf.Str("value").as[Either[Int, String]].get
    val left: Either[Int, String] = Conf.Str("123").as[Either[Int, String]].get
    assertThat(right).isEqualTo(Right("value"))
    assertThat(left).isEqualTo(Left(123))

    val nonEmptyStringDecoder: ConfDecoder[String] = ConfDecoder.stringConfDecoder.flatMap { value =>
      if (value.nonEmpty) Configured.ok(value)
      else Configured.error("empty string is not allowed")
    }
    val fallbackDecoder: ConfDecoder[String] = nonEmptyStringDecoder.orElse(ConfDecoder.constant("fallback"))
    assertThat(Conf.Str("").as(fallbackDecoder).get).isEqualTo("fallback")
    assertThat(Conf.Str("actual").as(fallbackDecoder).get).isEqualTo("actual")

    val upperCaseEncoder: ConfEncoder[String] = ConfEncoder.StringEncoder.contramap[String](_.toUpperCase)
    assertThat(upperCaseEncoder.write("lower")).isEqualTo(Conf.Str("LOWER"))
  }

  @Test
  def extendedDecodersApplyIncrementalCollectionUpdatesAgainstExistingState(): Unit = {
    val baseItems: List[String] = List("base")
    val itemUpdate: Conf = Conf.Obj("+" -> Conf.Lst(Conf.Str("extra"), Conf.Str("last")))
    val listDecoder: ConfDecoderEx[List[String]] = ConfDecoderExT.canBuildSeq[String, List]

    val appendedItems: Configured[List[String]] = itemUpdate.getEx(Some(baseItems))(listDecoder)
    assertThat(appendedItems.get.asJava).containsExactly("base", "extra", "last")

    val replacementItems: Configured[List[String]] =
      Conf.Lst(Conf.Str("replacement")).getEx(Some(baseItems))(listDecoder)
    assertThat(replacementItems.get.asJava).containsExactly("replacement")

    val baseAliases: ListMap[String, Int] = ListMap("existing" -> 1)
    val aliasUpdate: Conf = Conf.Obj(
      "+" -> Conf.Obj(
        "new" -> Conf.fromInt(2),
        "other" -> Conf.fromNumberOrString("3")
      )
    )
    val mapDecoder: ConfDecoderEx[ListMap[String, Int]] = ConfDecoderExT.canBuildStringMap[Int, ListMap]

    val appendedAliases: Configured[ListMap[String, Int]] =
      aliasUpdate.getEx(Some(baseAliases))(mapDecoder)
    assertThat(appendedAliases.get).isEqualTo(
      ListMap("existing" -> 1, "new" -> 2, "other" -> 3)
    )

    val root: Conf = Conf.Obj("items" -> itemUpdate)
    val updatedFromPath: Configured[List[String]] = Conf.getEx(baseItems, root, Seq("items"))(listDecoder)
    assertThat(updatedFromPath.get.asJava).containsExactly("base", "extra", "last")

    val unchangedWhenMissing: Configured[List[String]] = Conf.getEx(baseItems, root, Seq("missing"))(listDecoder)
    assertThat(unchangedWhenMissing.get.asJava).containsExactly("base")
  }

  @Test
  def configuredValuesAndErrorsExposeSuccessFailureAndDiagnosticMetadata(): Unit = {
    val success: Configured[Int] = Configured.ok(21).map(_ * 2)
    assertThat(success.isOk).isTrue()
    assertThat(success.get).isEqualTo(42)
    assertThat(success.toEither).isEqualTo(Right(42))

    val missing: ConfError = ConfError.missingField(
      Conf.Obj(
        "known" -> Conf.Str("value"),
        "other" -> Conf.Str("value")
      ),
      "knwon"
    )
    val mismatch: ConfError = ConfError.typeMismatch("Number", Conf.Str("not-a-number"))
    assertThat(missing.isMissingField).isTrue()
    assertThat(mismatch.isTypeMismatch).isTrue()
    val combined: Configured[(Nothing, Nothing)] = Configured.notOk(missing).product(Configured.notOk(mismatch))
    assertThat(combined.isNotOk).isTrue()
    val Left(combinedError) = combined.toEither
    assertThat(combinedError.all.size).isEqualTo(2)
    assertThat(combinedError.toString).contains("Did you mean 'known' instead?")

    val invalid: ConfError = ConfError.invalidFields(List("verbsoe"), List("verbose", "name"))
    assertThat(invalid.toString).contains("found option 'verbsoe'")
    assertThat(invalid.toString).contains("Did you mean 'verbose'?")

    val captured: Configured[Int] = Configured.fromExceptionThrowing("abc".toInt)
    assertThat(captured.isNotOk).isTrue()
    val Left(capturedError) = captured.toEither
    assertThat(capturedError.isException).isTrue()

    val recovered: Int = Configured.error("boom").getOrElse(7)
    assertThat(recovered).isEqualTo(7)
  }

  @Test
  def inputAndPositionTrackOffsetsLinesCaretsAndAttachedConfPositions(): Unit = {
    val input: Input = Input.VirtualFile("example.conf", "first\nsecond\nthird")
    assertThat(input.lineToOffset(1)).isEqualTo(6)
    assertThat(input.offsetToLine(8)).isEqualTo(1)

    val position: Position = Position.Range(input, 7, 10)
    assertThat(position.startLine).isEqualTo(1)
    assertThat(position.startColumn).isEqualTo(1)
    assertThat(position.text).isEqualTo("eco")
    assertThat(position.lineInput("error", "bad value")).isEqualTo("example.conf:2:1 error: bad value")
    assertThat(position.lineContent).isEqualTo("second")
    assertThat(position.lineCaret).startsWith(" ^")
    assertThat(position.pretty("warn", "check this")).contains("example.conf:2:1 warn: check this")

    val enclosing: Position = Position.Range(input, 6, 12)
    assertThat(enclosing.encloses(position)).isTrue()

    val withPosition: Conf = Conf.Str("bad").withPos(position)
    val errorAtPosition: ConfError = ConfError.typeMismatch("Number", withPosition)
    assertThat(errorAtPosition.hasPos).isTrue()
    assertThat(errorAtPosition.toString).contains("example.conf:2:1 error: Type mismatch")

    val tempFile: Path = Files.createTempFile("metaconfig-input", ".conf")
    try {
      Files.write(tempFile, "file-text".getBytes(StandardCharsets.UTF_8))
      val fileInput: Input = Input.File(tempFile)
      assertThat(fileInput.path).isEqualTo(tempFile.toString)
      assertThat(fileInput.text).isEqualTo("file-text")
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  @Test
  def derivedSettingsCodecAndCliArgumentParsingHonorAnnotationsAndDefaults(): Unit = {
    val parsedConf: Conf = Conf.parseCliArgs[ExampleOptions](
      List(
        "--verbose",
        "--n=alice",
        "--items",
        "one",
        "--items=two",
        "--level",
        "7",
        "--extras.region",
        "emea",
        "--path",
        "config.conf"
      )
    ).get
    val decoded: ExampleOptions = parsedConf.as[ExampleOptions].get

    assertThat(decoded).isEqualTo(
      ExampleOptions(
        verbose = true,
        name = "alice",
        items = List("one", "two"),
        nested = NestedOptions(level = 7),
        extras = ListMap("region" -> "emea"),
        secret = "hidden-default",
        path = Paths.get("config.conf")
      )
    )

    val negated: ExampleOptions = Conf.parseCliArgs[ExampleOptions](List("--no-verbose")).get.as[ExampleOptions].get
    assertThat(negated.verbose).isFalse()

    val encoded: Conf.Obj = ConfEncoder[ExampleOptions].writeObj(decoded)
    assertThat(encoded.field("name")).isEqualTo(Some(Conf.Str("alice")))
    assertThat(encoded.field("nested").get.asInstanceOf[Conf.Obj].field("level")).isEqualTo(Some(Conf.Num(7)))

    val settings: Settings[ExampleOptions] = Settings[ExampleOptions]
    assertThat(settings.names.asJava).contains("verbose", "name", "items", "nested", "extras", "secret", "path")
    assertThat(settings.nonHiddenNames.asJava).doesNotContain("secret")
    assertThat(settings.unsafeGet("name").description).isEqualTo(Some("Display name"))
    assertThat(settings.unsafeGet("name").alternativeNames.asJava).contains("n")
    assertThat(settings.unsafeGet("verbose").isBoolean).isTrue()
    assertThat(settings.unsafeGet("items").isRepeated).isTrue()
    assertThat(settings.unsafeGet("nested").underlying.get.names.asJava).contains("level")
    assertThat(settings.unsafeGet("extras").isDynamic).isTrue()
    assertThat(settings.unsafeGet("path").isTabCompleteAsPath).isTrue()
    assertThat(settings.cliDescription.get.render(80)).contains("Run the example command")
    assertThat(settings.cliUsage.get.render(80)).contains("example [OPTIONS]")
    assertThat(settings.cliExamples.map(_.render(80)).asJava).contains("example --verbose")
  }

  @Test
  def cliAppRunsCommandsReportsHelpAndProvidesTabCompletions(): Unit = {
    val outBuffer = new ByteArrayOutputStream()
    val errBuffer = new ByteArrayOutputStream()
    val out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8.name())
    val err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8.name())
    val tempDirectory: Path = Files.createTempDirectory("metaconfig-complete")
    try {
      Files.createFile(tempDirectory.resolve("alpha.conf"))
      Files.createDirectory(tempDirectory.resolve("nested"))

      val checkCommand: Command[ExampleOptions] = new Command[ExampleOptions]("check") {
        override def run(value: ExampleOptions, app: CliApp): Int = {
          app.out.println(s"checked ${value.name} verbose=${value.verbose}")
          0
        }
      }
      val app: CliApp = CliApp(
        version = "test-version",
        binaryName = "example",
        commands = List(checkCommand, HelpCommand, TabCompleteCommand),
        out = out,
        err = err,
        in = new ByteArrayInputStream(Array.emptyByteArray),
        workingDirectory = tempDirectory,
        environmentVariables = Map.empty
      )

      assertThat(app.run(List("check", "--verbose", "--n", "bob"))).isEqualTo(0)
      assertThat(outBuffer.toString(StandardCharsets.UTF_8.name())).contains("checked bob verbose=true")

      outBuffer.reset()
      assertThat(app.run(List("help", "check"))).isEqualTo(0)
      val commandHelp: String = outBuffer.toString(StandardCharsets.UTF_8.name())
      assertThat(commandHelp).contains("USAGE:")
      assertThat(commandHelp).contains("example [OPTIONS]")
      assertThat(commandHelp).contains("Run the example command")

      assertThat(app.run(List("chek"))).isEqualTo(1)
      assertThat(errBuffer.toString(StandardCharsets.UTF_8.name())).contains("Did you mean 'example check'?")

      outBuffer.reset()
      TabCompleteCommand.run(TabCompleteOptions(arguments = List("example", "")), app)
      assertThat(outBuffer.toString(StandardCharsets.UTF_8.name())).contains("check")

      outBuffer.reset()
      TabCompleteCommand.run(TabCompleteOptions(arguments = List("example", "check", "--")), app)
      val flagCompletions: String = outBuffer.toString(StandardCharsets.UTF_8.name())
      assertThat(flagCompletions).contains("--verbose")
      assertThat(flagCompletions).contains("--path")
      assertThat(flagCompletions).doesNotContain("--secret")

      outBuffer.reset()
      TabCompleteCommand.run(TabCompleteOptions(arguments = List("example", "check", "--path", "")), app)
      val pathCompletions: String = outBuffer.toString(StandardCharsets.UTF_8.name())
      assertThat(pathCompletions).contains("alpha.conf")
      assertThat(pathCompletions).contains("nested")
    } finally {
      out.close()
      err.close()
      val paths = Files.walk(tempDirectory)
      try {
        paths.sorted(java.util.Comparator.reverseOrder[Path]()).forEach { path: Path =>
          Files.deleteIfExists(path)
          ()
        }
      } finally {
        paths.close()
      }
    }
  }
}

object Metaconfig_core_2_13Test {
  final case class NestedOptions(level: Int = 1)

  @Description("Run the example command")
  @Usage("example [OPTIONS]")
  @ExampleUsage("example --verbose")
  final case class ExampleOptions(
      verbose: Boolean = false,
      @Description("Display name") @ExtraName("n") name: String = "default",
      items: List[String] = Nil,
      @Inline nested: NestedOptions = NestedOptions(),
      @Dynamic extras: ListMap[String, String] = ListMap.empty,
      @Hidden secret: String = "hidden-default",
      path: Path = Paths.get("default.conf")
  )

  implicit val nestedSurface: Surface[NestedOptions] = deriveSurface[NestedOptions]
  implicit val nestedCodec: ConfCodec[NestedOptions] = deriveCodec[NestedOptions](NestedOptions())
  implicit val exampleSurface: Surface[ExampleOptions] = deriveSurface[ExampleOptions]
  implicit val exampleCodec: ConfCodec[ExampleOptions] = deriveCodec[ExampleOptions](ExampleOptions())
}
