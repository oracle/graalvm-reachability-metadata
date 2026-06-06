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

class DataSourceJdbcDataSourceTest {
  @Test
  def createsDataSourceFromConfiguredClassName(): Unit = {
    val dataSourceClassName: String = "slick.jdbc.DriverDataSource"
    val config: Config = ConfigFactory.parseString(s"""
      connectionPool = "disabled"
      dataSourceClassName = "$dataSourceClassName"
      maxConnections = 7
      """)

    val jdbcDataSource: JdbcDataSource = JdbcDataSource.forConfig(
      config,
      driver = null,
      name = "configured-data-source",
      classLoader = getClass.getClassLoader)

    assertThat(jdbcDataSource).isInstanceOf(classOf[DataSourceJdbcDataSource])
    val dataSource: DataSourceJdbcDataSource = jdbcDataSource.asInstanceOf[DataSourceJdbcDataSource]
    assertThat(dataSource.ds.getClass.getName).isEqualTo(dataSourceClassName)
    assertThat(dataSource.maxConnections).isEqualTo(Some(7))
  }

  @Test
  def factoryInstantiatesConfiguredDataSourceClass(): Unit = {
    val dataSourceClassName: String = "org.postgresql.ds.PGSimpleDataSource"
    val config: Config = ConfigFactory.parseString(s"""
      dataSourceClassName = "$dataSourceClassName"
      maxConnections = 3
      """)

    val dataSource: DataSourceJdbcDataSource = DataSourceJdbcDataSource.forConfig(
      config,
      driver = null,
      name = "direct-configured-data-source",
      classLoader = getClass.getClassLoader)

    assertThat(dataSource.ds.getClass.getName).isEqualTo(dataSourceClassName)
    assertThat(dataSource.maxConnections).isEqualTo(Some(3))
  }
}
