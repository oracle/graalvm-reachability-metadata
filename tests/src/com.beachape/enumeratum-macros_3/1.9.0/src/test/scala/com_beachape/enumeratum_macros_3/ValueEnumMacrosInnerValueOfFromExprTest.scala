/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_macros_3

import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ValueEnumMacrosInnerValueOfFromExprTest {
  @Test
  def intValueEnumFindValuesDiscoversSingletonEntries(): Unit = {
    assertEquals(
      IndexedSeq(TicketPriority.Low, TicketPriority.Normal, TicketPriority.Urgent),
      TicketPriority.values
    )
    assertEquals(
      Map(1 -> TicketPriority.Low, 5 -> TicketPriority.Normal, 9 -> TicketPriority.Urgent),
      TicketPriority.valuesToEntriesMap
    )
    assertSame(TicketPriority.Normal, TicketPriority.withValue(5))
    assertEquals(Some(TicketPriority.Urgent), TicketPriority.withValueOpt(9))
    assertEquals(Right(TicketPriority.Low), TicketPriority.withValueEither(1))
    assertEquals(None, TicketPriority.withValueOpt(42))
  }

  @Test
  def intValueEnumFindValuesSupportsValuesDeclaredInEntryBodies(): Unit = {
    assertEquals(
      IndexedSeq(BodyBackedCode.Accepted, BodyBackedCode.Rejected),
      BodyBackedCode.values
    )
    assertEquals(
      Map(202 -> BodyBackedCode.Accepted, 409 -> BodyBackedCode.Rejected),
      BodyBackedCode.valuesToEntriesMap
    )
    assertSame(BodyBackedCode.Accepted, BodyBackedCode.withValue(202))
    assertEquals(Some(BodyBackedCode.Rejected), BodyBackedCode.withValueOpt(409))
  }
}

sealed abstract class TicketPriority(val value: Int) extends IntEnumEntry

object TicketPriority extends IntEnum[TicketPriority] {
  val values: IndexedSeq[TicketPriority] = findValues

  case object Low extends TicketPriority(1)
  case object Normal extends TicketPriority(5)
  case object Urgent extends TicketPriority(9)
}

sealed abstract class BodyBackedCode extends IntEnumEntry

object BodyBackedCode extends IntEnum[BodyBackedCode] {
  val values: IndexedSeq[BodyBackedCode] = findValues

  case object Accepted extends BodyBackedCode {
    val value: Int = 202
  }

  case object Rejected extends BodyBackedCode {
    val value: Int = 409
  }
}
