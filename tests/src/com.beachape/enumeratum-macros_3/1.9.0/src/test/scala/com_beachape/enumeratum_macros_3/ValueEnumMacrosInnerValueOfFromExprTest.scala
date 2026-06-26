/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_macros_3

import enumeratum.ValueEnumMacros
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ValueEnumMacrosInnerValueOfFromExprTest {
  @Test
  def stringValueEntriesAreDiscoveredFromSingletonEntries(): Unit = {
    val entries: IndexedSeq[ArticleStatus] = ArticleStatus.values

    assertEquals(IndexedSeq(ArticleStatus.Draft, ArticleStatus.Published), entries)
    assertEquals(IndexedSeq("draft", "published"), entries.map(_.value))
    assertSame(ArticleStatus.Draft, entries.head)
  }
}

inline def findStringValueEntries[A]: IndexedSeq[A] =
  ${ ValueEnumMacros.findStringValueEntriesImpl[A] }

sealed abstract class ArticleStatus(val value: String)

object ArticleStatus {
  case object Draft extends ArticleStatus("draft")
  case object Published extends ArticleStatus("published")

  val values: IndexedSeq[ArticleStatus] = findStringValueEntries[ArticleStatus]
}
