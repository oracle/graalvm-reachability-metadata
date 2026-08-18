/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import java.sql.{Connection, Driver, DriverPropertyInfo}
import java.util.Properties
import java.util.logging.Logger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.jdbc.DriverDataSource

class DriverDataSourceTestDriver extends Driver {
  override def acceptsURL(url: String): Boolean =
    url != null && url.startsWith("jdbc:slick-driver-data-source-test:")

  override def connect(url: String, info: Properties): Connection = null

  override def getMajorVersion: Int = 1

  override def getMinorVersion: Int = 0

  override def getParentLogger: Logger =
    Logger.getLogger("com_typesafe_slick.slick_2_13.DriverDataSourceTestDriver")

  override def getPropertyInfo(url: String, info: Properties): Array[DriverPropertyInfo] =
    Array.empty[DriverPropertyInfo]

  override def jdbcCompliant: Boolean = false
}

class DriverDataSourceTest {
  @Test
  def loadsAndInstantiatesUnregisteredDriverClass(): Unit = {
    val dataSource = new DriverDataSource(
      url = "jdbc:slick-driver-data-source-test:database",
      driverClassName = classOf[DriverDataSourceTestDriver].getName,
      classLoader = getClass.getClassLoader)

    try {
      dataSource.init()

      assertThat(dataSource.getParentLogger.getName)
        .isEqualTo("com_typesafe_slick.slick_2_13.DriverDataSourceTestDriver")
    } finally {
      dataSource.close()
    }
  }
}
