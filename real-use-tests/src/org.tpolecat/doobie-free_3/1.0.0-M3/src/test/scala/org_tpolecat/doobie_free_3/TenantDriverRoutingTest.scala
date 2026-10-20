/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.effect.IO
import cats.effect.syntax.all._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import doobie.free.{KleisliInterpreter, connection, driver}
import doobie.implicits._
import org.h2.Driver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.SQLException
import java.util.Properties
import scala.concurrent.duration._

/** Exercises DriverIO connection routing to keep tenant settings isolated across database reconnects. */
@Timeout(60)
class TenantDriverRoutingTest {
  private case class Tenant(database: String, shopName: String, currency: String)

  private def inTenant[A](tenant: Tenant)(action: connection.ConnectionIO[A]): driver.DriverIO[A] = {
    val credentials: Properties = new Properties()
    credentials.setProperty("user", "sa")
    credentials.setProperty("password", "")
    val url: String = s"jdbc:h2:mem:${tenant.database};DB_CLOSE_DELAY=-1"

    driver.connect(url, credentials).bracket { database =>
      if (database == null) {
        driver.raiseError[A](new SQLException("Configured driver did not accept the tenant URL"))
      } else {
        driver.embed(database, action)
      }
    } { database =>
      if (database == null) driver.unit
      else driver.embed(database, connection.close)
    }
  }

  private def provision(tenant: Tenant): driver.DriverIO[Unit] = inTenant(tenant)(for {
    _ <- sql"DROP TABLE IF EXISTS shop_settings".update.run
    _ <- sql"""CREATE TABLE shop_settings (
                setting_key VARCHAR(40) PRIMARY KEY,
                setting_value VARCHAR(100) NOT NULL
              )""".update.run
    _ <- sql"INSERT INTO shop_settings VALUES ('shop_name', ${tenant.shopName})".update.run
    _ <- sql"INSERT INTO shop_settings VALUES ('currency', ${tenant.currency})".update.run
  } yield ())

  private def settings(tenant: Tenant): driver.DriverIO[List[(String, String)]] =
    inTenant(tenant)(
      sql"SELECT setting_key, setting_value FROM shop_settings ORDER BY setting_key"
        .query[(String, String)].to[List]
    )

  @Test
  def reconnectingToEachTenantReadsOnlyItsOwnShopSettings(): Unit = {
    val north: Tenant = Tenant("driver_routing_north", "North Coffee", "CAD")
    val south: Tenant = Tenant("driver_routing_south", "South Tea", "NZD")
    val tenants: List[Tenant] = List(north, south)

    val program: driver.DriverIO[(List[(String, String)], List[(String, String)])] = for {
      _ <- tenants.traverse_(provision)
      _ <- inTenant(north)(
        sql"UPDATE shop_settings SET setting_value = 'North Roasters' WHERE setting_key = 'shop_name'"
          .update.run
      )
      northSettings <- settings(north)
      southSettings <- settings(south)
    } yield (northSettings, southSettings)

    val cleanup: driver.DriverIO[Unit] = tenants.traverse_ { tenant =>
      inTenant(tenant)(sql"DROP TABLE IF EXISTS shop_settings".update.run.void)
    }
    val jdbcDriver: Driver = new Driver()
    val interpreter: KleisliInterpreter[IO] = KleisliInterpreter[IO]
    val (northSettings, southSettings): (List[(String, String)], List[(String, String)]) =
      program.guarantee(cleanup).foldMap(interpreter.DriverInterpreter).run(jdbcDriver)
        .timeout(45.seconds).unsafeRunSync()

    assertEquals(List(("currency", "CAD"), ("shop_name", "North Roasters")), northSettings)
    assertEquals(List(("currency", "NZD"), ("shop_name", "South Tea")), southSettings)
  }
}
