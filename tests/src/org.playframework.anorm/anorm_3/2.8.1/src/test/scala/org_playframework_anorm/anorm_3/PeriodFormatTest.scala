/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.util.Locale

import org.assertj.core.api.Assertions.assertThat
import org.joda.time.Period
import org.joda.time.format.PeriodFormat
import org.joda.time.format.PeriodFormatter
import org.junit.jupiter.api.Test

class PeriodFormatTest {
  @Test
  def buildsWordBasedFormatterFromLocaleResources(): Unit = {
    val locale: Locale = new Locale("en", "US", "ANORM_PERIOD_FORMAT")

    val formatter: PeriodFormatter = PeriodFormat.wordBased(locale)
    val period: Period = new Period(2, 3, 0, 4, 5, 0, 0, 0)
    val text: String = formatter.print(period)

    assertThat(text).isEqualTo("2 years, 3 months, 4 days and 5 hours")
    assertThat(formatter.getLocale).isEqualTo(locale)
  }
}
