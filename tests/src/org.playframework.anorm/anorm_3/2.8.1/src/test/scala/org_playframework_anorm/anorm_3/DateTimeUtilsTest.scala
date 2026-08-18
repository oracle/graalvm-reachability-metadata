/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.text.DateFormatSymbols
import java.util.Locale

import org.joda.time.DateTimeUtils
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DateTimeUtilsTest {
  @Test
  def getsDateFormatSymbolsForLocale(): Unit = {
    val symbols: DateFormatSymbols = DateTimeUtils.getDateFormatSymbols(Locale.US)
    val expectedSymbols: DateFormatSymbols = DateFormatSymbols.getInstance(Locale.US)

    assertTrue(symbols.getMonths.sameElements(expectedSymbols.getMonths))
    assertTrue(symbols.getZoneStrings.nonEmpty)
  }
}
