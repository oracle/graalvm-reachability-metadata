/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.reactivestreams.{Subscriber, Subscription}
import slick.jdbc.H2Profile.api._

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

/** Exercises Slick's Reactive Streams subsystem to export a settled ledger with bounded demand.
  * A CSV consumer requests two rows at a time and publishes its output only when the stream completes.
  */
class StreamingLedgerExportTest {
  class Ledger(tag: Tag) extends Table[(Int, String, Int, Boolean)](tag, "STREAM_LEDGER") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def account: Rep[String] = column[String]("ACCOUNT")
    def cents: Rep[Int] = column[Int]("CENTS")
    def settled: Rep[Boolean] = column[Boolean]("SETTLED")
    def * = (id, account, cents, settled)
  }

  class CsvConsumer extends Subscriber[String] {
    val completed: Promise[Vector[String]] = Promise[Vector[String]]()
    private val lines = Vector.newBuilder[String]
    private var subscription: Subscription = _
    private var received: Int = 0

    override def onSubscribe(value: Subscription): Unit = {
      subscription = value
      subscription.request(2L)
    }

    override def onNext(line: String): Unit = {
      lines += line
      received += 1
      if (received % 2 == 0) subscription.request(2L)
    }

    override def onError(error: Throwable): Unit = {
      completed.tryFailure(error)
    }

    override def onComplete(): Unit = {
      completed.trySuccess(lines.result())
    }
  }

  @Test
  @Timeout(60)
  def exportsOnlySettledEntriesInLedgerOrder(): Unit = {
    val ledger = TableQuery(tag => new Ledger(tag))
    val database: Database = Database.forURL(
      "jdbc:h2:mem:streaming_ledger_export;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val setup = DBIO.seq(
      ledger.schema.create,
      ledger ++= Seq(
        (7, "sales", 4200, true),
        (2, "sales", 9900, false),
        (5, "refunds", -500, true),
        (1, "sales", 1250, true),
        (6, "fees", -250, true),
        (4, "sales", 8800, false),
        (3, "sales", 3400, true)
      )
    )
    val exportRows = ledger.filter(_.settled).sortBy(_.id)
      .map(row => (row.id, row.account, row.cents))
      .result.withStatementParameters(fetchSize = 2)
    val consumer: CsvConsumer = new CsvConsumer

    try {
      Await.result(database.run(setup), 20.seconds)
      database.stream(exportRows).mapResult { case (id, account, cents) =>
        s"$id,$account,$cents"
      }.subscribe(consumer)
      val rows: Vector[String] = Await.result(consumer.completed.future, 20.seconds)
      val csv: String = ("id,account,cents" +: rows).mkString("\n")
      assertEquals(
        """id,account,cents
          |1,sales,1250
          |3,sales,3400
          |5,refunds,-500
          |6,fees,-250
          |7,sales,4200""".stripMargin,
        csv
      )
    } finally {
      try {
        Await.result(database.run(ledger.schema.dropIfExists), 10.seconds)
      } finally {
        database.close()
      }
    }
  }
}
