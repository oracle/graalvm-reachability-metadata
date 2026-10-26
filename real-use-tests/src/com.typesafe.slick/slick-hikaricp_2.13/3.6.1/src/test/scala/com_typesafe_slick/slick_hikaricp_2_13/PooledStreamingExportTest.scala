/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_hikaricp_2_13

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.h2.Driver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import slick.jdbc.H2Profile.api._
import slick.jdbc.hikaricp.HikariCPJdbcDataSource
import slick.util.AsyncExecutor

import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._

/** Exercises HikariCP-backed Slick streaming cancellation for a bounded catalog export preview. */
class PooledStreamingExportTest {
  @Test
  @Timeout(60)
  def cancellingPreviewReturnsTheOnlyConnectionForCatalogUpdates(): Unit = {
    val config: Config = ConfigFactory.parseString("""
      url = "jdbc:h2:mem:pooled_streaming_export"
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
      "catalog-export-pool",
      getClass.getClassLoader
    )
    val database: Database = Database.forSource(
      source,
      AsyncExecutor("catalog-export-worker", minThreads = 1, maxThreads = 1, queueSize = 100, maxConnections = 1)
    )

    try {
      Await.result(database.run(DBIO.seq(
        sqlu"create table catalog (product_id integer primary key, name varchar(80) not null)",
        sqlu"""insert into catalog values
          (1, 'Apricot'), (2, 'Blueberry'), (3, 'Cherry'), (4, 'Date'), (5, 'Elderberry')"""
      )), 15.seconds)

      val preview: PreviewSubscriber = new PreviewSubscriber(3)
      database.stream(
        sql"select name from catalog order by product_id".as[String].withStatementParameters(fetchSize = 2)
      ).subscribe(preview)
      assertEquals(Vector("Apricot", "Blueberry", "Cherry"), Await.result(preview.result.future, 15.seconds))

      // There is only one physical connection: this work cannot finish if cancellation leaks it.
      val updated: Int = Await.result(database.run(
        sqlu"update catalog set name = 'Dried date' where product_id = 4"
      ), 15.seconds)
      assertEquals(1, updated)
      val catalog: Vector[String] = Await.result(database.run(
        sql"select name from catalog order by product_id".as[String]
      ), 15.seconds)
      assertEquals(Vector("Apricot", "Blueberry", "Cherry", "Dried date", "Elderberry"), catalog)
    } finally {
      database.close()
    }
  }

  private class PreviewSubscriber(limit: Int) extends Subscriber[String] {
    val result: Promise[Vector[String]] = Promise[Vector[String]]()
    private var subscription: Subscription = _
    private var rows: Vector[String] = Vector.empty

    override def onSubscribe(value: Subscription): Unit = {
      subscription = value
      value.request(limit.toLong)
    }

    override def onNext(value: String): Unit = {
      rows = rows :+ value
      if (rows.size == limit) {
        subscription.cancel()
        result.trySuccess(rows)
      }
    }

    override def onError(error: Throwable): Unit = {
      result.tryFailure(error)
    }

    override def onComplete(): Unit = {
      result.trySuccess(rows)
    }
  }
}
