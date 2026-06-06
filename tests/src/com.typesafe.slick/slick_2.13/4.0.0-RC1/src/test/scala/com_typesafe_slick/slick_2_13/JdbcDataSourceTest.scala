/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.jdbc.DataSourceJdbcDataSource
import slick.jdbc.JdbcDataSource

class JdbcDataSourceTest {
  @Test
  def loadsConfiguredJdbcDataSourceFactoryObject(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      connectionPool = "slick.jdbc.DataSourceJdbcDataSource$"
      dataSourceClassName = "slick.jdbc.DriverDataSource"
      maxConnections = 1
      """)

    val jdbcDataSource: JdbcDataSource = JdbcDataSource.forConfig(
      config,
      driver = null,
      name = "configured-factory",
      classLoader = getClass.getClassLoader)

    assertThat(jdbcDataSource).isInstanceOf(classOf[DataSourceJdbcDataSource])
    val configuredDataSource: DataSourceJdbcDataSource = jdbcDataSource.asInstanceOf[DataSourceJdbcDataSource]
    assertThat(configuredDataSource.ds.getClass.getName).isEqualTo("slick.jdbc.DriverDataSource")
    assertThat(configuredDataSource.maxConnections).isEqualTo(Some(1))
  }
}
