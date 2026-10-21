/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_hikari_3

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Resource
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.ExecutionContext

/** Exercises configuration-driven Hikari pool creation and connection initialization.
  * A regional reporting service selects its schema and report region when connections open.
  */
class ConfiguredReportingPoolTest {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Test
  @Timeout(60)
  def configuredConnectionsRunRegionalReportsInTheReportingSchema(): Unit = {
    val pool: Resource[IO, HikariTransactor[IO]] = for {
      connectExecutionContext <- ExecutionContexts.fixedThreadPool[IO](2)
      blocker <- Blocker[IO]
      config <- Resource.eval(IO {
        val settings: HikariConfig = new HikariConfig()
        settings.setDriverClassName("org.h2.Driver")
        settings.setJdbcUrl("jdbc:h2:mem:configured_reporting;INIT=CREATE SCHEMA IF NOT EXISTS reporting")
        settings.setUsername("sa")
        settings.setPassword("")
        settings.setMaximumPoolSize(1)
        settings.setMinimumIdle(1)
        settings.setConnectionTimeout(10000)
        settings.setSchema("REPORTING")
        settings.setConnectionInitSql("SET @report_region = 'west'")
        settings
      })
      transactor <- HikariTransactor.fromHikariConfig[IO](config, connectExecutionContext, blocker)
    } yield transactor

    pool.use { transactor =>
      for {
        _ <- (for {
          _ <- sql"""CREATE TABLE sales (
            region VARCHAR(20) NOT NULL,
            product VARCHAR(40) NOT NULL,
            amount INTEGER NOT NULL
          )""".update.run
          _ <- sql"""INSERT INTO sales (region, product, amount) VALUES
            ('west', 'coffee', 30), ('west', 'coffee', 20),
            ('west', 'tea', 15), ('east', 'coffee', 900)
          """.update.run
        } yield ()).transact(transactor)
        schema <- sql"SELECT CURRENT_SCHEMA".query[String].unique.transact(transactor)
        report <- sql"""SELECT product, SUM(amount) FROM sales
          WHERE region = @report_region GROUP BY product ORDER BY product
        """.query[(String, Long)].to[List].transact(transactor)
        _ <- IO {
          assertEquals("REPORTING", schema)
          assertEquals(List(("coffee", 50L), ("tea", 15L)), report)
        }
      } yield ()
    }.unsafeRunSync()
  }
}
