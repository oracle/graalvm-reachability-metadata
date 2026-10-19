/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import scala.jdk.CollectionConverters.*

import com.typesafe.config.{Config, ConfigObject, ConfigRenderOptions, ConfigValue}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigConvert, ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure}
import pureconfig.generic.semiauto.{
  deriveConvert,
  deriveEnumerationConvert,
  deriveEnumerationReader,
  deriveEnumerationWriter
}

/**
 * Exercises enumeration derivation: Scala 3 enums and sealed families of case objects in an
 * observability pipeline's settings are read and written as plain strings through
 * `deriveEnumerationReader`, `deriveEnumerationWriter` and `deriveEnumerationConvert`, with the
 * default kebab-case naming and custom name transforms, inside case classes whose converters come
 * from `deriveConvert`, and the failure an operator sees for a value that names no enum case.
 */
class ObservabilityPipelineSettingsTest {
  import ObservabilityPipelineSettingsTest.*
  import ObservabilityPipelineSettingsTest.given

  @Test
  def loadsEnumValuesSpelledTheWayEachTransformExpects(): Unit = {
    val settings: PipelineSettings = ConfigSource.string(PipelineConfig).at("observability").loadOrThrow[PipelineSettings]

    assertEquals(DeploymentStage.CanaryRollout, settings.stage)
    assertEquals(LogLevel.Info, settings.rootLevel)
    assertEquals(
      List(
        LoggerOverride("com.example.checkout", LogLevel.Debug),
        LoggerOverride("org.apache.kafka", LogLevel.Warn)
      ),
      settings.loggers
    )
    assertEquals(
      List(
        Exporter("traces", ExportProtocol.OtlpGrpc, Compression.Gzip, Some(Region.EuWest1)),
        Exporter("metrics", ExportProtocol.PrometheusPull, Compression.NoCompression, None),
        Exporter("logs", ExportProtocol.OtlpHttp, Compression.Zstd, Some(Region.UsEast2))
      ),
      settings.exporters
    )
    assertEquals(Set(Signal.Traces, Signal.Metrics), settings.sampledSignals)
  }

  @Test
  def writesEnumCasesAsStringsThatReloadUnchanged(): Unit = {
    val settings: PipelineSettings = ConfigSource.string(PipelineConfig).at("observability").loadOrThrow[PipelineSettings]
    val rendered: ConfigValue = ConfigWriter[PipelineSettings].to(settings)
    val config: Config = rendered.asInstanceOf[ConfigObject].toConfig

    assertEquals("canary-rollout", config.getString("stage"))
    assertEquals("INFO", config.getString("root-level"))
    assertEquals("DEBUG", config.getConfigList("loggers").get(0).getString("level"))
    val exporters: List[Config] = config.getConfigList("exporters").asScala.toList
    assertEquals(List("otlp-grpc", "prometheus-pull", "otlp-http"), exporters.map(_.getString("protocol")))
    assertEquals(List("gzip", "no-compression", "zstd"), exporters.map(_.getString("compression")))
    assertEquals("eu-west-1", exporters.head.getString("region"))
    assertFalse(exporters(1).hasPath("region"))
    assertEquals(Set("traces", "metrics"), config.getStringList("sampled-signals").asScala.toSet)

    val reloaded: PipelineSettings =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[PipelineSettings]
    assertEquals(settings, reloaded)
  }

  @Test
  def convertsStandaloneEnumValuesInBothDirections(): Unit = {
    assertEquals(Right(Compression.NoCompression), ConfigSource.string("value = no-compression").at("value").load[Compression])
    assertEquals("zstd", ConfigConvert[Compression].to(Compression.Zstd).unwrapped)
    assertEquals(Right(Region.ApSoutheast1), ConfigSource.string("value = ap-southeast-1").at("value").load[Region])
    assertEquals("us-east-2", ConfigWriter[Region].to(Region.UsEast2).unwrapped)
    assertEquals(
      Right(List(LogLevel.Trace, LogLevel.Error)),
      ConfigSource.string("levels = [TRACE, ERROR]").at("levels").load[List[LogLevel]]
    )
  }

