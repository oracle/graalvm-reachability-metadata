/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's generated-key insertion subsystem to create an order and its receipt.
  * Returned identities populate application records and link each new line item to its order.
  */
class GeneratedOrderReceiptTest {
  case class Order(id: Long, customer: String)
  case class OrderLine(id: Long, orderId: Long, sku: String, quantity: Int)

  class Orders(tag: Tag) extends Table[Order](tag, "RECEIPT_ORDERS") {
    def id: Rep[Long] = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def customer: Rep[String] = column[String]("CUSTOMER")
    def * = (id, customer).mapTo[Order]
  }

  class OrderLines(tag: Tag, orders: TableQuery[Orders])
      extends Table[OrderLine](tag, "RECEIPT_ORDER_LINES") {
    def id: Rep[Long] = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def orderId: Rep[Long] = column[Long]("ORDER_ID")
    def sku: Rep[String] = column[String]("SKU")
    def quantity: Rep[Int] = column[Int]("QUANTITY")
    def order = foreignKey("RECEIPT_ORDER_FK", orderId, orders)(_.id)
    def * = (id, orderId, sku, quantity).mapTo[OrderLine]
  }

  @Test
  @Timeout(60)
  def returnsIdentitiesForLinkedOrderAndBatchInsertedLines(): Unit = {
    val orders = TableQuery(tag => new Orders(tag))
    val lines = TableQuery(tag => new OrderLines(tag, orders))
    val schema = orders.schema ++ lines.schema
    val insertOrder = orders.returning(orders.map(_.id)).into { (order, id) =>
      order.copy(id = id)
    }
    val insertLines = lines.returning(lines.map(_.id)).into { (line, id) =>
      line.copy(id = id)
    }
    val database: Database = Database.forURL(
      "jdbc:h2:mem:generated_order_receipt;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )

    val placeOrder = for {
      order <- insertOrder += Order(0L, "Ada")
      items <- insertLines ++= Seq(
        OrderLine(0L, order.id, "NOTEBOOK", 2),
        OrderLine(0L, order.id, "PEN", 3)
      )
    } yield (order, items)

    val scenario = for {
      _ <- schema.create
      placed <- placeOrder.transactionally
      (order, items) = placed
      savedOrder <- orders.filter(_.id === order.id).result.head
      savedItems <- lines.filter(_.orderId === order.id).sortBy(_.sku).result
      receipt <- (for {
        header <- orders if header.id === order.id
        line <- lines if line.orderId === header.id
      } yield (header.customer, line.sku, line.quantity)).sortBy(_._2).result
    } yield (order, items, savedOrder, savedItems, receipt)

    try {
      val (order, items, savedOrder, savedItems, receipt) = Await.result(
        database.run(scenario.andFinally(schema.drop)),
        30.seconds
      )
      assertTrue(order.id > 0L)
      assertEquals(Order(order.id, "Ada"), savedOrder)
      assertEquals(2, items.size)
      assertTrue(items.forall(line => line.id > 0L && line.orderId == order.id))
      assertEquals(2, items.map(_.id).distinct.size)
      assertEquals(items.sortBy(_.sku), savedItems)
      assertEquals(Seq(("Ada", "NOTEBOOK", 2), ("Ada", "PEN", 3)), receipt)
    } finally {
      database.close()
    }
  }
}
