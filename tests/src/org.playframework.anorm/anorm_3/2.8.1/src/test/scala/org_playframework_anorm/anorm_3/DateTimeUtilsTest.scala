/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.text.DateFormatSymbols
import java.util.Locale

import org.assertj.core.api.Assertions.assertThat
import org.joda.time.DateTimeUtils
import org.junit.jupiter.api.Test

class DateTimeUtilsTest {
  @Test
  def obtainsLocaleDateFormatSymbolsThroughJodaUtility(): Unit = {
    val locale: Locale = Locale.US

    val symbols: DateFormatSymbols = DateTimeUtils.getDateFormatSymbols(locale)

    assertThat(symbols.getMonths).contains("January", "December")
    assertThat(symbols.getShortWeekdays).contains("Sun", "Sat")
  }
}
