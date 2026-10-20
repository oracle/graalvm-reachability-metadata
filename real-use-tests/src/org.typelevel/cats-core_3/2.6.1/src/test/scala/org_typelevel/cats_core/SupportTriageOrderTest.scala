/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.Order
import cats.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Order to rank a support queue by severity, waiting time, and ticket reference.
  * Assigned tickets are excluded and each dispatch returns only the highest-ranked available work.
  */
@Timeout(60)
class SupportTriageOrderTest {
  private case class Ticket(
    reference: String,
    severity: Int,
    openedMinute: Long,
    assignee: Option[String]
  )

  // Severity 1 is most urgent; an earlier opening minute means a longer wait.
  private val triageOrder: Order[Ticket] = Order.by[Ticket, (Int, Long, String)] { ticket =>
    (ticket.severity, ticket.openedMinute, ticket.reference)
  }

  private def dispatch(tickets: List[Ticket], capacity: Int): List[String] = {
    tickets.filter(_.assignee.isEmpty)
      .sorted(using triageOrder.toOrdering)
      .take(capacity)
      .map(_.reference)
  }

  @Test
  def selectsUrgentThenOldestTicketsWithoutRedispatchingAssignedWork(): Unit = {
    val tickets: List[Ticket] = List(
      Ticket("SUP-105", 3, 100L, None),
      Ticket("SUP-104", 1, 140L, None),
      Ticket("SUP-103", 2, 90L, None),
      Ticket("SUP-102", 1, 120L, None),
      Ticket("SUP-101", 1, 80L, Some("agent-a"))
    )

    assertEquals(List("SUP-102", "SUP-104", "SUP-103"), dispatch(tickets, 3))
    assertEquals(
      List("SUP-102", "SUP-104", "SUP-103", "SUP-105"),
      dispatch(tickets, 10)
    )

    val afterAssignment: List[Ticket] = tickets.map { ticket =>
      if (ticket.reference == "SUP-102") ticket.copy(assignee = Some("agent-b"))
      else ticket
    }
    assertEquals(List("SUP-104", "SUP-103"), dispatch(afterAssignment, 2))
    assertEquals(List("SUP-102"), dispatch(tickets, 1))
  }

  @Test
  def breaksTiesByReferenceRegardlessOfTheUploadOrder(): Unit = {
    val tickets: List[Ticket] = List(
      Ticket("SUP-203", 2, 200L, None),
      Ticket("SUP-201", 2, 200L, None),
      Ticket("SUP-202", 2, 200L, None)
    )
    val expected: List[String] = List("SUP-201", "SUP-202", "SUP-203")

    tickets.permutations.foreach { upload =>
      assertEquals(expected, dispatch(upload, 3))
      assertEquals(expected.take(2), dispatch(upload, 2))
    }
  }

  @Test
  def returnsNoWorkWhenTheQueueOrDispatchCapacityIsEmpty(): Unit = {
    val available: Ticket = Ticket("SUP-300", 1, 300L, None)
    val assigned: Ticket = available.copy(assignee = Some("agent-c"))

    assertEquals(List.empty[String], dispatch(List.empty, 5))
    assertEquals(List.empty[String], dispatch(List(assigned), 5))
    assertEquals(List.empty[String], dispatch(List(available), 0))
    assertEquals(List("SUP-300"), dispatch(List(available), 1))
  }
}
