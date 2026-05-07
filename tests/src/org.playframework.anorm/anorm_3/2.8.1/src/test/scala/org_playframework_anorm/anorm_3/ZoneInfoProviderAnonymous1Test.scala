/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.assertj.core.api.Assertions.assertThat
import org.joda.time.DateTimeZone
import org.joda.time.tz.ZoneInfoProvider
import org.junit.jupiter.api.Test

class ZoneInfoProviderAnonymous1Test {
  @Test
  def loadsBundledZoneInfoMapThroughSystemClassLoader(): Unit = {
    val provider: ZoneInfoProvider = new ZoneInfoProvider("org/joda/time/tz/data", null)

    assertThat(provider.getAvailableIDs).contains("UTC")
    assertThat(provider.getZone("UTC")).isSameAs(DateTimeZone.UTC)
  }
}
