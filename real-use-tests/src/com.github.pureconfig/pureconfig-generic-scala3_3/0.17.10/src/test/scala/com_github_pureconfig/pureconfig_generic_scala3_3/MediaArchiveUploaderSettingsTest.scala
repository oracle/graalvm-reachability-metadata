/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import java.io.File
import java.net.URI
import java.nio.file.{Path, Paths}
import java.time.{Instant, Period, Year, ZoneId, ZoneOffset}
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

import com.typesafe.config.{Config, ConfigMemorySize, ConfigObject, ConfigRenderOptions, ConfigValue}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{
  CannotConvert,
  ConfigReaderFailures,
  ConvertFailure,
  FailureReason,
  KeyNotFound,
  WrongSizeString,
  WrongType
}
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises product derivation over pureconfig's built-in leaf types: an object-storage archiver's
 * derived settings hold UUIDs, URIs, file-system paths, java.time values, Java enums, memory sizes,
 * regular expressions, big numbers, an infinite duration and raw Typesafe Config blocks, which the
 * derived reader converts from their HOCON spellings, the derived writer renders back and the
 * reader reloads unchanged, while malformed spellings are reported at their field paths.
 */
class MediaArchiveUploaderSettingsTest {
  import MediaArchiveUploaderSettingsTest.*
  import MediaArchiveUploaderSettingsTest.given

