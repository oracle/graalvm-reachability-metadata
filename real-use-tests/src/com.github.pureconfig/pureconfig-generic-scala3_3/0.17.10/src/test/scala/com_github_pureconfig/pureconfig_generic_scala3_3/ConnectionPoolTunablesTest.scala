/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_scala3_3

import java.time.Duration

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import com.typesafe.config.{Config, ConfigObject, ConfigRenderOptions}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import pureconfig.{ConfigReader, ConfigSource, ConfigWriter}
import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure, KeyNotFound, WrongType}
import pureconfig.generic.semiauto.{deriveReader, deriveWriter}

/**
 * Exercises derivation for wide products: a connection pool's tunables form a case class with
 * more than 22 fields, whose derived reader fills every field left out of a sparse HOCON file
 * from its default argument, whose derived writer renders all of them back out, and whose
 * failures for several broken fields, including ones past the 22nd, are reported together.
 */
class ConnectionPoolTunablesTest {
  import ConnectionPoolTunablesTest.*
  import ConnectionPoolTunablesTest.given

  @Test
  def fillsFieldsLeftOutOfASparseFileFromDefaultArguments(): Unit = {
    val pools: DataSources = ConfigSource.string(PoolsConfig).at("datasources").loadOrThrow[DataSources]

    assertEquals(
      PoolTunables(
        jdbcUrl = "jdbc:postgresql://db-primary.internal:5432/orders",
        username = "orders_app",
        poolName = "orders-primary",
        maximumPoolSize = 40,
        connectionTimeout = 5.seconds,
        leakDetectionThreshold = Some(2.minutes),
        connectionInitSql = Some("SET statement_timeout = 15000"),
        prepStmtCacheSqlLimit = 4096,
        useServerPrepStmts = false,
        socketTimeout = 90.seconds,
        dataSourceProperties = Map("sslmode" -> "verify-full", "applicationName" -> "orders")
      ),
      pools.primary
    )

    val reporting: PoolTunables = pools.replicas("reporting")
    assertEquals("jdbc:postgresql://db-replica-1.internal:5432/orders", reporting.jdbcUrl)
    assertEquals("orders_reader", reporting.username)
    assertTrue(reporting.readOnly)
    assertEquals(Some("TRANSACTION_REPEATABLE_READ"), reporting.isolationLevel)
    assertEquals(PoolTunables.defaultsFor(reporting.jdbcUrl, reporting.username).copy(
      readOnly = true,
      isolationLevel = Some("TRANSACTION_REPEATABLE_READ")
    ), reporting)
    assertEquals(10, reporting.maximumPoolSize)
    assertEquals(250, reporting.prepStmtCacheSize)
    assertEquals(60.seconds, reporting.socketTimeout)
    assertEquals(Map.empty, reporting.dataSourceProperties)
  }

  @Test
  def rendersEveryFieldOfAWideProductAndReloadsItUnchanged(): Unit = {
    val pools: DataSources = ConfigSource.string(PoolsConfig).at("datasources").loadOrThrow[DataSources]
    val rendered: ConfigObject = ConfigWriter[DataSources].to(pools).asInstanceOf[ConfigObject]
    val primary: Config = rendered.toConfig.getConfig("primary")

    val unsetOptionalKeys: Set[String] = Set("isolation-level", "catalog", "schema")
    assertEquals(AllPoolKeys -- unsetOptionalKeys, primary.root.keySet.asScala.toSet)
    assertEquals(40, primary.getInt("maximum-pool-size"))
    assertEquals(Duration.ofSeconds(90), primary.getDuration("socket-timeout"))
    assertEquals(4096, primary.getInt("prep-stmt-cache-sql-limit"))
    assertFalse(primary.getBoolean("use-server-prep-stmts"))
    assertEquals("verify-full", primary.getConfig("data-source-properties").getString("sslmode"))

    val reporting: Config = rendered.toConfig.getConfig("replicas").getConfig("reporting")
    assertEquals(
      AllPoolKeys -- Set("leak-detection-threshold", "catalog", "schema", "connection-init-sql"),
      reporting.root.keySet.asScala.toSet
    )
    assertTrue(reporting.getBoolean("read-only"))

    val reloaded: DataSources =
      ConfigSource.string(rendered.render(ConfigRenderOptions.concise())).loadOrThrow[DataSources]
    assertEquals(pools, reloaded)
  }

