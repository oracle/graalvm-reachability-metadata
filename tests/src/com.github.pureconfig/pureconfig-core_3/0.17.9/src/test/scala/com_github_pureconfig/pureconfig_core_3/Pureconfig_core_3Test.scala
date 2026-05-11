/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_core_3

import java.net.{URI, URL}
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import scala.concurrent.duration.*

import com.typesafe.config.{ConfigMemorySize, ConfigRenderOptions}
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import pureconfig.*
import pureconfig.error.*
import pureconfig.generic.derivation.EnumConfigConvert
import pureconfig.syntax.*

class Pureconfig_core_3Test {
  import Pureconfig_core_3Test.*
  import Pureconfig_core_3Test.given

  @Test
  def loadsNestedDerivedProductsAndCollectionsFromHocon(): Unit = {
    val result: ConfigReader.Result[ApplicationConfig] = ConfigSource
      .string(
        """
          |app {
          |  service-name = "catalog"
          |  endpoint {
          |    host = "127.0.0.1"
          |    port = 8080
          |    enabled = true
          |    labels = ["edge", "blue"]
          |    connect-timeout = "250 millis"
          |    retries = null
          |  }
          |  percentages = ["12.5%", 0.25]
          |  limits {
          |    users = 100
          |    jobs-per-node = 4
          |  }
          |  aliases = ["primary", "read-only"]
          |}
          |""".stripMargin
      )
      .at("app")
      .load[ApplicationConfig]

    assertRight(
      ApplicationConfig(
        serviceName = "catalog",
        endpoint = Endpoint(
          host = "127.0.0.1",
          port = 8080,
          enabled = true,
          labels = List("edge", "blue"),
          connectTimeout = 250.millis,
          retries = None
        ),
        percentages = Vector(0.125d, 0.25d),
        limits = Map("users" -> 100, "jobs-per-node" -> 4),
        aliases = Set("primary", "read-only")
      ),
      result
    )
  }

  @Test
  def derivesSealedTraitReadersWithTypeDiscriminator(): Unit = {
    val result: ConfigReader.Result[RetryPolicy] = ConfigSource
      .string(
        """
          |type = "exponential-backoff"
          |initial-delay = "100 millis"
          |max-delay = "2 seconds"
          |multiplier = 2.5
          |""".stripMargin
      )
      .load[RetryPolicy]

    assertRight(
      RetryPolicy.ExponentialBackoff(
        initialDelay = 100.millis,
        maxDelay = 2.seconds,
        multiplier = 2.5d
      ),
      result
    )

    ConfigSource.string("type = \"linear\"").load[RetryPolicy] match {
      case Left(failures) =>
        failures.head match {
          case ConvertFailure(CannotConvert(value, toType, because), _, path) =>
            assertEquals("linear", value)
            assertEquals("RetryPolicy", toType)
            assertEquals("The value is not a valid option.", because)
            assertEquals("type", path)
          case other =>
            throw new AssertionError(s"Expected an invalid coproduct option failure, but got: $other")
        }
      case Right(policy) =>
        throw new AssertionError(s"Expected invalid retry policy to fail, but loaded: $policy")
    }
  }

  @Test
  def mergesFileSourcesSupportsOptionalFilesAndReportsReadFailures(): Unit = {
    val tempDirectory: Path = Files.createTempDirectory("pureconfig-core")
    val fallbackFile: Path = tempDirectory.resolve("fallback.conf")
    val overrideFile: Path = tempDirectory.resolve("override.conf")
    val missingFile: Path = tempDirectory.resolve("missing.conf")

    try {
      Files.writeString(
        fallbackFile,
        """
          |app {
          |  service-name = "fallback-service"
          |  endpoint {
          |    host = "fallback.example.test"
          |    port = 8080
          |    enabled = false
          |    labels = ["fallback"]
          |    connect-timeout = "1 second"
          |  }
          |  percentages = [0.5]
          |  limits {
          |    users = 100
          |    jobs-per-node = 2
          |  }
          |  aliases = ["fallback"]
          |}
          |""".stripMargin
      )
      Files.writeString(
        overrideFile,
        """
          |app {
          |  endpoint {
          |    port = 9090
          |    labels = ["override"]
          |  }
          |  limits.users = 250
          |}
          |""".stripMargin
      )

      val mergedResult: ConfigReader.Result[ApplicationConfig] = ConfigSource
        .file(overrideFile)
        .withFallback(ConfigSource.file(fallbackFile))
        .at("app")
        .load[ApplicationConfig]

      assertRight(
        ApplicationConfig(
          serviceName = "fallback-service",
          endpoint = Endpoint(
            host = "fallback.example.test",
            port = 9090,
            enabled = false,
            labels = List("override"),
            connectTimeout = 1.second,
            retries = None
          ),
          percentages = Vector(0.5d),
          limits = Map("users" -> 250, "jobs-per-node" -> 2),
          aliases = Set("fallback")
        ),
        mergedResult
      )

      val optionalResult: ConfigReader.Result[ApplicationConfig] = ConfigSource
        .file(missingFile)
        .optional
        .withFallback(ConfigSource.file(fallbackFile))
        .at("app")
        .load[ApplicationConfig]

      assertEquals("fallback-service", optionalResult.toOption.get.serviceName)

      ConfigSource.file(missingFile).config() match {
        case Left(failures) =>
          assertTrue(failures.head.isInstanceOf[CannotReadFile], failures.prettyPrint())
        case Right(config) =>
          throw new AssertionError(s"Expected a missing file failure, but read: $config")
      }
    } finally {
      Files.deleteIfExists(overrideFile)
      Files.deleteIfExists(fallbackFile)
      Files.deleteIfExists(tempDirectory)
    }
  }