  @Test
  def convertsBuiltInLeafTypesInDerivedFields(): Unit = {
    val settings: ArchiverSettings = ConfigSource.string(ArchiverConfig).at("archiver").loadOrThrow[ArchiverSettings]

    assertEquals(UUID.fromString("3f1c2b9e-7d4a-4c1e-9b2f-0a6d5e8c7b11"), settings.instanceId)
    assertEquals(URI.create("https://objects.eu-central.example.net:9443/media"), settings.endpoint)
    assertEquals("objects.eu-central.example.net", settings.endpoint.getHost)
    assertEquals(9443, settings.endpoint.getPort)
    assertEquals(Paths.get("/var/spool/archiver/staging"), settings.stagingDirectory)
    assertEquals(new File("/etc/archiver/credentials.json"), settings.credentialsFile)
    assertEquals(ZoneId.of("Europe/Berlin"), settings.schedule.timezone)
    assertEquals(ZoneOffset.ofHours(-5), settings.schedule.reportingOffset)
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), settings.schedule.startAfter)
    assertEquals(Period.ofDays(90), settings.schedule.retention)
    assertEquals(ChronoUnit.DAYS, settings.schedule.retentionGranularity)
    assertEquals(Year.of(2026), settings.schedule.fiscalYear)
    assertEquals(java.time.Duration.ofSeconds(45), settings.upload.timeout)
    assertEquals(15.seconds, settings.upload.pollInterval)
    assertEquals(Duration.Inf, settings.upload.maxIdle)
    assertEquals(ConfigMemorySize.ofBytes(16L * 1024 * 1024), settings.upload.partSize)
    assertEquals(BigInt("5497558138880"), settings.upload.maxObjectBytes)
    assertEquals(BigDecimal("0.0235"), settings.upload.costPerGigabyte)
    assertEquals(TimeUnit.MILLISECONDS, settings.upload.clockUnit)
    assertEquals('/', settings.upload.keyDelimiter)
    assertEquals(Set("eu-central-1", "eu-west-1"), settings.upload.allowedRegions)

    assertTrue(settings.filter.accept.matches("raw/2026/clip-0042.mov"))
    assertFalse(settings.filter.accept.matches("raw/2026/notes.txt"))
    assertTrue(settings.filter.reject.matcher("raw/tmp/clip-0042.mov").find())
    assertFalse(settings.filter.reject.matcher("raw/2026/clip-0042.mov").find())
    assertEquals(Some("clip-0042"), settings.filter.accept.findFirstMatchIn("raw/2026/clip-0042.mov").map(_.group(1)))

    assertEquals("media-team", settings.objectMetadata.toConfig.getString("owner"))
    assertEquals(3, settings.objectMetadata.toConfig.getInt("replicas"))
    assertEquals(List("public", "cdn"), settings.objectMetadata.toConfig.getStringList("labels").asScala.toList)
    assertEquals(true, settings.vendorExtension.unwrapped)
    assertEquals(None, settings.previousInstanceId)
  }

  @Test
  def rendersBuiltInLeafTypesAndReloadsThemUnchanged(): Unit = {
    val settings: ArchiverSettings = ConfigSource.string(ArchiverConfig).at("archiver").loadOrThrow[ArchiverSettings]
    val rendered: ConfigObject = ConfigWriter[ArchiverSettings].to(settings).asInstanceOf[ConfigObject]
    val config: Config = rendered.toConfig

    assertEquals("3f1c2b9e-7d4a-4c1e-9b2f-0a6d5e8c7b11", config.getString("instance-id"))
    assertEquals("https://objects.eu-central.example.net:9443/media", config.getString("endpoint"))
    assertEquals("/var/spool/archiver/staging", config.getString("staging-directory"))
    assertEquals("Europe/Berlin", config.getString("schedule.timezone"))
    assertEquals("2026-01-01T00:00:00Z", config.getString("schedule.start-after"))
    assertEquals("P90D", config.getString("schedule.retention"))
    assertEquals("PT45S", config.getString("upload.timeout"))
    assertEquals(16L * 1024 * 1024, config.getBytes("upload.part-size"))
    assertEquals("MILLISECONDS", config.getString("upload.clock-unit"))
    assertEquals("""raw/\d{4}/(clip-\d+)\.(mov|mxf)""", config.getString("filter.accept"))
    assertEquals("media-team", config.getString("object-metadata.owner"))
    assertFalse(config.hasPath("previous-instance-id"))

    val reloaded: ArchiverSettings =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[ArchiverSettings]
    assertEquals(settings.copy(filter = reloaded.filter), reloaded)
    assertEquals(settings.filter.accept.regex, reloaded.filter.accept.regex)
    assertEquals(settings.filter.reject.pattern, reloaded.filter.reject.pattern)
    assertEquals(settings.filter.reject.flags, reloaded.filter.reject.flags)

    val successor: ArchiverSettings = settings.copy(
      previousInstanceId = Some(settings.instanceId),
      instanceId = UUID.fromString("8a0e4f6d-1b2c-4d3e-8f90-123456789abc")
    )
    val successorConfig: Config = ConfigWriter[ArchiverSettings].to(successor).asInstanceOf[ConfigObject].toConfig
    assertEquals("3f1c2b9e-7d4a-4c1e-9b2f-0a6d5e8c7b11", successorConfig.getString("previous-instance-id"))
    assertEquals(
      Right(successor.previousInstanceId),
      ConfigSource.fromConfig(successorConfig).load[ArchiverSettings].map(_.previousInstanceId)
    )
  }

  @Test
  def reportsMalformedLeafValuesAtTheirFieldPaths(): Unit = {
    val brokenConfig: String = """
      archiver {
        instance-id = "not-a-uuid"
        endpoint = "https://objects example net/media"
        staging-directory = "/var/spool/archiver/staging"
        credentials-file = "/etc/archiver/credentials.json"
        schedule {
          timezone = "Mars/Olympus_Mons"
          reporting-offset = "-05:00"
          start-after = "yesterday"
          retention = "90 days"
          retention-granularity = DAYS
          fiscal-year = 2026
        }
        upload {
          timeout = "PT45S"
          poll-interval = 15 seconds
          max-idle = Inf
          part-size = "sixteen megabytes"
          max-object-bytes = 5497558138880
          cost-per-gigabyte = 0.0235
          clock-unit = FORTNIGHTS
          key-delimiter = "//"
          allowed-regions = ["eu-central-1"]
        }
        filter {
          accept = "raw/(unclosed"
          reject = "/tmp/"
        }
        object-metadata = "owner=media-team"
        vendor-extension = true
      }
      """

    ConfigSource.string(brokenConfig).at("archiver").load[ArchiverSettings] match {
      case Right(settings) => fail(s"Expected the malformed archiver settings to be rejected, but loaded $settings")
      case Left(failures) =>
        val reasons: Map[String, FailureReason] = failureReasons(failures)
        assertEquals(
          Set(
            "archiver.instance-id",
            "archiver.endpoint",
            "archiver.schedule.timezone",
            "archiver.schedule.start-after",
            "archiver.upload.part-size",
            "archiver.upload.clock-unit",
            "archiver.upload.key-delimiter",
            "archiver.filter.accept",
            "archiver.object-metadata"
          ),
          reasons.keySet,
          failures.prettyPrint()
        )
        val cannotConvert: Set[String] = reasons.keySet --
          Set("archiver.object-metadata", "archiver.upload.key-delimiter", "archiver.upload.part-size")
        cannotConvert.foreach { path =>
          assertTrue(reasons(path).isInstanceOf[CannotConvert], s"$path: ${failures.prettyPrint()}")
        }
        assertTrue(reasons("archiver.object-metadata").isInstanceOf[WrongType], failures.prettyPrint())
        assertTrue(reasons("archiver.upload.part-size").description.contains("'sixteen'"), failures.prettyPrint())
        assertEquals(WrongSizeString(1, 2), reasons("archiver.upload.key-delimiter"), failures.prettyPrint())
        reasons("archiver.upload.clock-unit") match {
          case CannotConvert(value, _, _) => assertEquals("FORTNIGHTS", value)
          case other => fail(s"Unexpected clock-unit failure $other")
        }
    }

    ConfigSource.string("archiver { instance-id = \"3f1c2b9e-7d4a-4c1e-9b2f-0a6d5e8c7b11\" }")
      .at("archiver")
      .load[ArchiverSettings] match {
      case Right(settings) => fail(s"Expected the incomplete archiver settings to be rejected, but loaded $settings")
      case Left(failures) =>
        val missing: Set[String] = failures.toList.collect {
          case ConvertFailure(KeyNotFound(key, _), _, "archiver") => key
        }.toSet
        assertEquals(
          Set("endpoint", "staging-directory", "credentials-file", "schedule", "upload", "filter", "object-metadata",
            "vendor-extension"),
          missing,
          failures.prettyPrint()
        )
    }
  }

  /** The failure reason of every field that failed to convert, keyed by its path. */
  private def failureReasons(failures: ConfigReaderFailures): Map[String, FailureReason] =
    failures.toList.collect {
      case ConvertFailure(reason, _, path) => path -> reason
    }.toMap
}

