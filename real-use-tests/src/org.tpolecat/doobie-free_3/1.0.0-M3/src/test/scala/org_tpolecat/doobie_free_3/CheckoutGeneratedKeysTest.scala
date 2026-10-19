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
import doobie.free.{connection, preparedstatement, resultset}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.{SQLException, Statement}
import scala.concurrent.duration._

/** Exercises generated-key retrieval to link checkout line items to a database-assigned order ID. */
@Timeout(60)
class CheckoutGeneratedKeysTest {
  private case class Line(sku: String, quantity: Int, unitCents: Int)

  private def createOrder(customer: String): connection.ConnectionIO[Long] =
    connection.prepareStatement(
      "INSERT INTO checkout_orders (customer) VALUES (?)",
      Statement.RETURN_GENERATED_KEYS
    ).bracket { insert =>
      connection.embed(insert, for {
        _ <- preparedstatement.setString(1, customer)
        _ <- preparedstatement.executeUpdate
        orderId <- preparedstatement.getGeneratedKeys.bracket { keys =>
          preparedstatement.embed(keys, resultset.next.flatMap { available =>
            if (available) {
              resultset.getLong(1)
            } else {
              resultset.raiseError[Long](new SQLException("Order ID was not returned"))
            }
          })
        }(keys => preparedstatement.embed(keys, resultset.close))
      } yield orderId)
    }(insert => connection.embed(insert, preparedstatement.close))

  @Test
  def checkoutLinksCommittedLinesToTheGeneratedOrder(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:checkout_generated_keys;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val customer: String = "customer-1042"
    val lines: List[Line] = List(Line("coffee", 2, 650), Line("mug", 1, 1200))

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS checkout_lines".update.run
      _ <- sql"DROP TABLE IF EXISTS checkout_orders".update.run
      _ <- sql"""CREATE TABLE checkout_orders (
                  order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                  customer VARCHAR(60) NOT NULL
                )""".update.run
      _ <- sql"""CREATE TABLE checkout_lines (
                  order_id BIGINT NOT NULL REFERENCES checkout_orders(order_id),
                  sku VARCHAR(40) NOT NULL,
                  quantity INTEGER NOT NULL,
                  unit_cents INTEGER NOT NULL,
                  PRIMARY KEY (order_id, sku)
                )""".update.run
    } yield ()

    val checkout: connection.ConnectionIO[Long] = for {
      orderId <- createOrder(customer)
      _ <- lines.traverse_ { line =>
        sql"""INSERT INTO checkout_lines (order_id, sku, quantity, unit_cents)
              VALUES ($orderId, ${line.sku}, ${line.quantity}, ${line.unitCents})""".update.run
      }
    } yield orderId

    def readReceipt(orderId: Long): connection.ConnectionIO[List[(String, String, Int, Int)]] =
      sql"""SELECT orders.customer, lines.sku, lines.quantity, lines.quantity * lines.unit_cents
            FROM checkout_orders orders JOIN checkout_lines lines ON orders.order_id = lines.order_id
            WHERE orders.order_id = $orderId ORDER BY lines.sku"""
        .query[(String, String, Int, Int)].to[List]

    val (orderId, receipt): (Long, List[(String, String, Int, Int)]) = (for {
      _ <- initialize.transact(transactor)
      assignedId <- checkout.transact(transactor)
      persisted <- readReceipt(assignedId).transact(transactor)
      _ <- sql"DROP TABLE checkout_lines".update.run.transact(transactor)
      _ <- sql"DROP TABLE checkout_orders".update.run.transact(transactor)
    } yield (assignedId, persisted)).timeout(45.seconds).unsafeRunSync()

    assertTrue(orderId > 0L)
    assertEquals(List((customer, "coffee", 2, 1300), (customer, "mug", 1, 1200)), receipt)
    assertEquals(2500, receipt.map(_._4).sum)
  }
}
