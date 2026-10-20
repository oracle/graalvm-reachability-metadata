/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.effect.IO
import cats.effect.kernel.Outcome
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import doobie.free.connection
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

/** Exercises ConnectionIO Future interoperation and cancellation for imports awaiting approval. */
@Timeout(60)
class ImportApprovalCancellationTest {
  private case class Entry(sku: String, quantity: Int)

  private def importStock(
      batch: String,
      entries: List[Entry],
      approval: Future[Unit],
      staged: Promise[Unit]
  ): connection.ConnectionIO[Unit] = for {
    _ <- entries.traverse_ { entry =>
      sql"""INSERT INTO approved_stock (batch_id, sku, quantity)
              VALUES ($batch, ${entry.sku}, ${entry.quantity})""".update.run
    }
    _ <- connection.delay { staged.success(()); () }
    _ <- connection.fromFuture(connection.pure(approval))
    _ <- sql"INSERT INTO stock_import_receipts (batch_id) VALUES ($batch)".update.run
  } yield ()

  @Test
  def abandoningApprovalDiscardsStagedStockAndAnApprovedRetryCommits(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:import_approval_cancellation;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val entries: List[Entry] = List(Entry("coffee", 12), Entry("tea", 8))
    val pendingApproval: Promise[Unit] = Promise[Unit]()
    val staged: Promise[Unit] = Promise[Unit]()

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS stock_import_receipts".update.run
      _ <- sql"DROP TABLE IF EXISTS approved_stock".update.run
      _ <- sql"""CREATE TABLE approved_stock (
                  batch_id VARCHAR(40) NOT NULL,
                  sku VARCHAR(40) NOT NULL,
                  quantity INTEGER NOT NULL,
                  PRIMARY KEY (batch_id, sku)
                )""".update.run
      _ <- sql"""CREATE TABLE stock_import_receipts (
                  batch_id VARCHAR(40) PRIMARY KEY
                )""".update.run
    } yield ()

    val cleanup: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS stock_import_receipts".update.run
      _ <- sql"DROP TABLE IF EXISTS approved_stock".update.run
    } yield ()

    val readStock: connection.ConnectionIO[List[(String, String, Int)]] =
      sql"SELECT batch_id, sku, quantity FROM approved_stock ORDER BY batch_id, sku"
        .query[(String, String, Int)].to[List]
    val readReceipts: connection.ConnectionIO[List[String]] =
      sql"SELECT batch_id FROM stock_import_receipts ORDER BY batch_id".query[String].to[List]

    val cancelPendingImport: IO[Outcome[IO, Throwable, Unit]] =
      importStock("abandoned", entries, pendingApproval.future, staged).transact(transactor)
        .start.bracket { fiber =>
          for {
            _ <- IO.fromFuture(IO.pure(staged.future)).timeout(15.seconds)
            _ <- fiber.cancel
            outcome <- fiber.join
          } yield outcome
        }(_.cancel)

    val program = for {
      _ <- initialize.transact(transactor)
      outcome <- cancelPendingImport
      abandonedStock <- readStock.transact(transactor)
      abandonedReceipts <- readReceipts.transact(transactor)
      _ <- importStock("approved", entries, Future.successful(()), Promise[Unit]())
        .transact(transactor)
      committedStock <- readStock.transact(transactor)
      committedReceipts <- readReceipts.transact(transactor)
    } yield (outcome, abandonedStock, abandonedReceipts, committedStock, committedReceipts)

    val (outcome, abandonedStock, abandonedReceipts, committedStock, committedReceipts) =
      program.guarantee(cleanup.transact(transactor)).timeout(45.seconds).unsafeRunSync()

    assertTrue(outcome match {
      case Outcome.Canceled() => true
      case _ => false
    })
    assertEquals(List.empty[(String, String, Int)], abandonedStock)
    assertEquals(List.empty[String], abandonedReceipts)
    assertEquals(List(("approved", "coffee", 12), ("approved", "tea", 8)), committedStock)
    assertEquals(List("approved"), committedReceipts)
  }
}
