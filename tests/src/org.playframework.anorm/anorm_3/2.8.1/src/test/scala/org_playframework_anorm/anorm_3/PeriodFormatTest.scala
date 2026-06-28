/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.util.Locale

import org.joda.time.Period
import org.joda.time.format.PeriodFormat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeriodFormatTest {
  @Test
  def wordBasedFormatterLoadsLocalizedBundle(): Unit = {
    val locale: Locale = Locale.forLanguageTag("en-AU")
    val period: Period = new Period(1, 2, 0, 3, 4, 5, 6, 0)

    val formatted: String = PeriodFormat.wordBased(locale).print(period)

    assertFalse(formatted.isBlank)
    assertTrue(formatted.contains("1"))
  }
}
