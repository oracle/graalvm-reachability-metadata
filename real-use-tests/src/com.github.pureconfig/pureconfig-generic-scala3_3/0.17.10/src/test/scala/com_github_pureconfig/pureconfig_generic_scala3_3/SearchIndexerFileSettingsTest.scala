/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.concurrent.duration.*

import com.typesafe.config.{ConfigOrigin, ConfigRenderOptions, ConfigValue}
import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows, assertTrue, fail}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pureconfig.{ConfigObjectSource, ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{
  CannotParse,
  CannotReadFile,
  ConfigReaderException,
  ConfigReaderFailures,
  ConvertFailure,
  KeyNotFound,
  WrongType
}
import pureconfig.generic.FieldCoproductHint
import pureconfig.generic.error.UnexpectedValueForFieldCoproductHint
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises failure origins of derived readers over file-backed configuration: semiauto-derived
 * product and coproduct readers loaded from layered HOCON files (a deployment file that includes a
 * shared file, falling back to packaged defaults), whose `ConvertFailure`s carry the file and line
 * of the offending value, whose `loadOrThrow` raises a `ConfigReaderException` naming them, and
 * which sit behind unreadable, unparseable, optional and recovered sources.
 */
class SearchIndexerFileSettingsTest {
  import SearchIndexerFileSettingsTest.*
  import SearchIndexerFileSettingsTest.given

  @Test
  def loadsDerivedSettingsFromIncludedAndFallbackFiles(@TempDir dir: Path): Unit = {
    val deployment: Path = writeLayers(dir, SharedConfig, DeploymentConfig)

    val settings: IndexerSettings = layeredSource(dir, deployment).at("indexer").loadOrThrow[IndexerSettings]

    assertEquals("catalog-v7", settings.indexName)
    assertEquals(12, settings.shards)
    assertEquals(BatchPolicy(size = 500, flushInterval = 2.seconds), settings.batch)
    assertEquals(List("es-1.internal:9200", "es-2.internal:9200"), settings.nodes)
    assertEquals(SinkTarget.ObjectStore("search-snapshots", "eu-west-1"), settings.snapshots)
    assertEquals(SinkTarget.Discard, settings.deadLetters)
  }

  @Test
  def reportsDerivedFailuresAtTheFileAndLineOfTheBrokenValues(@TempDir dir: Path): Unit = {
    val deployment: Path = writeLayers(dir, BrokenSharedConfig, BrokenDeploymentConfig)

    layeredSource(dir, deployment).at("indexer").load[IndexerSettings] match {
      case Right(settings) => fail(s"Expected the broken deployment to be rejected, but loaded $settings")
      case Left(failures) =>
        val byPath: Map[String, ConvertFailure] = convertFailuresByPath(failures)
        assertEquals(
          Set("indexer.shards", "indexer.batch.flush-interval", "indexer.snapshots.type", "indexer.dead-letters"),
          byPath.keySet,
          failures.prettyPrint()
        )

        val shards: ConvertFailure = byPath("indexer.shards")
        assertTrue(shards.reason.isInstanceOf[WrongType], failures.prettyPrint())
        assertOrigin(shards.origin, "deployment.conf", 4)

        // The broken flush interval sits in the included shared file, not in the deployment file.
        val flushInterval: ConvertFailure = byPath("indexer.batch.flush-interval")
        assertOrigin(flushInterval.origin, "shared.conf", 4)

        val snapshotType: ConvertFailure = byPath("indexer.snapshots.type")
        snapshotType.reason match {
          case UnexpectedValueForFieldCoproductHint(value) => assertEquals("ftp", value.unwrapped)
          case other => fail(s"Expected an unknown sink type, got $other")
        }
        assertOrigin(snapshotType.origin, "deployment.conf", 7)

        // A missing field is reported at the object that lacks it.
        val deadLetters: ConvertFailure = byPath("indexer.dead-letters")
        assertEquals(KeyNotFound("bucket", Set.empty), deadLetters.reason)
        assertOrigin(deadLetters.origin, "deployment.conf", 9)

        assertTrue(failures.prettyPrint().contains("deployment.conf: 4"), failures.prettyPrint())
        assertTrue(failures.prettyPrint().contains("shared.conf: 4"), failures.prettyPrint())
    }
  }

  @Test
  def loadOrThrowRaisesAnExceptionListingEveryFailure(@TempDir dir: Path): Unit = {
    val deployment: Path = writeLayers(dir, BrokenSharedConfig, BrokenDeploymentConfig)

    val error: ConfigReaderException[?] = assertThrows(
      classOf[ConfigReaderException[?]],
      () => layeredSource(dir, deployment).at("indexer").loadOrThrow[IndexerSettings]
    )

    assertEquals(4, error.failures.toList.size, error.getMessage)
    assertTrue(error.getMessage.contains("IndexerSettings"), error.getMessage)
    assertTrue(error.getMessage.contains("'indexer.shards'"), error.getMessage)
    assertTrue(error.getMessage.contains("'indexer.dead-letters'"), error.getMessage)
    assertTrue(error.getMessage.contains("deployment.conf: 7"), error.getMessage)
  }

  @Test
  def reportsUnreadableAndUnparseableFilesBeforeDerivationRuns(@TempDir dir: Path): Unit = {
    val missing: Path = dir.resolve("missing.conf")
    ConfigSource.file(missing).at("indexer").load[IndexerSettings] match {
      case Right(settings) => fail(s"Expected a missing file to be rejected, but loaded $settings")
      case Left(failures) =>
        failures.head match {
          case CannotReadFile(path, _) => assertEquals(missing, path)
          case other => fail(s"Expected CannotReadFile, got $other")
        }
    }

    val malformed: Path = write(dir, "malformed.conf", "indexer {\n  index-name = \"catalog\"\n  shards = [1, 2\n}\n")
    ConfigSource.file(malformed).at("indexer").load[IndexerSettings] match {
      case Right(settings) => fail(s"Expected a malformed file to be rejected, but loaded $settings")
      case Left(failures) =>
        failures.head match {
          case CannotParse(_, origin) =>
            assertTrue(origin.exists(_.filename.endsWith("malformed.conf")), failures.prettyPrint())
          case other => fail(s"Expected CannotParse, got $other")
        }
    }
  }

  @Test
  def optionalAndRecoveredSourcesStillFeedDerivedReaders(@TempDir dir: Path): Unit = {
    val deployment: Path = writeLayers(dir, SharedConfig, DeploymentConfig)
    val absentOverride: ConfigObjectSource = ConfigSource.file(dir.resolve("local-override.conf")).optional

    val withAbsentOverride: IndexerSettings =
      absentOverride.withFallback(layeredSource(dir, deployment)).at("indexer").loadOrThrow[IndexerSettings]
    assertEquals(12, withAbsentOverride.shards)

    val recovered: IndexerSettings = ConfigSource
      .file(dir.resolve("remote-snapshot.conf"))
      .recoverWith { case _ => layeredSource(dir, deployment) }
      .at("indexer")
      .loadOrThrow[IndexerSettings]
    assertEquals(withAbsentOverride, recovered)
  }

  @Test
  def rendersSettingsToAFileThatReloadsUnchanged(@TempDir dir: Path): Unit = {
    val deployment: Path = writeLayers(dir, SharedConfig, DeploymentConfig)
    val settings: IndexerSettings = layeredSource(dir, deployment).at("indexer").loadOrThrow[IndexerSettings]

    val rendered: ConfigValue = ConfigWriter[IndexerSettings].to(settings)
    val renderOptions: ConfigRenderOptions = ConfigRenderOptions.defaults().setOriginComments(false)
    val snapshotFile: Path = write(dir, "resolved.conf", "indexer = " + rendered.render(renderOptions))

    val reloaded: IndexerSettings = ConfigSource.file(snapshotFile).at("indexer").loadOrThrow[IndexerSettings]
    assertEquals(settings, reloaded)
    val renderedType: Either[ConfigReaderFailures, String] =
      ConfigSource.file(snapshotFile).config().map(_.getString("indexer.snapshots.type"))
    assertEquals(Right("object-store"), renderedType)
  }

  private def assertOrigin(origin: Option[ConfigOrigin], fileName: String, line: Int): Unit =
    origin match {
      case None => fail(s"Expected a failure origin in $fileName")
      case Some(o) =>
        assertTrue(o.filename.endsWith(fileName), s"Expected $fileName, got ${o.description}")
        assertEquals(line, o.lineNumber, o.description)
    }

  private def convertFailuresByPath(failures: ConfigReaderFailures): Map[String, ConvertFailure] =
    failures.toList.collect { case failure: ConvertFailure => failure.path -> failure }.toMap
}

object SearchIndexerFileSettingsTest {
  val DefaultsConfig: String = """indexer {
  shards = 3
  batch { size = 100, flush-interval = 5s }
  nodes = ["localhost:9200"]
}
"""

  val SharedConfig: String = """indexer {
  batch {
    size = 500
    flush-interval = 2s
  }
}
"""

  val BrokenSharedConfig: String = """indexer {
  batch {
    size = 500
    flush-interval = "sometimes"
  }
}
"""

  val DeploymentConfig: String = """include "shared.conf"
indexer {
  index-name = "catalog-v7"
  shards = 12
  nodes = ["es-1.internal:9200", "es-2.internal:9200"]
  snapshots {
    type = object-store
    bucket = "search-snapshots", region = "eu-west-1"
  }
}
"""

  val BrokenDeploymentConfig: String = """include "shared.conf"
indexer {
  index-name = "catalog-v7"
  shards = [12]
  nodes = ["es-1.internal:9200"]
  snapshots {
    type = ftp
  }
  dead-letters {
    type = object-store, region = "eu-west-1"
  }
}
"""

  /** Where the indexer ships snapshots and rejected documents. */
  sealed trait SinkTarget

  object SinkTarget {
    final case class ObjectStore(bucket: String, region: String) extends SinkTarget
    case object Discard extends SinkTarget
  }

  final case class BatchPolicy(size: Int, flushInterval: FiniteDuration)

  final case class IndexerSettings(
      indexName: String,
      shards: Int,
      batch: BatchPolicy,
      nodes: List[String],
      snapshots: SinkTarget,
      deadLetters: SinkTarget = SinkTarget.Discard
  )

  given FieldCoproductHint[SinkTarget] = new FieldCoproductHint[SinkTarget]("type")
  given ConfigReader[SinkTarget.ObjectStore] = deriveReader[SinkTarget.ObjectStore]
  given ConfigWriter[SinkTarget.ObjectStore] = deriveWriter[SinkTarget.ObjectStore]
  given ConfigReader[SinkTarget] = deriveReader[SinkTarget]
  given ConfigWriter[SinkTarget] = deriveWriter[SinkTarget]
  given ConfigReader[BatchPolicy] = deriveReader[BatchPolicy]
  given ConfigWriter[BatchPolicy] = deriveWriter[BatchPolicy]
  given ConfigReader[IndexerSettings] = deriveReader[IndexerSettings]
  given ConfigWriter[IndexerSettings] = deriveWriter[IndexerSettings]

  /** Writes the packaged defaults, the shared file and the given deployment file into `dir`. */
  def writeLayers(dir: Path, sharedConfig: String, deploymentConfig: String): Path = {
    write(dir, "defaults.conf", DefaultsConfig)
    write(dir, "shared.conf", sharedConfig)
    write(dir, "deployment.conf", deploymentConfig)
  }

  def layeredSource(dir: Path, deployment: Path): ConfigObjectSource =
    ConfigSource.file(deployment).withFallback(ConfigSource.file(dir.resolve("defaults.conf")))

  def write(dir: Path, name: String, content: String): Path =
    Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8)
}
