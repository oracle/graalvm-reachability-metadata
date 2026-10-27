/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import scala.jdk.CollectionConverters.*

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigRenderOptions, ConfigValue, ConfigValueFactory}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertThrows, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigCursor, ConfigObjectCursor, ConfigReader, ConfigSource, ConfigWriter, KebabCase}
import pureconfig.error.{CollidingKeys, ConfigReaderFailures, ConvertFailure, FailureReason, UnknownKey}
import pureconfig.generic.{CoproductHint, FieldCoproductHint, ProductHint}
import pureconfig.generic.error.{CoproductHintException, InvalidCoproductOption}
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises user-defined hint strategies: semiauto-derived readers and writers for a backup job
 * catalog driven by an application-written `CoproductHint` that encodes storage backends as
 * single-key wrapper objects (with legacy backend aliases) and an application-written
 * `ProductHint` that accepts deprecated key spellings, tolerates `x-` extension keys, rejects other
 * unknown keys and omits empty lists when rendering, next to the `CoproductHintException` a
 * `FieldCoproductHint` raises when a case class field collides with its discriminator.
 */
class BackupJobCatalogTest {
  import BackupJobCatalogTest.*
  import BackupJobCatalogTest.given

  @Test
  def loadsJobsWrittenWithCurrentAndDeprecatedSpellings(): Unit = {
    val catalog: BackupCatalog = ConfigSource.string(CatalogConfig).at("backups").loadOrThrow[BackupCatalog]

    assertEquals(
      List(
        BackupJob(
          name = "orders-db",
          schedule = "0 2 * * *",
          retentionDays = 30,
          storage = S3Bucket("acme-backups", "eu-west-1", "orders/"),
          excludePaths = List("/tmp", "/var/cache"),
          alerts = List(Email("ops@acme.example"))
        ),
        BackupJob(
          name = "legacy-reports",
          schedule = "@daily",
          retentionDays = 14,
          storage = S3Bucket("acme-legacy", "us-east-1"),
          excludePaths = List("/reports/tmp")
        ),
        BackupJob(name = "scratch", schedule = "@hourly", storage = Discard),
        BackupJob(
          name = "media",
          schedule = "@weekly",
          retentionDays = 90,
          storage = LocalDirectory("/mnt/backup/media", compress = false),
          alerts = List(Webhook("https://hooks.acme.example/backup"))
        )
      ),
      catalog.jobs
    )
  }

  @Test
  def rendersTheCurrentSchemaThatReloadsUnchanged(): Unit = {
    val catalog: BackupCatalog = ConfigSource.string(CatalogConfig).at("backups").loadOrThrow[BackupCatalog]
    val emailOnly: BackupCatalog = BackupCatalog(catalog.jobs.filterNot(_.alerts.exists(_.isInstanceOf[Webhook])))
    val rendered: ConfigObject = ConfigWriter[BackupCatalog].to(emailOnly).asInstanceOf[ConfigObject]
    val jobs: List[Config] = rendered.toConfig.getConfigList("jobs").asScala.toList

    assertEquals(3, jobs.size)
    val legacy: Config = jobs(1)
    assertEquals(14, legacy.getInt("retention-days"))
    assertFalse(legacy.hasPath("keep-days"))
    assertEquals(List("/reports/tmp"), legacy.getStringList("exclude-paths").asScala.toList)
    assertFalse(legacy.hasPath("exclude"))
    assertEquals(Set("s3"), legacy.getObject("storage").keySet.asScala.toSet)
    assertEquals("acme-legacy", legacy.getString("storage.s3.bucket"))
    assertFalse(legacy.hasPath("alerts"))

    val scratch: Config = jobs(2)
    assertEquals(Set("discard"), scratch.getObject("storage").keySet.asScala.toSet)
    assertTrue(scratch.getObject("storage.discard").isEmpty)
    assertFalse(scratch.hasPath("exclude-paths"))
    assertEquals(7, scratch.getInt("retention-days"))

    assertEquals("email", jobs.head.getConfigList("alerts").get(0).getString("kind"))

    val text: String = rendered.render(ConfigRenderOptions.concise())
    val reloaded: BackupCatalog = ConfigSource.string(text).loadOrThrow[BackupCatalog]
    assertEquals(emailOnly, reloaded)
  }

