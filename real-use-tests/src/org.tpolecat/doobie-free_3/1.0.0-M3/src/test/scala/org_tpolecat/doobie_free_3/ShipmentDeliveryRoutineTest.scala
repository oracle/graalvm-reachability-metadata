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
import doobie.free.{callablestatement, connection}
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.{Date, Types}
import scala.concurrent.duration._

/** Exercises callable statements to calculate and store shipment delivery dates with a database routine. */
@Timeout(60)
class ShipmentDeliveryRoutineTest {
  private case class Shipment(id: String, dispatchedOn: Date, transitDays: Int)

  private def deliveryDate(shipment: Shipment): connection.ConnectionIO[Date] =
    connection.prepareCall("{ ? = call DATEADD('DAY', ?, CAST(? AS DATE)) }").bracket { routine =>
      connection.embed(routine, for {
        _ <- callablestatement.registerOutParameter(1, Types.DATE)
        _ <- callablestatement.setInt(2, shipment.transitDays)
        _ <- callablestatement.setDate(3, shipment.dispatchedOn)
        _ <- callablestatement.execute
        promisedDate <- callablestatement.getDate(1)
      } yield promisedDate)
    }(routine => connection.embed(routine, callablestatement.close))

  @Test
  def deliveryPromisesSurviveMonthAndYearBoundaries(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:shipment_delivery_routine;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val shipments: List[Shipment] = List(
      Shipment("leap-day-order", Date.valueOf("2024-02-28"), 2),
      Shipment("new-year-order", Date.valueOf("2024-12-30"), 3)
    )

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS delivery_promises".update.run
      _ <- sql"""CREATE TABLE delivery_promises (
                  shipment_id VARCHAR(40) PRIMARY KEY,
                  promised_on DATE NOT NULL
                )""".update.run
    } yield ()

    val schedule: connection.ConnectionIO[List[Date]] = shipments.traverse { shipment =>
      for {
        promisedDate <- deliveryDate(shipment)
        _ <- sql"""INSERT INTO delivery_promises (shipment_id, promised_on)
                    VALUES (${shipment.id}, $promisedDate)""".update.run
      } yield promisedDate
    }

    val readPromises: connection.ConnectionIO[List[(String, Date)]] =
      sql"SELECT shipment_id, promised_on FROM delivery_promises ORDER BY shipment_id"
        .query[(String, Date)].to[List]

    val (calculated, persisted): (List[Date], List[(String, Date)]) = (for {
      _ <- initialize.transact(transactor)
      dates <- schedule.transact(transactor)
      rows <- readPromises.transact(transactor)
      _ <- sql"DROP TABLE delivery_promises".update.run.transact(transactor)
    } yield (dates, rows)).timeout(45.seconds).unsafeRunSync()

    assertEquals(List(Date.valueOf("2024-03-01"), Date.valueOf("2025-01-02")), calculated)
    assertEquals(
      List(
        ("leap-day-order", Date.valueOf("2024-03-01")),
        ("new-year-order", Date.valueOf("2025-01-02"))
      ),
      persisted
    )
  }
}