object MediaArchiveUploaderSettingsTest {

  /** When archiving runs and how long archived objects are kept. */
  final case class Schedule(
      timezone: ZoneId,
      reportingOffset: ZoneOffset,
      startAfter: Instant,
      retention: Period,
      retentionGranularity: ChronoUnit,
      fiscalYear: Year
  )

  given ConfigReader[Schedule] = deriveReader[Schedule]
  given ConfigWriter[Schedule] = deriveWriter[Schedule]

  /** How objects are split into parts and pushed to the store. */
  final case class Upload(
      timeout: java.time.Duration,
      pollInterval: FiniteDuration,
      maxIdle: Duration,
      partSize: ConfigMemorySize,
      maxObjectBytes: BigInt,
      costPerGigabyte: BigDecimal,
      clockUnit: TimeUnit,
      keyDelimiter: Char,
      allowedRegions: Set[String]
  )

  given ConfigReader[Upload] = deriveReader[Upload]
  given ConfigWriter[Upload] = deriveWriter[Upload]

  /** Which staged files are archived; regular expressions have no structural equality. */
  final case class ObjectFilter(accept: Regex, reject: Pattern)

  given ConfigReader[ObjectFilter] = deriveReader[ObjectFilter]
  given ConfigWriter[ObjectFilter] = deriveWriter[ObjectFilter]

  /** The whole archiver; object metadata and the vendor extension are passed through untouched. */
  final case class ArchiverSettings(
      instanceId: UUID,
      previousInstanceId: Option[UUID],
      endpoint: URI,
      stagingDirectory: Path,
      credentialsFile: File,
      schedule: Schedule,
      upload: Upload,
      filter: ObjectFilter,
      objectMetadata: ConfigObject,
      vendorExtension: ConfigValue
  )

  given ConfigReader[ArchiverSettings] = deriveReader[ArchiverSettings]
  given ConfigWriter[ArchiverSettings] = deriveWriter[ArchiverSettings]

  val ArchiverConfig: String = """
    archiver {
      instance-id = "3f1c2b9e-7d4a-4c1e-9b2f-0a6d5e8c7b11"
      endpoint = "https://objects.eu-central.example.net:9443/media"
      staging-directory = "/var/spool/archiver/staging"
      credentials-file = "/etc/archiver/credentials.json"
      schedule {
        timezone = "Europe/Berlin"
        reporting-offset = "-05:00"
        start-after = "2026-01-01T00:00:00Z"
        retention = 90 days
        retention-granularity = DAYS
        fiscal-year = 2026
      }
      upload {
        timeout = "PT45S"
        poll-interval = 15 seconds
        max-idle = Inf
        part-size = 16 MiB
        max-object-bytes = 5497558138880
        cost-per-gigabyte = 0.0235
        clock-unit = MILLISECONDS
        key-delimiter = "/"
        allowed-regions = ["eu-central-1", "eu-west-1", "eu-central-1"]
      }
      filter {
        accept = "raw/\\d{4}/(clip-\\d+)\\.(mov|mxf)"
        reject = "/tmp/"
      }
      object-metadata {
        owner = "media-team"
        replicas = 3
        labels = ["public", "cdn"]
      }
      vendor-extension = true
    }
    """
}
