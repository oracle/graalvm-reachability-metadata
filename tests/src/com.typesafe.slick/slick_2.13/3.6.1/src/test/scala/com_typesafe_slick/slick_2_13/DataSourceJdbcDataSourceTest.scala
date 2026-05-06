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
import slick.jdbc.DataSourceJdbcDataSource
import slick.jdbc.JdbcDataSource

class DataSourceJdbcDataSourceTest {
  @Test
  def createsDataSourceFromConfiguredClassName(): Unit = {
    val dataSourceClassName = "slick.jdbc.DriverDataSource"
    val config = ConfigFactory.parseString(s"""
      connectionPool = "disabled"
      dataSourceClassName = "$dataSourceClassName"
      maxConnections = 7
      """)

    val jdbcDataSource = JdbcDataSource.forConfig(
      config,
      driver = null,
      name = "configured-data-source",
      classLoader = getClass.getClassLoader)

    assertThat(jdbcDataSource).isInstanceOf(classOf[DataSourceJdbcDataSource])
    val dataSource = jdbcDataSource.asInstanceOf[DataSourceJdbcDataSource]
    assertThat(dataSource.ds.getClass.getName).isEqualTo(dataSourceClassName)
    assertThat(dataSource.maxConnections).isEqualTo(Some(7))
  }
}
