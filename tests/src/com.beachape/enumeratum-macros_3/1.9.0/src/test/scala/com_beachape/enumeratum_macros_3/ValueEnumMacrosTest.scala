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

class ValueEnumMacrosTest {
  @Test
  def precompiledStringValueEntriesAreDiscoveredFromSingletonEntries(): Unit = {
    val entries: IndexedSeq[PrecompiledArticleStatus] = discoveredPrecompiledArticleStatuses

    assertEquals(IndexedSeq(PrecompiledDraft, PrecompiledPublished), entries)
    assertEquals(IndexedSeq("draft", "published"), entries.map(_.value))
    assertSame(PrecompiledDraft, entries.head)
  }
}

inline def findPrecompiledStringValueEntries[A]: IndexedSeq[A] =
  ${ ValueEnumMacros.findStringValueEntriesImpl[A] }

val discoveredPrecompiledArticleStatuses: IndexedSeq[PrecompiledArticleStatus] =
  findPrecompiledStringValueEntries[PrecompiledArticleStatus]