  @Test
  def reportsMalformedStorageBlocksAndUnknownKeysAtTheirPaths(): Unit = {
    val result: ConfigReader.Result[BackupCatalog] = ConfigSource.string(BrokenConfig).at("backups").load[BackupCatalog]

    result match {
      case Right(catalog) => fail(s"Expected the broken catalog to be rejected, but loaded $catalog")
      case Left(failures) =>
        val reasons: Map[String, List[FailureReason]] = failuresByPath(failures)
        assertEquals(
          List(AmbiguousStorageBlock(List("local", "s3"))),
          reasons.getOrElse("backups.jobs.0.storage", Nil),
          failures.prettyPrint()
        )
        assertEquals(
          List(UnknownStorageBackend("ftp")),
          reasons.getOrElse("backups.jobs.1.storage", Nil),
          failures.prettyPrint()
        )
        assertEquals(
          List(InvalidCoproductOption("GcsBucket")),
          reasons.getOrElse("backups.jobs.2.storage.gcs", Nil),
          failures.prettyPrint()
        )
        assertEquals(
          List(UnknownKey("keep-days")),
          reasons.getOrElse("backups.jobs.3.keep-days", Nil),
          failures.prettyPrint()
        )
        assertEquals(
          List(UnknownKey("retension-days")),
          reasons.getOrElse("backups.jobs.4.retension-days", Nil),
          failures.prettyPrint()
        )
        assertFalse(reasons.keys.exists(_.endsWith("x-ticket")), failures.prettyPrint())
        assertEquals(5, failures.toList.size, failures.prettyPrint())
    }
  }

  @Test
  def renderingAnAlertWhoseFieldCollidesWithTheDiscriminatorFails(): Unit = {
    val catalog: BackupCatalog = ConfigSource.string(CatalogConfig).at("backups").loadOrThrow[BackupCatalog]
    val media: BackupJob = catalog.jobs.find(_.name == "media").getOrElse(fail("media job missing"))

    val thrown: CoproductHintException = assertThrows(
      classOf[CoproductHintException],
      () => ConfigWriter[BackupJob].to(media)
    )
    thrown.failure match {
      case CollidingKeys(key, existing) =>
        assertEquals("kind", key)
        assertEquals("json", existing.unwrapped)
      case other => fail(s"Expected a colliding discriminator key, got $other")
    }
    assertTrue(thrown.getMessage.contains("collides with a key necessary to disambiguate"), thrown.getMessage)
  }

  private def failuresByPath(failures: ConfigReaderFailures): Map[String, List[FailureReason]] =
    failures.toList
      .collect { case ConvertFailure(reason, _, path) => path -> reason }
      .groupMap(_._1)(_._2)
}

object BackupJobCatalogTest {

  /** Raised when a storage block names zero or several backends instead of exactly one. */
  final case class AmbiguousStorageBlock(keys: List[String]) extends FailureReason {
    def description: String = s"Expected exactly one storage backend, found ${keys.mkString("[", ", ", "]")}."
  }

  /** Raised when a storage block names a backend nobody knows. */
  final case class UnknownStorageBackend(key: String) extends FailureReason {
    def description: String = s"Unknown storage backend '$key'."
  }

  sealed trait StorageTarget
  final case class S3Bucket(bucket: String, region: String, prefix: String = "") extends StorageTarget
  final case class LocalDirectory(path: String, compress: Boolean = true) extends StorageTarget
  case object Discard extends StorageTarget

  /**
   * Encodes every storage backend as an object with a single key naming the backend, such as
   * `storage { s3 { bucket = ... } }`. Retired spellings stay readable through an alias table.
   */
  final class WrapperKeyCoproductHint extends CoproductHint[StorageTarget] {
    private val keyForOption: Map[String, String] =
      Map("S3Bucket" -> "s3", "LocalDirectory" -> "local", "Discard" -> "discard")
    private val optionForKey: Map[String, String] = keyForOption.map(_.swap)

    /** `gcs` still points at a backend that was removed from the family. */
    private val legacyAliases: Map[String, String] = Map("aws-s3" -> "S3Bucket", "gcs" -> "GcsBucket")

    def from(cursor: ConfigCursor, options: Seq[String]): ConfigReader.Result[CoproductHint.Action] =
      cursor.asObjectCursor.flatMap { objCursor =>
        objCursor.keys.toList match {
          case List(key) =>
            optionForKey.get(key).orElse(legacyAliases.get(key)) match {
              case Some(option) => objCursor.atKey(key).map(inner => CoproductHint.Use(inner, option))
              case None => objCursor.failed(UnknownStorageBackend(key))
            }
          case keys => objCursor.failed(AmbiguousStorageBlock(keys.sorted))
        }
      }

    def to(value: ConfigValue, name: String): ConfigValue =
      ConfigValueFactory.fromMap(Map(keyForOption(name) -> value).asJava)
  }

