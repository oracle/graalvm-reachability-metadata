/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.effect.IO
import cats.effect.syntax.all._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import doobie.free.{connection, resultset, statement}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.ResultSet
import scala.concurrent.duration._

/** Exercises updatable result-set cursors to reconcile warehouse inventory with a physical count. */
@Timeout(60)
class InventoryCursorReconciliationTest {
  private def reconcile(counted: Map[String, Int]): resultset.ResultSetIO[List[(String, Int)]] =
    resultset.next.flatMap { available =>
      if (available) {
        for {
          sku <- resultset.getString("sku")
          recorded <- resultset.getInt("on_hand")
          actual = counted(sku)
          adjustment <- if (actual != recorded) {
            for {
              _ <- resultset.updateInt("on_hand", actual)
              _ <- resultset.updateRow
            } yield List((sku, actual - recorded))
          } else {
            resultset.pure(List.empty[(String, Int)])
          }
          remaining <- reconcile(counted)
        } yield adjustment ++ remaining
      } else {
        resultset.pure(List.empty[(String, Int)])
      }
    }

  @Test
  def physicalCountCorrectionsAreVisibleAfterCommit(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:inventory_cursor_reconciliation;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val physicalCount: Map[String, Int] = Map("coffee" -> 7, "tea" -> 14, "cocoa" -> 5)

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS warehouse_stock".update.run
      _ <- sql"""CREATE TABLE warehouse_stock (
                  sku VARCHAR(40) PRIMARY KEY,
                  on_hand INTEGER NOT NULL CHECK (on_hand >= 0)
                )""".update.run
      _ <- sql"""INSERT INTO warehouse_stock VALUES
                  ('coffee', 10), ('tea', 12), ('cocoa', 5)""".update.run
    } yield ()

    val correctInventory: connection.ConnectionIO[List[(String, Int)]] =
      connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)
        .bracket { cursorStatement =>
          connection.embed(cursorStatement,
            statement.executeQuery("SELECT sku, on_hand FROM warehouse_stock")
              .bracket { rows =>
                statement.embed(rows, reconcile(physicalCount))
              }(rows => statement.embed(rows, resultset.close))
          )
        }(cursorStatement => connection.embed(cursorStatement, statement.close))

    val readInventory: connection.ConnectionIO[List[(String, Int)]] =
      sql"SELECT sku, on_hand FROM warehouse_stock ORDER BY sku".query[(String, Int)].to[List]

    val (adjustments, inventory): (List[(String, Int)], List[(String, Int)]) = (for {
      _ <- initialize.transact(transactor)
      changes <- correctInventory.transact(transactor)
      persisted <- readInventory.transact(transactor)
      _ <- sql"DROP TABLE warehouse_stock".update.run.transact(transactor)
    } yield (changes, persisted)).timeout(45.seconds).unsafeRunSync()

    assertEquals(List(("coffee", -3), ("tea", 2)), adjustments.sortBy(_._1))
    assertEquals(List(("cocoa", 5), ("coffee", 7), ("tea", 14)), inventory)
  }
}
