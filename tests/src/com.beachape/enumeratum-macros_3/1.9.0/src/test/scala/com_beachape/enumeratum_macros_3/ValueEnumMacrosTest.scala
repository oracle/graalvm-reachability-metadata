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

class ValueEnumMacrosTest {
  @Test
  def intValueEnumFindValuesDiscoversTopLevelSingletonEntries(): Unit = {
    assertEquals(
      IndexedSeq(TopLevelHttpStatusAccepted, TopLevelHttpStatusConflict),
      TopLevelHttpStatus.values
    )
    assertEquals(
      Map(202 -> TopLevelHttpStatusAccepted, 409 -> TopLevelHttpStatusConflict),
      TopLevelHttpStatus.valuesToEntriesMap
    )
    assertSame(TopLevelHttpStatusAccepted, TopLevelHttpStatus.withValue(202))
    assertEquals(Some(TopLevelHttpStatusConflict), TopLevelHttpStatus.withValueOpt(409))
    assertEquals(Right(TopLevelHttpStatusAccepted), TopLevelHttpStatus.withValueEither(202))
    assertEquals(None, TopLevelHttpStatus.withValueOpt(500))
  }
}

sealed abstract class TopLevelHttpStatus(val value: Int) extends IntEnumEntry

case object TopLevelHttpStatusAccepted extends TopLevelHttpStatus(202)
case object TopLevelHttpStatusConflict extends TopLevelHttpStatus(409)

object TopLevelHttpStatus extends IntEnum[TopLevelHttpStatus] {
  val values: IndexedSeq[TopLevelHttpStatus] = findValues
}
