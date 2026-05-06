/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.Array
import java.sql.Blob
import java.sql.CallableStatement
import java.sql.Clob
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.NClob
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLWarning
import java.sql.SQLXML
import java.sql.Savepoint
import java.sql.ShardingKey
import java.sql.Statement
import java.sql.Struct
import java.util.Properties
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

class Slick_hikaricp_2_13Test {
  import Slick_hikaricp_2_13Test._

  @Test
  def buildsHikariDataSourceFromExplicitHikariSettings(): Unit = {
    val driver: TestJdbcDriver = new TestJdbcDriver
    withRegisteredDriver(driver) {
      val dataSource: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        ConfigFactory.parseString(s"""
          jdbcUrl = "$JdbcUrl"
          username = "slick-user"
          password = "slick-password"
          properties {
            cachePrepStmts = "true"
            prepStmtCacheSize = "8"
          }
          autoCommit = false
          readOnly = true
          transactionIsolation = READ_COMMITTED
          schema = "test_schema"
          catalog = "test_catalog"
          maximumPoolSize = 4
          minimumIdle = 1
          poolName = "configured-slick-pool"
          connectionTimeout = 250 ms
          validationTimeout = 250 ms
          idleTimeout = 30000 ms
          maxLifetime = 60000 ms
          initializationFailTimeout = 1000 ms
        """),
        driver,
        "fallback-pool-name",
        getClass.getClassLoader
      )

      try {
        assertThat(dataSource.hconf.getJdbcUrl).isEqualTo(JdbcUrl)
        assertThat(dataSource.hconf.getUsername).isEqualTo("slick-user")
        assertThat(dataSource.hconf.getPassword).isEqualTo("slick-password")
        assertThat(dataSource.hconf.getDataSourceProperties.getProperty("cachePrepStmts")).isEqualTo("true")
        assertThat(dataSource.hconf.getDataSourceProperties.getProperty("prepStmtCacheSize")).isEqualTo("8")
        assertThat(dataSource.hconf.isAutoCommit).isFalse
        assertThat(dataSource.hconf.isReadOnly).isTrue
        assertThat(dataSource.hconf.getTransactionIsolation).isEqualTo("TRANSACTION_READ_COMMITTED")
        assertThat(dataSource.hconf.getSchema).isEqualTo("test_schema")
        assertThat(dataSource.hconf.getCatalog).isEqualTo("test_catalog")
        assertThat(dataSource.hconf.getMaximumPoolSize).isEqualTo(4)
        assertThat(dataSource.hconf.getMinimumIdle).isEqualTo(1)
        assertThat(dataSource.hconf.getPoolName).isEqualTo("configured-slick-pool")
        assertThat(dataSource.hconf.getConnectionTimeout).isEqualTo(250L)
        assertThat(dataSource.hconf.getValidationTimeout).isEqualTo(250L)
        assertThat(dataSource.maxConnections).isEqualTo(Some(4))

        val connection: Connection = dataSource.createConnection()
        try {
          assertThat(connection.isClosed).isFalse
          assertThat(driver.connectCount).isGreaterThanOrEqualTo(1)
        } finally {
          connection.close()
        }
      } finally {
        dataSource.close()
      }
    }
  }

  @Test
  def honorsSlickConfigurationAliasesAndDefaultPoolName(): Unit = {
    val driver: TestJdbcDriver = new TestJdbcDriver
    withRegisteredDriver(driver) {
      val dataSource: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        ConfigFactory.parseString(s"""
          url = "$JdbcUrl"
          user = "alias-user"
          maxConnections = 3
          minConnections = 2
          numThreads = 5
          initializationFailTimeout = 1000 ms
          connectionTimeout = 250 ms
          validationTimeout = 250 ms
        """),
        driver,
        "named-by-slick",
        getClass.getClassLoader
      )

      try {
        assertThat(dataSource.hconf.getJdbcUrl).isEqualTo(JdbcUrl)
        assertThat(dataSource.hconf.getUsername).isEqualTo("alias-user")
        assertThat(dataSource.hconf.getMaximumPoolSize).isEqualTo(3)
        assertThat(dataSource.hconf.getMinimumIdle).isEqualTo(2)
        assertThat(dataSource.hconf.getPoolName).isEqualTo("named-by-slick")
        assertThat(dataSource.maxConnections).isEqualTo(Some(3))

        val firstConnection: Connection = dataSource.createConnection()
        val secondConnection: Connection = dataSource.createConnection()
        try {
          assertThat(firstConnection.isClosed).isFalse
          assertThat(secondConnection.isClosed).isFalse
          assertThat(driver.connectCount).isGreaterThanOrEqualTo(2)
        } finally {
          secondConnection.close()
          firstConnection.close()
        }
      } finally {
        dataSource.close()
      }
    }
  }

  @Test
  def usesNumThreadsAsPoolSizeWhenExplicitConnectionLimitsAreAbsent(): Unit = {
    val driver: TestJdbcDriver = new TestJdbcDriver
    withRegisteredDriver(driver) {
      val dataSource: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        ConfigFactory.parseString(s"""
          jdbcUrl = "$JdbcUrl"
          numThreads = 2
          initializationFailTimeout = 1000 ms
          connectionTimeout = 250 ms
          validationTimeout = 250 ms
        """),
        driver,
        "num-threads-pool",
        getClass.getClassLoader
      )

      try {
        assertThat(dataSource.hconf.getMaximumPoolSize).isEqualTo(2)
        assertThat(dataSource.hconf.getMinimumIdle).isEqualTo(2)
        assertThat(dataSource.maxConnections).isEqualTo(Some(2))
      } finally {
        dataSource.close()
      }
    }
  }

  @Test
  def configuresLazyInitializationAndConnectionLifecycleSettings(): Unit = {
    val driver: TestJdbcDriver = new TestJdbcDriver
    withRegisteredDriver(driver) {
      val dataSource: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
        ConfigFactory.parseString(s"""
          jdbcUrl = "$JdbcUrl"
          maximumPoolSize = 1
          minimumIdle = 0
          initializationFailTimeout = -1 ms
          connectionTimeout = 250 ms
          validationTimeout = 250 ms
          connectionTestQuery = "SELECT 1"
          connectionInitSql = "SET search_path TO slick_test"
          isolateInternalQueries = true
          allowPoolSuspension = true
          leakDetectionThreshold = 2500 ms
        """),
        driver,
        "lazy-lifecycle-pool",
        getClass.getClassLoader
      )

      try {
        assertThat(dataSource.hconf.getInitializationFailTimeout).isEqualTo(-1L)
        assertThat(dataSource.hconf.getConnectionTestQuery).isEqualTo("SELECT 1")
        assertThat(dataSource.hconf.getConnectionInitSql).isEqualTo("SET search_path TO slick_test")
        assertThat(dataSource.hconf.isIsolateInternalQueries).isTrue
        assertThat(dataSource.hconf.isAllowPoolSuspension).isTrue
        assertThat(dataSource.hconf.getLeakDetectionThreshold).isEqualTo(2500L)
        assertThat(driver.connectCount).isZero
      } finally {
        dataSource.close()
      }
    }
  }
}