  @Test
  def reportsEveryBrokenFieldOfAWideProductTogether(): Unit = {
    val brokenConfig: String = """
      datasources {
        primary {
          username = "orders_app"
          maximum-pool-size = "forty"
          auto-commit = sometimes
          prep-stmt-cache-size = [250]
          socket-timeout = "ninety seconds"
          data-source-properties = "sslmode=require"
        }
        replicas {}
      }
      """

    ConfigSource.string(brokenConfig).at("datasources").load[DataSources] match {
      case Right(pools) => fail(s"Expected the broken pool settings to be rejected, but loaded $pools")
      case Left(failures) =>
        assertEquals(
          Set(
            "datasources.primary.jdbc-url",
            "datasources.primary.maximum-pool-size",
            "datasources.primary.auto-commit",
            "datasources.primary.prep-stmt-cache-size",
            "datasources.primary.socket-timeout",
            "datasources.primary.data-source-properties"
          ),
          failurePaths(failures),
          failures.prettyPrint()
        )
        assertTrue(
          failures.toList.exists {
            case ConvertFailure(KeyNotFound("jdbc-url", _), _, "datasources.primary") => true
            case _ => false
          },
          failures.prettyPrint()
        )
        val reasons: Map[String, Any] = failures.toList.collect {
          case ConvertFailure(reason, _, path) => path -> reason
        }.toMap
        assertTrue(reasons("datasources.primary.maximum-pool-size").isInstanceOf[WrongType], failures.prettyPrint())
        assertTrue(reasons("datasources.primary.auto-commit").isInstanceOf[WrongType], failures.prettyPrint())
        assertTrue(reasons("datasources.primary.prep-stmt-cache-size").isInstanceOf[WrongType], failures.prettyPrint())
        assertTrue(reasons("datasources.primary.socket-timeout").isInstanceOf[CannotConvert], failures.prettyPrint())
        assertTrue(reasons("datasources.primary.data-source-properties").isInstanceOf[WrongType], failures.prettyPrint())
    }
  }

  /** Failure paths; a missing key is reported at its parent, so the key name is appended. */
  private def failurePaths(failures: ConfigReaderFailures): Set[String] =
    failures.toList.collect {
      case ConvertFailure(KeyNotFound(key, _), _, path) => s"$path.$key"
      case ConvertFailure(_, _, path) => path
    }.toSet
}

object ConnectionPoolTunablesTest {

  /** Every tunable of one connection pool; only the URL and the user have no default. */
  final case class PoolTunables(
      jdbcUrl: String,
      username: String,
      poolName: String = "primary",
      minimumIdle: Int = 2,
      maximumPoolSize: Int = 10,
      connectionTimeout: FiniteDuration = 30.seconds,
      idleTimeout: FiniteDuration = 10.minutes,
      maxLifetime: FiniteDuration = 30.minutes,
      keepaliveTime: FiniteDuration = 2.minutes,
      validationTimeout: FiniteDuration = 5.seconds,
      leakDetectionThreshold: Option[FiniteDuration] = None,
      autoCommit: Boolean = true,
      readOnly: Boolean = false,
      isolationLevel: Option[String] = None,
      catalog: Option[String] = None,
      schema: Option[String] = None,
      connectionInitSql: Option[String] = None,
      registerMbeans: Boolean = false,
      allowPoolSuspension: Boolean = false,
      initializationFailTimeout: FiniteDuration = 1.second,
      cachePrepStmts: Boolean = true,
      prepStmtCacheSize: Int = 250,
      prepStmtCacheSqlLimit: Int = 2048,
      useServerPrepStmts: Boolean = true,
      socketTimeout: FiniteDuration = 60.seconds,
      dataSourceProperties: Map[String, String] = Map.empty
  )

  object PoolTunables {
    def defaultsFor(jdbcUrl: String, username: String): PoolTunables = PoolTunables(jdbcUrl, username)
  }

  given ConfigReader[PoolTunables] = deriveReader[PoolTunables]
  given ConfigWriter[PoolTunables] = deriveWriter[PoolTunables]

  final case class DataSources(primary: PoolTunables, replicas: Map[String, PoolTunables])

  given ConfigReader[DataSources] = deriveReader[DataSources]
  given ConfigWriter[DataSources] = deriveWriter[DataSources]

  /** The kebab-case key of every `PoolTunables` field. */
  val AllPoolKeys: Set[String] = Set(
    "jdbc-url",
    "username",
    "pool-name",
    "minimum-idle",
    "maximum-pool-size",
    "connection-timeout",
    "idle-timeout",
    "max-lifetime",
    "keepalive-time",
    "validation-timeout",
    "leak-detection-threshold",
    "auto-commit",
    "read-only",
    "isolation-level",
    "catalog",
    "schema",
    "connection-init-sql",
    "register-mbeans",
    "allow-pool-suspension",
    "initialization-fail-timeout",
    "cache-prep-stmts",
    "prep-stmt-cache-size",
    "prep-stmt-cache-sql-limit",
    "use-server-prep-stmts",
    "socket-timeout",
    "data-source-properties"
  )

  val PoolsConfig: String = """
    datasources {
      primary {
        jdbc-url = "jdbc:postgresql://db-primary.internal:5432/orders"
        username = "orders_app"
        pool-name = "orders-primary"
        maximum-pool-size = 40
        connection-timeout = 5 seconds
        leak-detection-threshold = 2 minutes
        connection-init-sql = "SET statement_timeout = 15000"
        prep-stmt-cache-sql-limit = 4096
        use-server-prep-stmts = false
        socket-timeout = 90 seconds
        data-source-properties {
          sslmode = "verify-full"
          applicationName = "orders"
        }
      }
      replicas {
        reporting {
          jdbc-url = "jdbc:postgresql://db-replica-1.internal:5432/orders"
          username = "orders_reader"
          read-only = true
          isolation-level = "TRANSACTION_REPEATABLE_READ"
        }
      }
    }
    """
}
