/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.basic.DatabaseConfig
import slick.memory.MemoryProfile

class ConfiguredMemoryProfile extends MemoryProfile

class DatabaseConfigTest {
  @Test
  def loadsProfileObjectFromConfig(): Unit = {
    val config = ConfigFactory.parseString("""
      slick.profile = "slick.memory.MemoryProfile$"
      """)

    val databaseConfig = DatabaseConfig.forConfig[MemoryProfile]("slick", config)

    assertThat(databaseConfig.profile).isSameAs(MemoryProfile)
    assertThat(databaseConfig.profileName).isEqualTo("slick.memory.MemoryProfile")
    assertThat(databaseConfig.profileIsObject).isTrue
    assertThat(databaseConfig.config.getString("profile")).isEqualTo("slick.memory.MemoryProfile$")
  }

  @Test
  def instantiatesProfileClassFromConfig(): Unit = {
    val profileClassName = classOf[ConfiguredMemoryProfile].getName
    val config = ConfigFactory.parseString(s"""
      slick.profile = "$profileClassName"
      """)

    val databaseConfig = DatabaseConfig.forConfig[MemoryProfile]("slick", config)

    assertThat(databaseConfig.profile).isInstanceOf(classOf[ConfiguredMemoryProfile])
    assertThat(databaseConfig.profileName).isEqualTo(profileClassName)
    assertThat(databaseConfig.profileIsObject).isFalse
    assertThat(databaseConfig.config.getString("profile")).isEqualTo(profileClassName)
  }
}