object Slick_hikaricp_2_13Test {
  private val JdbcUrl: String = "jdbc:slick-hikaricp-test:mem"

  private def withRegisteredDriver(driver: Driver)(test: => Unit): Unit = {
    DriverManager.registerDriver(driver)
    try {
      test
    } finally {
      DriverManager.deregisterDriver(driver)
    }
  }

  private final class TestJdbcDriver extends Driver {
    private val connections: AtomicInteger = new AtomicInteger(0)

    def connectCount: Int = connections.get()

    override def acceptsURL(url: String): Boolean = url == JdbcUrl

    override def connect(url: String, info: Properties): Connection = {
      if (!acceptsURL(url)) {
        null
      } else {
        connections.incrementAndGet()
        new TestConnection
      }
    }

    override def getMajorVersion: Int = 1

    override def getMinorVersion: Int = 0

    override def getParentLogger: Logger = Logger.getLogger(getClass.getName)

    override def getPropertyInfo(url: String, info: Properties): scala.Array[DriverPropertyInfo] = scala.Array.empty

    override def jdbcCompliant(): Boolean = false
  }

  private final class TestConnection extends Connection {
    private var autoCommit: Boolean = true
    private var catalog: String = _
    private var closed: Boolean = false
    private var networkTimeout: Int = 0
    private var readOnly: Boolean = false
    private var schema: String = _
    private var transactionIsolation: Int = Connection.TRANSACTION_READ_COMMITTED
    private val clientInfo: Properties = new Properties

    override def abort(executor: Executor): Unit = close()

    override def beginRequest(): Unit = ()

    override def clearWarnings(): Unit = ()

    override def close(): Unit = closed = true

    override def commit(): Unit = ()

