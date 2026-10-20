/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.skunk_core_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.otel4s.trace.Tracer
import skunk.Codec
import skunk.Command
import skunk.Query
import skunk.Session
import skunk.Void
import skunk.codec.all.*
import skunk.data.Completion
import skunk.implicits.*

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

/** Exercises Skunk multi-row command encoders to import supplier prices in bounded batches. */
class BulkPriceImportTest {
  private given Tracer[IO] = Tracer.noop[IO]

  private final case class Price(sku: String, description: String, amount: BigDecimal)

  private val priceCodec: Codec[Price] = (text ~ text ~ numeric(12, 2)).imap {
    case ((sku, description), amount) => Price(sku, description, amount)
  }(price => ((price.sku, price.description), price.amount))

  private val readPrices: Query[Void, Price] =
    sql"SELECT sku, description, amount FROM supplier_prices ORDER BY sku".query(priceCodec)

  private def upsertBatch(size: Int): Command[List[Price]] =
    sql"""INSERT INTO supplier_prices (sku, description, amount)
          VALUES ${priceCodec.values.list(size)}
          ON CONFLICT (sku) DO UPDATE
          SET description = EXCLUDED.description, amount = EXCLUDED.amount""".command

  private def importPrices(session: Session[IO], prices: List[Price]): IO[List[Completion]] =
    prices.grouped(2).toList.traverse { batch =>
      session.prepare(upsertBatch(batch.size)).flatMap(_.execute(batch))
    }

  @Test
  @Timeout(60)
  def importsPartialBatchesAndReplacesExistingSupplierPrices(): Unit = {
    val postgres: Postgres = new Postgres
    val database: Resource[IO, Postgres] = Resource.make(
      IO.blocking(postgres.start()).as(postgres)
    )(_ => IO.blocking(postgres.stop()))

    val scenario: IO[Unit] = database.use { server =>
      Session.single[IO](
        host = "127.0.0.1",
        port = server.port,
        user = "postgres",
        database = "postgres",
        password = Some("price-import-secret")
      ).use { session =>
        val original: List[Price] = List(
          Price("A-100", "Worker's gloves", BigDecimal("12.35")),
          Price("B-200", "Bolts, box of 100", BigDecimal("8.90")),
          Price("C-300", "Cable \\ ties", BigDecimal("3.25")),
          Price("D-400", "Drill bit set", BigDecimal("45.00")),
          Price("E-500", "Ear protection", BigDecimal("18.75"))
        )
        val corrections: List[Price] = List(
          Price("B-200", "Bolts, box of 120", BigDecimal("9.50")),
          Price("D-400", "Drill bits 'premium'", BigDecimal("49.95")),
          Price("F-600", "First aid kit", BigDecimal("27.10"))
        )
        val expected: List[Price] = List(
          original(0), corrections(0), original(2), corrections(1), original(4), corrections(2)
        )
        for {
          _ <- session.execute(sql"""CREATE TEMP TABLE supplier_prices (
            sku text PRIMARY KEY, description text NOT NULL,
            amount numeric(12, 2) NOT NULL CHECK (amount >= 0))""".command)
          // Five input rows exercise full batches and a final one-row batch.
          inserted <- importPrices(session, original)
          firstImport <- session.execute(readPrices)
          _ <- IO {
            assertEquals(List(Completion.Insert(2), Completion.Insert(2), Completion.Insert(1)), inserted)
            assertEquals(original, firstImport)
          }
          updated <- importPrices(session, corrections)
          afterCorrections <- session.execute(readPrices)
          _ <- IO {
            assertEquals(List(Completion.Insert(2), Completion.Insert(1)), updated)
            assertEquals(expected, afterCorrections)
          }
          // Replaying a supplier feed updates existing SKUs rather than adding duplicates.
          _ <- importPrices(session, corrections)
          replayed <- session.execute(readPrices)
          emptyImport <- importPrices(session, List.empty[Price])
          afterEmptyImport <- session.execute(readPrices)
          _ <- IO {
            assertEquals(expected, replayed)
            assertTrue(emptyImport.isEmpty)
            assertEquals(expected, afterEmptyImport)
          }
        } yield ()
      }
    }
    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private final class Postgres {
    private val name: String = "skunk-bulk-prices-" + UUID.randomUUID().toString
    def port: Int = {
      val (exit, output) = docker("port", name, "5432/tcp")
      require(exit == 0, output)
      output.trim.split(":").last.toInt
    }

    def start(): Unit = {
      try {
        val (exit, output) = docker(
          "run", "--rm", "--pull=never", "-d", "--name", name,
          "-e", "POSTGRES_PASSWORD=price-import-secret",
          "-p", "127.0.0.1::5432", "postgres:18-alpine"
        )
        require(exit == 0, output)
        val deadline: Long = System.nanoTime() + 20.seconds.toNanos
        var ready: Boolean = false
        while (!ready && System.nanoTime() < deadline) {
          ready = docker("exec", name, "pg_isready", "-h", "127.0.0.1", "-U", "postgres")._1 == 0
          if (!ready) Thread.sleep(200)
        }
        require(ready, "PostgreSQL did not become ready")
      } catch {
        case failure: Exception =>
          stop()
          throw failure
      }
    }

    def stop(): Unit = {
      val (exit, output) = docker("rm", "-f", name)
      require(exit == 0, output)
    }

    private def docker(arguments: String*): (Int, String) = {
      val process: Process = new ProcessBuilder((Seq("docker") ++ arguments)*)
        .redirectErrorStream(true).start()
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw new IllegalStateException("Docker command timed out")
      }
      val output: String = new String(process.getInputStream.readAllBytes(), StandardCharsets.US_ASCII)
      (process.exitValue(), output)
    }
  }
}
