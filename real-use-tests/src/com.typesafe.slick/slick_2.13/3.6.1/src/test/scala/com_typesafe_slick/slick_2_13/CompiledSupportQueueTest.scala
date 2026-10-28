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

/** Exercises Slick's compiled query subsystem to serve tenant-specific support queue pages.
  * The same parameterized query serves multiple tenants and cursors and reflects ticket closures.
  */
class CompiledSupportQueueTest {
  case class Ticket(id: Int, tenantId: Int, subject: String, status: String)

  class Tickets(tag: Tag) extends Table[Ticket](tag, "COMPILED_SUPPORT_TICKETS") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def tenantId: Rep[Int] = column[Int]("TENANT_ID")
    def subject: Rep[String] = column[String]("SUBJECT")
    def status: Rep[String] = column[String]("STATUS")
    def * = (id, tenantId, subject, status).mapTo[Ticket]
  }

  @Test
  @Timeout(60)
  def pagesIsolatedQueuesAndRefreshesAfterClosure(): Unit = {
    val tickets = TableQuery(tag => new Tickets(tag))
    val queuePage = Compiled { (tenant: Rep[Int], state: Rep[String], afterId: Rep[Int]) =>
      tickets.filter(ticket =>
        ticket.tenantId === tenant && ticket.status === state && ticket.id > afterId
      ).sortBy(_.id).take(2)
    }
    val database: Database = Database.forURL(
      "jdbc:h2:mem:compiled_support_queue;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val scenario = for {
      _ <- tickets.schema.create
      _ <- tickets ++= Seq(
        Ticket(1, 10, "Reset password", "open"),
        Ticket(2, 20, "Invoice copy", "open"),
        Ticket(3, 10, "Export failed", "open"),
        Ticket(4, 10, "Old request", "closed"),
        Ticket(5, 10, "Change address", "open"),
        Ticket(6, 20, "Update billing", "closed")
      )
      firstPage <- queuePage(10, "open", 0).result
      secondPage <- queuePage(10, "open", firstPage.last.id).result
      otherTenant <- queuePage(20, "open", 0).result
      closedCount <- tickets.filter(ticket => ticket.tenantId === 10 && ticket.id === 1)
        .map(_.status).update("closed")
      refreshedPage <- queuePage(10, "open", 0).result
      closedPage <- queuePage(10, "closed", 0).result
      exhaustedPage <- queuePage(10, "open", 5).result
    } yield (firstPage, secondPage, otherTenant, closedCount,
      refreshedPage, closedPage, exhaustedPage)

    try {
      val (firstPage, secondPage, otherTenant, closedCount,
        refreshedPage, closedPage, exhaustedPage) = Await.result(
        database.run(scenario.andFinally(tickets.schema.drop)),
        30.seconds
      )
      assertEquals(
        Seq(Ticket(1, 10, "Reset password", "open"), Ticket(3, 10, "Export failed", "open")),
        firstPage
      )
      assertEquals(Seq(Ticket(5, 10, "Change address", "open")), secondPage)
      assertEquals(Seq(Ticket(2, 20, "Invoice copy", "open")), otherTenant)
      assertEquals(1, closedCount)
      assertEquals(
        Seq(Ticket(3, 10, "Export failed", "open"), Ticket(5, 10, "Change address", "open")),
        refreshedPage
      )
      assertEquals(
        Seq(Ticket(1, 10, "Reset password", "closed"), Ticket(4, 10, "Old request", "closed")),
        closedPage
      )
      assertEquals(Seq.empty[Ticket], exhaustedPage)
    } finally {
      database.close()
    }
  }
}