    override def createArrayOf(typeName: String, elements: scala.Array[AnyRef]): Array = unsupported()

    override def createBlob(): Blob = unsupported()

    override def createClob(): Clob = unsupported()

    override def createNClob(): NClob = unsupported()

    override def createSQLXML(): SQLXML = unsupported()

    override def createStatement(): Statement = unsupported()

    override def createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement = unsupported()

    override def createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement = unsupported()

    override def createStruct(typeName: String, attributes: scala.Array[AnyRef]): Struct = unsupported()

    override def endRequest(): Unit = ()

    override def getAutoCommit: Boolean = autoCommit

    override def getCatalog: String = catalog

    override def getClientInfo: Properties = {
      val copy: Properties = new Properties
      copy.putAll(clientInfo)
      copy
    }

    override def getClientInfo(name: String): String = clientInfo.getProperty(name)

    override def getHoldability: Int = 0

    override def getMetaData: DatabaseMetaData = unsupported()

    override def getNetworkTimeout: Int = networkTimeout

    override def getSchema: String = schema

    override def getTransactionIsolation: Int = transactionIsolation

    override def getTypeMap: java.util.Map[String, Class[_]] = new java.util.HashMap[String, Class[_]]

    override def getWarnings: SQLWarning = null

    override def isClosed: Boolean = closed

    override def isReadOnly: Boolean = readOnly

    override def isValid(timeout: Int): Boolean = !closed

    override def isWrapperFor(iface: Class[_]): Boolean = iface.isInstance(this)

    override def nativeSQL(sql: String): String = sql

    override def prepareCall(sql: String): CallableStatement = unsupported()

    override def prepareCall(sql: String, resultSetType: Int, resultSetConcurrency: Int): CallableStatement = unsupported()

    override def prepareCall(
        sql: String,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): CallableStatement = unsupported()

    override def prepareStatement(sql: String): PreparedStatement = unsupported()

    override def prepareStatement(sql: String, autoGeneratedKeys: Int): PreparedStatement = unsupported()

    override def prepareStatement(sql: String, columnIndexes: scala.Array[Int]): PreparedStatement = unsupported()

    override def prepareStatement(sql: String, columnNames: scala.Array[String]): PreparedStatement = unsupported()

    override def prepareStatement(sql: String, resultSetType: Int, resultSetConcurrency: Int): PreparedStatement = unsupported()

    override def prepareStatement(
        sql: String,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): PreparedStatement = unsupported()

    override def releaseSavepoint(savepoint: Savepoint): Unit = ()

    override def rollback(): Unit = ()

    override def rollback(savepoint: Savepoint): Unit = ()

    override def setAutoCommit(autoCommit: Boolean): Unit = this.autoCommit = autoCommit

    override def setCatalog(catalog: String): Unit = this.catalog = catalog

    override def setClientInfo(name: String, value: String): Unit = clientInfo.setProperty(name, value)

    override def setClientInfo(properties: Properties): Unit = {
      clientInfo.clear()
      clientInfo.putAll(properties)
    }

    override def setHoldability(holdability: Int): Unit = ()

    override def setNetworkTimeout(executor: Executor, milliseconds: Int): Unit = networkTimeout = milliseconds

    override def setReadOnly(readOnly: Boolean): Unit = this.readOnly = readOnly

    override def setSavepoint(): Savepoint = unsupported()

    override def setSavepoint(name: String): Savepoint = unsupported()

    override def setSchema(schema: String): Unit = this.schema = schema

    override def setShardingKey(shardingKey: ShardingKey): Unit = ()

    override def setShardingKey(superShardingKey: ShardingKey, shardingKey: ShardingKey): Unit = ()

    override def setShardingKeyIfValid(shardingKey: ShardingKey, timeout: Int): Boolean = true

    override def setShardingKeyIfValid(superShardingKey: ShardingKey, shardingKey: ShardingKey, timeout: Int): Boolean = true

    override def setTransactionIsolation(level: Int): Unit = transactionIsolation = level

    override def setTypeMap(map: java.util.Map[String, Class[_]]): Unit = ()

    override def unwrap[T](iface: Class[T]): T = {
      if (iface.isInstance(this)) {
        iface.cast(this)
      } else {
        throw new SQLException(s"Connection is not a wrapper for ${iface.getName}")
      }
    }

    private def unsupported[T](): T = throw new SQLFeatureNotSupportedException("Not required by the HikariCP tests")
  }
}
