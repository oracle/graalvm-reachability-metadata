/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Stores values through the compressing `RedisCodec.gzip` and `RedisCodec.deflate` codecs.
 *
 * A reporting service archives large quarterly CSV reports with GZIP and
 * keeps per-team audit trails in hashes compressed with DEFLATE. Both
 * connections share one client, and an operations tool reads the same keys
 * through the raw `RedisCodec.Bytes` codec to check that what sits in Redis
 * is compressed, smaller than the original and readable by standard
 * decompressors, while the keys themselves stay plain text.
 */
class ReportArchiveCompressedCodecsTest {

  /** What the service and the operations tool read back from Redis. */
  private final case class Observed(
      report: Option[String],
      reports: Map[String, String],
      rawReport: Array[Byte],
      audit: Map[String, String],
      rawAuditEntry: Array[Byte],
      keys: List[String]
  )

  /** Archives reports under `report:<quarter>` with GZIP-compressed values. */
  private final class ReportArchive(redis: RedisCommands[IO, String, String]) {

    def archive(quarter: String, csv: String): IO[Unit] = redis.set(s"report:$quarter", csv)

    def load(quarter: String): IO[Option[String]] = redis.get(s"report:$quarter")

    def loadAll(quarters: Set[String]): IO[Map[String, String]] =
      redis.mGet(quarters.map(q => s"report:$q")).map(_.map((key, csv) => key.stripPrefix("report:") -> csv))
  }

  /** Records audit entries per team in a hash with DEFLATE-compressed values. */
  private final class AuditTrail(redis: RedisCommands[IO, String, String]) {

    def record(team: String, entries: Map[String, String]): IO[Long] = redis.hSet(s"audit:$team", entries)

    def entries(team: String): IO[Map[String, String]] = redis.hGetAll(s"audit:$team")
  }

  private def readAll(in: InputStream): String =
    try new String(in.readAllBytes(), StandardCharsets.UTF_8)
    finally in.close()

  private def quarterlyReport(quarter: String): String =
    (1 to 200)
      .map(row => s"$quarter,store-${row % 7},widgets,${row * 3},EUR,${row * 17 % 100}.50")
      .mkString("region,store,product,units,currency,revenue\n", "\n", "\n")

  private def bytes(text: String): Array[Byte] = text.getBytes(StandardCharsets.UTF_8)

  @Test
  def archivesCompressedReportsAndAuditTrails(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("report-archive")
      given Log[IO] = log4CatsInstance[IO]

      type Connections = (
          RedisCommands[IO, String, String],
          RedisCommands[IO, String, String],
          RedisCommands[IO, Array[Byte], Array[Byte]]
      )
      val connections: Resource[IO, Connections] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          reports <- Redis[IO].fromClient(client, RedisCodec.gzip(RedisCodec.Utf8))
          audits <- Redis[IO].fromClient(client, RedisCodec.deflate(RedisCodec.Utf8))
          raw <- Redis[IO].fromClient(client, RedisCodec.Bytes)
        } yield (reports, audits, raw)

      val q3: String = quarterlyReport("2026-Q3")
      val q2: String = quarterlyReport("2026-Q2")
      val decision: String = "approved discount for store-3; ticket OPS-4411; " * 20

      val observed: Observed = connections
        .use { (reports, audits, raw) =>
          val archive: ReportArchive = new ReportArchive(reports)
          val trail: AuditTrail = new AuditTrail(audits)
          for {
            _ <- archive.archive("2026-Q3", q3)
            _ <- archive.archive("2026-Q2", q2)
            _ <- trail.record("pricing", Map("2026-10-01" -> decision, "2026-10-02" -> "no changes"))
            report <- archive.load("2026-Q3")
            all <- archive.loadAll(Set("2026-Q2", "2026-Q3", "2026-Q1"))
            audit <- trail.entries("pricing")
            rawReport <- raw.get(bytes("report:2026-Q3"))
            rawAudit <- raw.hGet(bytes("audit:pricing"), bytes("2026-10-01"))
            keys <- raw.keys(bytes("*"))
          } yield Observed(
            report,
            all,
            rawReport.getOrElse(Array.emptyByteArray),
            audit,
            rawAudit.getOrElse(Array.emptyByteArray),
            keys.map(key => new String(key, StandardCharsets.UTF_8)).sorted
          )
        }
        .unsafeRunSync()

      assertEquals(Some(q3), observed.report)
      assertEquals(Map("2026-Q2" -> q2, "2026-Q3" -> q3), observed.reports)
      assertEquals(Map("2026-10-01" -> decision, "2026-10-02" -> "no changes"), observed.audit)

      assertEquals(0x1f.toByte, observed.rawReport(0))
      assertEquals(0x8b.toByte, observed.rawReport(1))
      assertTrue(observed.rawReport.length < bytes(q3).length / 2)
      assertEquals(q3, readAll(new GZIPInputStream(new ByteArrayInputStream(observed.rawReport))))

      assertTrue(observed.rawAuditEntry.length < bytes(decision).length / 2)
      assertEquals(decision, readAll(new InflaterInputStream(new ByteArrayInputStream(observed.rawAuditEntry))))

      assertEquals(List("audit:pricing", "report:2026-Q2", "report:2026-Q3"), observed.keys)
    } finally {
      server.stop()
    }
  }
}