  @Test
  def composesCustomReadersForValidationFallbackAndZipping(): Unit = {
    given ConfigReader[Port] = ConfigReader[Int]
      .ensure(value => value > 0 && value <= 65535, value => s"port $value is out of range")
      .map(Port.apply)

    given ConfigReader[FeatureFlag] = ConfigReader[Boolean]
      .map(FeatureFlag.apply)
      .orElse(
        ConfigReader.fromString[FeatureFlag] {
          case "enabled" | "on" => Right(FeatureFlag(true))
          case "disabled" | "off" => Right(FeatureFlag(false))
          case other => Left(CannotConvert(other, "FeatureFlag", "expected enabled, disabled, on, or off"))
        }
      )

    val source: ConfigObjectSource = ConfigSource.string(
      """
        |valid-port = 8443
        |invalid-port = 70000
        |flag-from-string = "enabled"
        |flag-from-boolean = false
        |name = "pure"
        |""".stripMargin
    )

    assertRight(Port(8443), source.at("valid-port").load[Port])
    assertRight(FeatureFlag(true), source.at("flag-from-string").load[FeatureFlag])
    assertRight(FeatureFlag(false), source.at("flag-from-boolean").load[FeatureFlag])

    val nameSummaryReader: ConfigReader[(String, Int)] = ConfigReader[String]
      .map(_.toUpperCase)
      .zip(ConfigReader[String].map(_.length))
    assertRight(("PURE", 4), source.at("name").load[(String, Int)](using nameSummaryReader))

    source.at("invalid-port").load[Port] match {
      case Left(failures) =>
        failures.head match {
          case ConvertFailure(UserValidationFailed(message), _, path) =>
            assertEquals("port 70000 is out of range", message)
            assertEquals("invalid-port", path)
          case other =>
            throw new AssertionError(s"Expected a validation failure, but got: $other")
        }
      case Right(port) =>
        throw new AssertionError(s"Expected invalid port to fail, but loaded: $port")
    }
  }

