/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_geirsson.metaconfig_typesafe_config_2_13

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import metaconfig.Conf
import metaconfig.ConfDecoder
import metaconfig.Configured
import metaconfig.Hocon
import metaconfig.Input
import metaconfig.MetaconfigParser
import metaconfig.Position
import metaconfig.typesafeconfig.TypesafeConfig2Class
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Metaconfig_typesafe_config_2_13Test {
  @Test
  def convertsResolvedHoconIntoMetaconfigTree(): Unit = {
    val hocon: String =
      """|defaults {
         |  host = "localhost"
         |  enabled = true
         |  retries = 3
         |  limit = 9223372036854775806
         |  ratio = 0.5
         |  nothing = null
         |  tags = ["api", 7, false]
         |}
         |service = ${defaults}
         |service.host = "example.com"
         |service.tags += "blue"
         |""".stripMargin

    val obtained: Conf = configuredValue(TypesafeConfig2Class.gimmeConfFromString(hocon))
    val defaults: Conf = Conf.Obj(
      "host" -> Conf.Str("localhost"),
      "enabled" -> Conf.Bool(true),
      "retries" -> Conf.Num(3),
      "limit" -> Conf.Num(BigDecimal("9223372036854775806")),
      "ratio" -> Conf.Num(BigDecimal("0.5")),
      "nothing" -> Conf.Null(),
      "tags" -> Conf.Lst(Conf.Str("api"), Conf.Num(7), Conf.Bool(false))
    )
    val expected: Conf = Conf.Obj(
      "defaults" -> defaults,
      "service" -> Conf.Obj(
        "host" -> Conf.Str("example.com"),
        "enabled" -> Conf.Bool(true),
        "retries" -> Conf.Num(3),
        "limit" -> Conf.Num(BigDecimal("9223372036854775806")),
        "ratio" -> Conf.Num(BigDecimal("0.5")),
        "nothing" -> Conf.Null(),
        "tags" -> Conf.Lst(Conf.Str("blue"))
      )
    )

    assertEquals(expected, obtained)
  }

  @Test
  def hoconFacadeParsesStringsVirtualFilesFilesAndDecoders(): Unit = {
    val fromString: Conf.Obj = asObj(configuredValue(Hocon.fromString("answer = 42")))
    assertEquals(Conf.Num(42), fromString.field("answer").get)

    val virtualFile: Conf.Obj =
      asObj(configuredValue(Hocon.fromString("virtual.conf", "name = test\n")))
    val virtualName: Conf = virtualFile.field("name").get
    assertEquals(Conf.Str("test"), virtualName)
    assertEquals("name = test", normalizedLineContent(virtualName.pos))

    val file: Path = Files.createTempFile("metaconfig-hocon-facade", ".conf")
    Files.write(file, "feature.enabled = true\n".getBytes(StandardCharsets.UTF_8))
    val fromFile: Conf = configuredValue(Hocon.fromFile(file))
    val fromInput: Conf = configuredValue(Hocon.fromInput(Input.File(file)))
    assertEquals(fromFile, fromInput)
    assertEquals(true, configuredValue(fromFile.getNested[Boolean]("feature", "enabled")))

    val portsDecoder: ConfDecoder[List[Int]] = ConfDecoder.from(_.get[List[Int]]("ports"))
    val ports: List[Int] =
      configuredValue(Hocon.parseString[List[Int]]("ports = [8080, 8443]")(portsDecoder))
    assertEquals(List(8080, 8443), ports)

    val flagsDecoder: ConfDecoder[List[Boolean]] = ConfDecoder.from(_.get[List[Boolean]]("flags"))
    val flags: List[Boolean] = configuredValue(
      Hocon.parseFilename[List[Boolean]]("flags.conf", "flags = [true, false]")(flagsDecoder)
    )
    assertEquals(List(true, false), flags)

    val directionsDecoder: ConfDecoder[List[String]] = ConfDecoder.from(_.get[List[String]]("directions"))
    val directions: List[String] = configuredValue(
      Hocon.parseInput[List[String]](Input.String("directions = [west, east]"))(directionsDecoder)
    )
    assertEquals(List("west", "east"), directions)
  }

  @Test
  def typesafeConfigEntrypointsHandleIncludesConfigObjectsAndPositions(): Unit = {
    val directory: Path = Files.createTempDirectory("metaconfig-include")
    val mainFile: Path = directory.resolve("main.conf")
    val includedFile: Path = directory.resolve("included.conf")

    Files.write(
      includedFile,
      """|list = ${list} ["two", 3, true]
         |included-value = from-include
         |""".stripMargin.getBytes(StandardCharsets.UTF_8)
    )
    Files.write(
      mainFile,
      """|list = [1]
         |include "included.conf"
         |main-value = from-main
         |""".stripMargin.getBytes(StandardCharsets.UTF_8)
    )

    val included: Conf.Obj = asObj(configuredValue(TypesafeConfig2Class.gimmeConfFromFile(mainFile.toFile)))
    val expected: Conf = Conf.Obj(
      "list" -> Conf.Lst(Conf.Num(1), Conf.Str("two"), Conf.Num(3), Conf.Bool(true)),
      "included-value" -> Conf.Str("from-include"),
      "main-value" -> Conf.Str("from-main")
    )
    assertEquals(expected, included)
    assertEquals(Position.None, included.field("list").get.pos)
    assertEquals("included-value = from-include", normalizedLineContent(included.field("included-value").get.pos))
    assertEquals("main-value = from-main", normalizedLineContent(included.field("main-value").get.pos))

    val config: Config = ConfigFactory.parseString("alpha = 1\nnested.beta = two\n")
    assertThrows(classOf[IllegalArgumentException], () => TypesafeConfig2Class.gimmeConf(config))

    val named: Conf.Obj = asObj(
      configuredValue(TypesafeConfig2Class.gimmeConfFromStringFilename("named.conf", "named.value = 5\n"))
    )
    assertEquals(5, configuredValue(named.getNested[Int]("named", "value")))
    assertEquals("named.value = 5", normalizedLineContent(named.field("named").get.pos))
  }

  @Test
  def reportsInvalidInputsAsConfiguredErrors(): Unit = {
    val missingFile: Path = Files.createTempFile("metaconfig-missing", ".conf")
    assertTrue(Files.deleteIfExists(missingFile))
    assertFalse(TypesafeConfig2Class.gimmeConfFromFile(missingFile.toFile).isOk)

    val directory: Path = Files.createTempDirectory("metaconfig-directory")
    assertTrue(TypesafeConfig2Class.gimmeConfFromFile(directory.toFile).isNotOk)

  }

  @Test
  def packageParserIntegratesWithCoreMetaconfigApi(): Unit = {
    implicit val parser: MetaconfigParser = metaconfig.typesafeconfig.typesafeConfigMetaconfigParser
    assertSame(Hocon, parser)

    val parsed: Conf = configuredValue(
      Conf.parseString(
        """|nested.value = 123
           |items = [alpha, beta]
           |""".stripMargin
      )
    )
    assertEquals(123, configuredValue(parsed.getNested[Int]("nested", "value")))
    assertEquals(List("alpha", "beta"), configuredValue(parsed.get[List[String]]("items")))

    val fromVirtualInput: Conf.Obj = asObj(
      configuredValue(Conf.parseInput(Input.VirtualFile("core-api.conf", "enabled = true")))
    )
    assertEquals(Some(true), configuredValue(fromVirtualInput.getOption[Boolean]("enabled")))
    assertEquals(None, configuredValue(fromVirtualInput.getOption[String]("missing")))
  }

  private def configuredValue[A](configured: Configured[A]): A = {
    assertTrue(configured.isOk, configured.toString)
    configured.get
  }

  private def asObj(conf: Conf): Conf.Obj =
    conf match {
      case obj: Conf.Obj => obj
      case other => throw new AssertionError(s"Expected Conf.Obj, obtained ${other.kind}: $other")
    }

  private def normalizedLineContent(position: Position): String =
    position.lineContent.replace("\r", "").replace("\n", "")
}
