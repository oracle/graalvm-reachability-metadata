/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import doobie.free.connection.ConnectionIO
import doobie.free.preparedstatement
import doobie.hi.{connection => HC}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises prepared-statement batching to persist order lines and produce a receipt. */
@Timeout(60)
class BatchOrderReceiptTest {
  private case class OrderLine(sku: String, quantity: Int, unitCents: Int)

  @Test
  def submittedBatchProducesTheCommittedReceipt(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:batch_order_receipt;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val orderId: String = "order-1042"
    val lines: List[OrderLine] = List(
      OrderLine("baker's-flour", 2, 325),
      OrderLine("olive-oil", 1, 1199),
      OrderLine("tomatoes", 3, 249)
    )

    val initialize: ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS receipt_lines".update.run
      _ <- sql"""CREATE TABLE receipt_lines (
                  order_id VARCHAR(40) NOT NULL,
                  sku VARCHAR(80) NOT NULL,
                  quantity INTEGER NOT NULL,
                  unit_cents INTEGER NOT NULL,
                  PRIMARY KEY (order_id, sku)
                )""".update.run
    } yield ()

    val batch: preparedstatement.PreparedStatementIO[List[Int]] = for {
      _ <- lines.traverse_ { line =>
        for {
          _ <- preparedstatement.setString(1, orderId)
          _ <- preparedstatement.setString(2, line.sku)
          _ <- preparedstatement.setInt(3, line.quantity)
          _ <- preparedstatement.setInt(4, line.unitCents)
          _ <- preparedstatement.addBatch
        } yield ()
      }
      counts <- preparedstatement.executeBatch
    } yield counts.toList

    val submit: ConnectionIO[List[Int]] = HC.prepareStatement(
      "INSERT INTO receipt_lines (order_id, sku, quantity, unit_cents) VALUES (?, ?, ?, ?)"
    )(batch)

    val receipt: ConnectionIO[List[(String, Int, Int)]] =
      sql"""SELECT sku, quantity, quantity * unit_cents
            FROM receipt_lines WHERE order_id = $orderId ORDER BY sku"""
        .query[(String, Int, Int)].to[List]

    val (counts, rows): (List[Int], List[(String, Int, Int)]) = (for {
      _ <- initialize.transact(transactor)
      updates <- submit.transact(transactor)
      persisted <- receipt.transact(transactor)
      _ <- sql"DROP TABLE receipt_lines".update.run.transact(transactor)
    } yield (updates, persisted)).timeout(45.seconds).unsafeRunSync()

    assertEquals(List(1, 1, 1), counts)
    assertEquals(
      List(("baker's-flour", 2, 650), ("olive-oil", 1, 1199), ("tomatoes", 3, 747)),
      rows
    )
    assertEquals(2596, rows.map(_._3).sum)
  }
}