  @Test
  def writesProductsEnumsAndOptionsThenReadsThemBack(): Unit = {
    val database: Database = Database("db.example.test", 5432, AccessMode.ReadWrite, None)
    val configValue = database.toConfig
    val rendered: String = configValue.render(ConfigRenderOptions.concise())

    assertTrue(rendered.contains("read-write"), rendered)
    assertFalse(rendered.contains("comment"), rendered)
    assertRight(database, configValue.to[Database])

    val tempFile: Path = Files.createTempFile("pureconfig-database", ".conf")
    try {
      saveConfigAsPropertyFile(database, tempFile, overrideOutputPath = true, options = ConfigRenderOptions.defaults())
      assertRight(database, ConfigSource.file(tempFile).load[Database])
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  @Test
  def readsSystemPropertiesAsConfigSource(): Unit = {
    val root: String = s"pureconfig.core.test.id${UUID.randomUUID().toString.replace("-", "")}"
    val propertyValues: Map[String, String] = Map(
      s"$root.service.name" -> "catalog",
      s"$root.service.owner" -> "platform"
    )
    val previousValues: Map[String, Option[String]] = propertyValues.keys
      .map(key => key -> Option(System.getProperty(key)))
      .toMap

    try {
      propertyValues.foreach { case (key, value) => System.setProperty(key, value) }

      assertRight(
        Map("name" -> "catalog", "owner" -> "platform"),
        ConfigSource.systemProperties.at(s"$root.service").load[Map[String, String]]
      )
    } finally {
      previousValues.foreach {
        case (key, Some(value)) => System.setProperty(key, value)
        case (key, None) => System.clearProperty(key)
      }
    }
  }

  @Test
  def navigatesWithFluentCursorsAndReadsStandardLibraryTypes(): Unit = {
    val source: ConfigObjectSource = ConfigSource.string(
      """
        |servers = [
        |  { name = "alpha", port = 8080 },
        |  { name = "beta", port = 9090 }
        |]
        |metadata {
        |  env = "test"
        |  owner = "native-image"
        |}
        |standard {
        |  url = "https://example.test/config"
        |  uri = "urn:pureconfig:test"
        |  path = "/tmp/pureconfig"
        |  instant = "2024-01-02T03:04:05Z"
        |  chrono-unit = "half-days"
        |  uuid = "123e4567-e89b-12d3-a456-426614174000"
        |  memory = "64K"
        |}
        |""".stripMargin
    )

    val cursor: FluentConfigCursor = source.fluentCursor()
    assertEquals(
      Right("beta"),
      cursor.at(PathSegment.Key("servers"), PathSegment.Index(1), PathSegment.Key("name")).asString
    )
    assertEquals(
      Right(List("alpha", "beta")),
      cursor.at(PathSegment.Key("servers")).mapList(_.fluent.at(PathSegment.Key("name")).asString)
    )
    assertEquals(
      Right(Map("env" -> "test", "owner" -> "native-image")),
      cursor.at(PathSegment.Key("metadata")).mapObject(_.asString)
    )

    val standardTypes: StandardTypes = source.at("standard").loadOrThrow[StandardTypes]
    assertEquals(new URL("https://example.test/config"), standardTypes.url)
    assertEquals(new URI("urn:pureconfig:test"), standardTypes.uri)
    assertEquals(Paths.get("/tmp/pureconfig"), standardTypes.path)
    assertEquals(Instant.parse("2024-01-02T03:04:05Z"), standardTypes.instant)
    assertEquals(ChronoUnit.HALF_DAYS, standardTypes.chronoUnit)
    assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), standardTypes.uuid)
    assertEquals(65536L, standardTypes.memory.toBytes.longValue())
  }

  private def assertRight[A](expected: A, actual: ConfigReader.Result[A]): Unit = {
    actual match {
      case Right(value) => assertEquals(expected, value)
      case Left(failures) => throw new AssertionError(s"Expected Right($expected), but got: ${failures.prettyPrint()}")
    }
  }
}

object Pureconfig_core_3Test {
  final case class Endpoint(
      host: String,
      port: Int,
      enabled: Boolean,
      labels: List[String],
      connectTimeout: FiniteDuration,
      retries: Option[Int]
  ) derives ConfigReader

  final case class ApplicationConfig(
      serviceName: String,
      endpoint: Endpoint,
      percentages: Vector[Double],
      limits: Map[String, Int],
      aliases: Set[String]
  ) derives ConfigReader

  final case class Port(value: Int)

  final case class FeatureFlag(enabled: Boolean)

  sealed trait RetryPolicy derives ConfigReader

  object RetryPolicy {
    final case class FixedDelay(delay: FiniteDuration, attempts: Int) extends RetryPolicy derives ConfigReader

    final case class ExponentialBackoff(initialDelay: FiniteDuration, maxDelay: FiniteDuration, multiplier: Double)
        extends RetryPolicy
        derives ConfigReader
  }

  enum AccessMode derives EnumConfigConvert {
    case ReadOnly, ReadWrite
  }

  final case class Database(host: String, port: Int, accessMode: AccessMode, comment: Option[String])

  given ConfigReader[Database] =
    ConfigReader.forProduct4("host", "port", "access-mode", "comment")(Database.apply)

  given ConfigWriter[Database] =
    ConfigWriter.forProduct4("host", "port", "access-mode", "comment")(database =>
      (database.host, database.port, database.accessMode, database.comment)
    )

  final case class StandardTypes(
      url: URL,
      uri: URI,
      path: Path,
      instant: Instant,
      chronoUnit: ChronoUnit,
      uuid: UUID,
      memory: ConfigMemorySize
  ) derives ConfigReader
}
