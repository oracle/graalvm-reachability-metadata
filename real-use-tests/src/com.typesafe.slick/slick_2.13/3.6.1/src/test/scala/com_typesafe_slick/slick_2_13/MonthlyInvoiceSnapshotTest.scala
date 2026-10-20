/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's query-based bulk insertion subsystem to snapshot monthly subscription invoices.
  * An insert-select bills active subscriptions once per cycle and preserves historical charges.
  */
class MonthlyInvoiceSnapshotTest {
  class Subscriptions(tag: Tag)
      extends Table[(Int, String, Int, Int, Boolean)](tag, "BILLING_SUBSCRIPTIONS") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def customer: Rep[String] = column[String]("CUSTOMER")
    def priceCents: Rep[Int] = column[Int]("PRICE_CENTS")
    def seats: Rep[Int] = column[Int]("SEATS")
    def active: Rep[Boolean] = column[Boolean]("ACTIVE")
    def * = (id, customer, priceCents, seats, active)
  }

  class Invoices(tag: Tag)
      extends Table[(String, Int, String, Int)](tag, "BILLING_INVOICES") {
    def cycle: Rep[String] = column[String]("CYCLE")
    def subscriptionId: Rep[Int] = column[Int]("SUBSCRIPTION_ID")
    def customer: Rep[String] = column[String]("CUSTOMER")
    def amountCents: Rep[Int] = column[Int]("AMOUNT_CENTS")
    def key = primaryKey("BILLING_INVOICE_PK", (cycle, subscriptionId))
    def * = (cycle, subscriptionId, customer, amountCents)
  }

  @Test
  @Timeout(60)
  def billsActiveSubscriptionsWithoutRewritingPreviousCycles(): Unit = {
    val subscriptions = TableQuery(tag => new Subscriptions(tag))
    val invoices = TableQuery(tag => new Invoices(tag))
    val schema = subscriptions.schema ++ invoices.schema
    val database: Database = Database.forURL(
      "jdbc:h2:mem:monthly_invoice_snapshot;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )

    def bill(cycle: String): DBIO[Int] = {
      val unbilled = subscriptions.filter { subscription =>
        subscription.active && !invoices.filter { invoice =>
          invoice.cycle === cycle && invoice.subscriptionId === subscription.id
        }.exists
      }.map { subscription =>
        (LiteralColumn(cycle), subscription.id, subscription.customer,
          subscription.priceCents * subscription.seats)
      }
      invoices.forceInsertQuery(unbilled).transactionally
    }

    val scenario = for {
      _ <- schema.create
      _ <- subscriptions ++= Seq(
        (1, "Ada", 1200, 3, true),
        (2, "Acme", 2500, 2, true),
        (3, "Grace", 900, 4, false)
      )
      firstCount <- bill("2026-01")
      replayCount <- bill("2026-01")
      _ <- subscriptions.filter(_.id === 1).map(_.priceCents).update(1500)
      _ <- subscriptions.filter(_.id === 2).map(_.active).update(false)
      _ <- subscriptions.filter(_.id === 3).map(_.active).update(true)
      secondCount <- bill("2026-02")
      savedInvoices <- invoices.sortBy(invoice => (invoice.cycle, invoice.subscriptionId)).result
    } yield (firstCount, replayCount, secondCount, savedInvoices)

    try {
      val (firstCount, replayCount, secondCount, savedInvoices) = Await.result(
        database.run(scenario.andFinally(schema.drop)),
        30.seconds
      )
      assertEquals(2, firstCount)
      assertEquals(0, replayCount)
      assertEquals(2, secondCount)
      assertEquals(Seq(
        ("2026-01", 1, "Ada", 3600),
        ("2026-01", 2, "Acme", 5000),
        ("2026-02", 1, "Ada", 4500),
        ("2026-02", 3, "Grace", 3600)
      ), savedInvoices)
    } finally {
      database.close()
    }
  }
}
