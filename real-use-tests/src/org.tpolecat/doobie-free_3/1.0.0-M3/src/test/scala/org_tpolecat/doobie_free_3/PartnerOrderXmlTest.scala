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
import doobie.free.{connection, preparedstatement, resultset}
import doobie.hi.{connection => HC, preparedstatement => HPS}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises SQLXML binding and retrieval to retain partner order documents for fulfillment. */
@Timeout(60)
class PartnerOrderXmlTest {
  private def receive(orderId: String, xml: String): connection.ConnectionIO[Int] =
    connection.createSQLXML.bracket { document =>
      for {
        _ <- connection.delay(document.setString(xml))
        inserted <- HC.prepareStatement(
          "INSERT INTO partner_orders (partner_order_id, payload) VALUES (?, ?)"
        )(for {
          _ <- preparedstatement.setString(1, orderId)
          _ <- preparedstatement.setSQLXML(2, document)
          count <- preparedstatement.executeUpdate
        } yield count)
      } yield inserted
    }(document => connection.delay(document.free()))

  private def load(orderId: String): connection.ConnectionIO[Option[String]] = {
    val readDocument: resultset.ResultSetIO[Option[String]] = resultset.next.flatMap { found =>
      if (found) {
        resultset.getSQLXML("payload").bracket { document =>
          resultset.delay(Option(document.getString))
        }(document => resultset.delay(document.free()))
      } else {
        resultset.pure(Option.empty[String])
      }
    }

    HC.prepareStatement("SELECT payload FROM partner_orders WHERE partner_order_id = ?")(for {
      _ <- preparedstatement.setString(1, orderId)
      payload <- HPS.executeQuery(readDocument)
    } yield payload)
  }

  @Test
  def fulfillmentReadsTheCommittedPartnerDocumentWithoutLosingXmlContent(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:partner_order_xml;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val orderId: String = "partner-483"
    val payload: String =
      """<?xml version="1.0" encoding="UTF-8"?>
        |<order xmlns="urn:partner:orders" id="partner-483">
        |  <customer>Caf\u00e9 &amp; Co</customer>
        |  <line sku="tea" quantity="3"/>
        |  <instructions><![CDATA[Leave at <side door> & ring once.]]></instructions>
        |</order>""".stripMargin

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS partner_orders".update.run
      _ <- sql"""CREATE TABLE partner_orders (
                  partner_order_id VARCHAR(40) PRIMARY KEY,
                  payload CLOB NOT NULL
                )""".update.run
    } yield ()

    val (inserted, restored, missing): (Int, Option[String], Option[String]) = (for {
      _ <- initialize.transact(transactor)
      count <- receive(orderId, payload).transact(transactor)
      document <- load(orderId).transact(transactor)
      absent <- load("partner-not-received").transact(transactor)
      _ <- sql"DROP TABLE partner_orders".update.run.transact(transactor)
    } yield (count, document, absent)).timeout(45.seconds).unsafeRunSync()

    assertEquals(1, inserted)
    assertEquals(Some(payload), restored)
    assertEquals(None, missing)
  }
}