  @Test
  def reportsValuesThatNameNoEnumCase(): Unit = {
    val brokenConfig: String = """
      observability {
        stage = "blue-green"
        root-level = "info"
        loggers = []
        exporters = [ { name = "traces", protocol = "otlp-grpc", compression = "brotli" } ]
        sampled-signals = [ "traces", "profiles" ]
      }
      """

    ConfigSource.string(brokenConfig).at("observability").load[PipelineSettings] match {
      case Right(settings) => fail(s"Expected the broken pipeline to be rejected, but loaded $settings")
      case Left(failures) =>
        assertEquals(
          Map(
            "observability.stage" -> "blue-green",
            "observability.root-level" -> "info",
            "observability.exporters.0.compression" -> "brotli",
            "observability.sampled-signals.1" -> "profiles"
          ),
          rejectedValues(failures),
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(CannotConvert("blue-green", "DeploymentStage", _), _, _) => true
            case _ => false
          },
          failures.prettyPrint()
        )
        assertFalse(failurePaths(failures).contains("observability.exporters.0.protocol"), failures.prettyPrint())
    }
  }

  private def rejectedValues(failures: ConfigReaderFailures): Map[String, String] =
    failures.toList.collect { case ConvertFailure(CannotConvert(value, _, _), _, path) => path -> value }.toMap

  private def failurePaths(failures: ConfigReaderFailures): Set[String] =
    failures.toList.collect { case ConvertFailure(_, _, path) => path }.toSet
}

object ObservabilityPipelineSettingsTest {

  /** Rollout stage of the deployment, read with the default kebab-case naming (`canary-rollout`). */
  enum DeploymentStage {
    case Development, CanaryRollout, GeneralAvailability
  }

  given ConfigReader[DeploymentStage] = deriveEnumerationReader[DeploymentStage]
  given ConfigWriter[DeploymentStage] = deriveEnumerationWriter[DeploymentStage]

  private val upperCase: String => String = (caseName: String) => caseName.toUpperCase
  private val lowerCase: String => String = (caseName: String) => caseName.toLowerCase

  /** Logger verbosity, spelled in upper case the way logging frameworks print it. */
  enum LogLevel {
    case Trace, Debug, Info, Warn, Error
  }

  given ConfigReader[LogLevel] = deriveEnumerationReader[LogLevel](upperCase)
  given ConfigWriter[LogLevel] = deriveEnumerationWriter[LogLevel](upperCase)

  /** Wire protocol of an exporter, converted in both directions by one derived converter. */
  enum ExportProtocol {
    case OtlpGrpc, OtlpHttp, PrometheusPull
  }

  given ConfigConvert[ExportProtocol] = deriveEnumerationConvert[ExportProtocol]

  /** Payload compression, a sealed family of case objects rather than a Scala 3 enum. */
  sealed trait Compression
  object Compression {
    case object NoCompression extends Compression
    case object Gzip extends Compression
    case object Zstd extends Compression
  }

  given ConfigConvert[Compression] = deriveEnumerationConvert[Compression]

  /** Cloud region, whose names separate a trailing zone number with a dash (`eu-west-1`). */
  enum Region {
    case EuWest1, UsEast2, ApSoutheast1
  }

  private val regionName: String => String =
    (caseName: String) => caseName.replaceAll("([a-z])([A-Z0-9])", "$1-$2").toLowerCase

  given ConfigConvert[Region] = deriveEnumerationConvert[Region](regionName)

  /** Telemetry signal kind, named in lower case. */
  enum Signal {
    case Traces, Metrics, Logs
  }

  given ConfigConvert[Signal] = deriveEnumerationConvert[Signal](lowerCase)

  final case class LoggerOverride(name: String, level: LogLevel)

  given ConfigConvert[LoggerOverride] = deriveConvert[LoggerOverride]

  final case class Exporter(name: String, protocol: ExportProtocol, compression: Compression, region: Option[Region])

  given ConfigConvert[Exporter] = deriveConvert[Exporter]

  final case class PipelineSettings(
      stage: DeploymentStage,
      rootLevel: LogLevel,
      loggers: List[LoggerOverride],
      exporters: List[Exporter],
      sampledSignals: Set[Signal]
  )

  given ConfigConvert[PipelineSettings] = deriveConvert[PipelineSettings]

  val PipelineConfig: String = """
    observability {
      stage = "canary-rollout"
      root-level = "INFO"
      loggers = [
        { name = "com.example.checkout", level = "DEBUG" }
        { name = "org.apache.kafka", level = "WARN" }
      ]
      exporters = [
        { name = "traces", protocol = "otlp-grpc", compression = "gzip", region = "eu-west-1" }
        { name = "metrics", protocol = "prometheus-pull", compression = "no-compression" }
        { name = "logs", protocol = "otlp-http", compression = "zstd", region = "us-east-2" }
      ]
      sampled-signals = [ "traces", "metrics" ]
    }
    """
}