  given CoproductHint[StorageTarget] = new WrapperKeyCoproductHint
  given ConfigReader[StorageTarget] = deriveReader[StorageTarget]
  given ConfigWriter[StorageTarget] = deriveWriter[StorageTarget]

  sealed trait Alert
  final case class Email(to: String) extends Alert

  /** Its `kind` field shares the discriminator's name, so it reads but cannot be written. */
  final case class Webhook(url: String, kind: String = "json") extends Alert

  given CoproductHint[Alert] = new FieldCoproductHint[Alert]("kind")
  given ConfigReader[Alert] = deriveReader[Alert]
  given ConfigWriter[Alert] = deriveWriter[Alert]

  /**
   * Reads kebab-case keys, falling back to a deprecated spelling when the current one is absent,
   * accepts `x-` extension keys, rejects every other unknown key and leaves empty lists unwritten.
   */
  final class DeprecatedKeysProductHint[A](deprecatedKeys: Map[String, String]) extends ProductHint[A] {
    private val mapping: ConfigFieldMapping = ConfigFieldMapping(CamelCase, KebabCase)

    def from(cursor: ConfigObjectCursor, fieldName: String): ProductHint.Action = {
      val current: String = mapping(fieldName)
      val key: String =
        if (cursor.keys.exists(_ == current)) current
        else deprecatedKeys.get(fieldName).filter(old => cursor.keys.exists(_ == old)).getOrElse(current)
      ProductHint.UseOrDefault(cursor.atKeyOrUndefined(key), key)
    }

    def bottom(cursor: ConfigObjectCursor, usedFields: Set[String]): Option[ConfigReaderFailures] =
      cursor.map.toList.sortBy(_._1).collect {
        case (key, keyCursor) if !usedFields.contains(key) && !key.startsWith("x-") =>
          keyCursor.failureFor(UnknownKey(key))
      } match {
        case head :: tail => Some(ConfigReaderFailures(head, tail*))
        case Nil => None
      }

    def to(value: Option[ConfigValue], fieldName: String): Option[(String, ConfigValue)] =
      value
        .filter {
          case list: ConfigList => !list.isEmpty
          case _ => true
        }
        .map(mapping(fieldName) -> _)
  }

  final case class BackupJob(
      name: String,
      schedule: String,
      retentionDays: Int = 7,
      storage: StorageTarget,
      excludePaths: List[String] = Nil,
      alerts: List[Alert] = Nil
  )

  given ProductHint[BackupJob] =
    new DeprecatedKeysProductHint[BackupJob](Map("retentionDays" -> "keep-days", "excludePaths" -> "exclude"))
  given ConfigReader[BackupJob] = deriveReader[BackupJob]
  given ConfigWriter[BackupJob] = deriveWriter[BackupJob]

  final case class BackupCatalog(jobs: List[BackupJob])

  given ConfigReader[BackupCatalog] = deriveReader[BackupCatalog]
  given ConfigWriter[BackupCatalog] = deriveWriter[BackupCatalog]

  val CatalogConfig: String = """
    backups {
      jobs = [
        {
          name = "orders-db"
          schedule = "0 2 * * *"
          retention-days = 30
          storage { s3 { bucket = "acme-backups", region = "eu-west-1", prefix = "orders/" } }
          exclude-paths = ["/tmp", "/var/cache"]
          alerts = [ { kind = email, to = "ops@acme.example" } ]
          x-owner = "team-orders"
        }
        {
          name = "legacy-reports"
          schedule = "@daily"
          keep-days = 14
          storage { aws-s3 { bucket = "acme-legacy", region = "us-east-1" } }
          exclude = ["/reports/tmp"]
        }
        { name = "scratch", schedule = "@hourly", storage { discard {} } }
        {
          name = "media"
          schedule = "@weekly"
          retention-days = 90
          storage { local { path = "/mnt/backup/media", compress = false } }
          alerts = [ { kind = webhook, url = "https://hooks.acme.example/backup" } ]
        }
      ]
    }
    """

  val BrokenConfig: String = """
    backups {
      jobs = [
        { name = "a", schedule = "@daily", storage { s3 { bucket = b, region = r }, local { path = "/x" } } }
        { name = "b", schedule = "@daily", storage { ftp { host = "ftp.acme.example" } } }
        { name = "c", schedule = "@daily", storage { gcs { bucket = "old-bucket" } } }
        { name = "d", schedule = "@daily", retention-days = 3, keep-days = 5, storage { discard {} } }
        { name = "e", schedule = "@daily", retension-days = 3, x-ticket = "OPS-12", storage { discard {} } }
      ]
    }
    """
}
