/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's transactional DBIO subsystem to transfer funds atomically.
  * A failed recipient lookup rolls back the debit and journal entry before a valid transfer commits.
  */
class AtomicFundsTransferTest {
  class Accounts(tag: Tag) extends Table[(Int, Int)](tag, "TRANSFER_ACCOUNTS") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def balanceCents: Rep[Int] = column[Int]("BALANCE_CENTS")
    def * = (id, balanceCents)
  }

  class Journal(tag: Tag) extends Table[(Int, Int, Int, Int)](tag, "TRANSFER_JOURNAL") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def sender: Rep[Int] = column[Int]("SENDER")
    def recipient: Rep[Int] = column[Int]("RECIPIENT")
    def amountCents: Rep[Int] = column[Int]("AMOUNT_CENTS")
    def * = (id, sender, recipient, amountCents)
  }

  @Test
  @Timeout(60)
  def rollsBackRejectedTransferAndCommitsAcceptedTransfer(): Unit = {
    val accounts = TableQuery(tag => new Accounts(tag))
    val journal = TableQuery(tag => new Journal(tag))
    val schema = accounts.schema ++ journal.schema
    val database = Database.forURL(
      "jdbc:h2:mem:atomic_funds_transfer;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )

    def transfer(id: Int, sender: Int, recipient: Int, amountCents: Int): DBIO[Unit] = {
      val senderAccount = accounts.filter(_.id === sender)
      val recipientAccount = accounts.filter(_.id === recipient)
      val action = for {
        source <- senderAccount.forUpdate.result.head
        _ <- if (amountCents > 0 && source._2 >= amountCents) DBIO.successful(())
             else DBIO.failed(new IllegalArgumentException("Insufficient funds"))
        _ <- senderAccount.map(_.balanceCents).update(source._2 - amountCents)
        _ <- journal += ((id, sender, recipient, amountCents))
        destination <- recipientAccount.forUpdate.result.headOption
        _ <- destination match {
          case Some((_, balance)) =>
            recipientAccount.map(_.balanceCents).update(balance + amountCents).map(_ => ())
          case None => DBIO.failed(new IllegalArgumentException("Unknown recipient"))
        }
      } yield ()
      action.transactionally
    }

    val scenario = for {
      _ <- schema.create
      _ <- accounts ++= Seq((1, 10000), (2, 2000))
      rejected <- transfer(101, 1, 99, 2500).asTry
      balancesAfterRejection <- accounts.sortBy(_.id).result
      journalAfterRejection <- journal.result
      _ <- transfer(102, 1, 2, 2500)
      balancesAfterCommit <- accounts.sortBy(_.id).result
      journalAfterCommit <- journal.sortBy(_.id).result
    } yield (rejected, balancesAfterRejection, journalAfterRejection,
      balancesAfterCommit, journalAfterCommit)

    try {
      val (rejected, balancesAfterRejection, journalAfterRejection,
        balancesAfterCommit, journalAfterCommit) = Await.result(
        database.run(scenario.andFinally(schema.drop)),
        30.seconds
      )
      assertTrue(rejected.isFailure)
      assertTrue(rejected.failed.get.isInstanceOf[IllegalArgumentException])
      assertEquals("Unknown recipient", rejected.failed.get.getMessage)
      assertEquals(Seq((1, 10000), (2, 2000)), balancesAfterRejection)
      assertTrue(journalAfterRejection.isEmpty)
      assertEquals(Seq((1, 7500), (2, 4500)), balancesAfterCommit)
      assertEquals(Seq((102, 1, 2, 2500)), journalAfterCommit)
    } finally {
      database.close()
    }
  }
}
