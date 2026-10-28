/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import java.sql.SQLException

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.Driver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import slick.jdbc.H2Profile.api._
import slick.jdbc.hikaricp.HikariCPJdbcDataSource
import slick.util.AsyncExecutor

import scala.concurrent.Await
import scala.concurrent.duration._

/** Exercises HikariCP-backed transactional connection reuse for inventory transfers. */
class PooledStockTransferTest {
  @Test
  @Timeout(60)
  def commitsTransfersAndRollsBackInsufficientStock(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      url = "jdbc:h2:mem:pooled_stock_transfer"
      user = "sa"
      password = ""
      maxConnections = 1
      minConnections = 1
      connectionTimeout = 10 seconds
      validationTimeout = 10 seconds
      initializationFailTimeout = 10 seconds
    """)
    val source: HikariCPJdbcDataSource = HikariCPJdbcDataSource.forConfig(
      config,
      new Driver,
      "inventory-transfer-pool",
      getClass.getClassLoader
    )
    val database: Database = Database.forSource(
      source,
      AsyncExecutor("inventory-transfer-worker", minThreads = 1, maxThreads = 1, queueSize = 100, maxConnections = 1)
    )

    try {
      Await.result(database.run(DBIO.seq(
        sqlu"""create table stock (
          location varchar(32) primary key,
          units integer not null check (units >= 0)
        )""",
        sqlu"insert into stock (location, units) values ('warehouse', 10), ('shop', 2)"
      )), 15.seconds)

      Await.result(database.run(transfer(3).transactionally), 15.seconds)
      assertEquals(Vector("shop:5", "warehouse:7"), readStock(database))

      // Credit happens first, so an insufficient-stock failure must undo that credit too.
      assertThrows(classOf[SQLException], () => {
        Await.result(database.run(transfer(8).transactionally), 15.seconds)
        ()
      })
      assertEquals(Vector("shop:5", "warehouse:7"), readStock(database))

      // The single pooled connection must remain usable after the failed transaction.
      Await.result(database.run(transfer(2).transactionally), 15.seconds)
      assertEquals(Vector("shop:7", "warehouse:5"), readStock(database))
    } finally {
      database.close()
    }
  }

  private def transfer(quantity: Int): DBIO[Unit] = DBIO.seq(
    sqlu"update stock set units = units + $quantity where location = 'shop'",
    sqlu"update stock set units = units - $quantity where location = 'warehouse'"
  )

  private def readStock(database: Database): Vector[String] = {
    Await.result(database.run(
      sql"select location || ':' || units from stock order by location".as[String]
    ), 15.seconds)
  }
}
