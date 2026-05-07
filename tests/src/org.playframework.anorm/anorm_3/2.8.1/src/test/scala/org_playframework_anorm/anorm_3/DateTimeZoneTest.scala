/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.util.Locale
import java.util.Set as JavaSet

import org.assertj.core.api.Assertions.assertThat
import org.joda.time.DateTimeZone
import org.joda.time.tz.NameProvider
import org.joda.time.tz.Provider
import org.junit.jupiter.api.Test

class DateTimeZoneTest {
  @Test
  def loadsDefaultProviderFromSystemProperty(): Unit = {
    val propertyName: String = "org.joda.time.DateTimeZone.Provider"
    val originalProperty: String = System.getProperty(propertyName)
    val originalProvider: Provider = DateTimeZone.getProvider

    try {
      System.setProperty(propertyName, classOf[DateTimeZoneFixtureProvider].getName)
      DateTimeZone.setProvider(null)

      val provider: Provider = DateTimeZone.getProvider
      assertThat(provider).isInstanceOf(classOf[DateTimeZoneFixtureProvider])
      assertThat(provider.getZone("UTC")).isEqualTo(DateTimeZone.UTC)
      assertThat(provider.getAvailableIDs).contains("UTC", DateTimeZoneFixtureProvider.fixtureZoneId)
    } finally {
      restoreProperty(propertyName, originalProperty)
      DateTimeZone.setProvider(originalProvider)
    }
  }

  @Test
  def loadsDefaultNameProviderFromSystemProperty(): Unit = {
    val propertyName: String = "org.joda.time.DateTimeZone.NameProvider"
    val originalProperty: String = System.getProperty(propertyName)
    val originalNameProvider: NameProvider = DateTimeZone.getNameProvider

    try {
      System.setProperty(propertyName, classOf[DateTimeZoneFixtureNameProvider].getName)
      DateTimeZone.setNameProvider(null)

      val nameProvider: NameProvider = DateTimeZone.getNameProvider
      assertThat(nameProvider).isInstanceOf(classOf[DateTimeZoneFixtureNameProvider])
      assertThat(nameProvider.getShortName(Locale.ENGLISH, "UTC", "UTC")).isEqualTo("fixture-short-UTC")
      assertThat(nameProvider.getName(Locale.ENGLISH, "UTC", "UTC")).isEqualTo("fixture-name-UTC")
    } finally {
      restoreProperty(propertyName, originalProperty)
      DateTimeZone.setNameProvider(originalNameProvider)
    }
  }

  private def restoreProperty(name: String, value: String): Unit = {
    if (value == null) {
      System.clearProperty(name)
    } else {
      System.setProperty(name, value)
    }
  }
}

final class DateTimeZoneFixtureProvider extends Provider {
  override def getZone(id: String): DateTimeZone = {
    if ("UTC" == id) {
      DateTimeZone.UTC
    } else if (DateTimeZoneFixtureProvider.fixtureZoneId == id) {
      DateTimeZone.forOffsetHours(1)
    } else {
      null
    }
  }

  override def getAvailableIDs: JavaSet[String] =
    JavaSet.of("UTC", DateTimeZoneFixtureProvider.fixtureZoneId)
}

object DateTimeZoneFixtureProvider {
  val fixtureZoneId: String = "Fixture/Zone"
}

final class DateTimeZoneFixtureNameProvider extends NameProvider {
  override def getShortName(locale: Locale, id: String, nameKey: String): String =
    s"fixture-short-$nameKey"

  override def getName(locale: Locale, id: String, nameKey: String): String =
    s"fixture-name-$nameKey"
}
