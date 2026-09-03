/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.util.Locale
import java.util.Set

import org.joda.time.DateTimeZone
import org.joda.time.tz.NameProvider
import org.joda.time.tz.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DateTimeZoneTest {
  @Test
  def systemPropertiesConfigureProviderAndNameProvider(): Unit = {
    val originalProvider: Provider = DateTimeZone.getProvider
    val originalNameProvider: NameProvider = DateTimeZone.getNameProvider
    val originalProviderProperty: String = System.getProperty(providerPropertyName)
    val originalNameProviderProperty: String = System.getProperty(nameProviderPropertyName)

    try {
      System.setProperty(providerPropertyName, classOf[ConfiguredDateTimeZoneProvider].getName)
      System.setProperty(nameProviderPropertyName, classOf[ConfiguredDateTimeZoneNameProvider].getName)

      DateTimeZone.setProvider(null)
      DateTimeZone.setNameProvider(null)

      val provider: Provider = DateTimeZone.getProvider
      val nameProvider: NameProvider = DateTimeZone.getNameProvider

      assertEquals(classOf[ConfiguredDateTimeZoneProvider], provider.getClass)
      assertEquals(classOf[ConfiguredDateTimeZoneNameProvider], nameProvider.getClass)
      assertSame(DateTimeZone.UTC, provider.getZone("UTC"))
      assertEquals("UTC", provider.getAvailableIDs.iterator().next())
      assertEquals("TST", DateTimeZone.UTC.getShortName(0L, Locale.ENGLISH))
      assertEquals("Test time", DateTimeZone.UTC.getName(0L, Locale.ENGLISH))
    } finally {
      restoreProperty(providerPropertyName, originalProviderProperty)
      restoreProperty(nameProviderPropertyName, originalNameProviderProperty)
      DateTimeZone.setProvider(originalProvider)
      DateTimeZone.setNameProvider(originalNameProvider)
    }
  }

  private def providerPropertyName: String = "org.joda.time.DateTimeZone.Provider"

  private def nameProviderPropertyName: String = "org.joda.time.DateTimeZone.NameProvider"

  private def restoreProperty(name: String, value: String): Unit = {
    if (value == null) {
      System.clearProperty(name)
    } else {
      System.setProperty(name, value)
    }
  }
}

final class ConfiguredDateTimeZoneProvider extends Provider {
  override def getZone(id: String): DateTimeZone = {
    if ("UTC" == id) {
      DateTimeZone.UTC
    } else {
      null
    }
  }

  override def getAvailableIDs: Set[String] = Set.of("UTC")
}

final class ConfiguredDateTimeZoneNameProvider extends NameProvider {
  override def getShortName(locale: Locale, id: String, nameKey: String): String = "TST"

  override def getName(locale: Locale, id: String, nameKey: String): String = "Test time"
}
