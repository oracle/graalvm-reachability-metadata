/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.joda.time.DateTimeZone
import org.joda.time.tz.ZoneInfoProvider
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZoneInfoProviderAnonymous1Test {
  @Test
  def nullLoaderUsesSystemResourceLookup(): Unit = {
    val provider: ZoneInfoProvider = new ZoneInfoProvider("org/joda/time/tz/data", null)

    assertTrue(provider.getAvailableIDs.contains("UTC"))
    assertSame(DateTimeZone.UTC, provider.getZone("UTC"))
    assertNotNull(provider.getZone("Europe/Paris"))
  }
}
